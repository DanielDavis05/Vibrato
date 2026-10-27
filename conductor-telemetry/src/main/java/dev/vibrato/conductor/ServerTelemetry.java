package dev.vibrato.conductor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Observes real server ticks; disk I/O is off the game thread and the queue holds one snapshot. */
public final class ServerTelemetry implements ModInitializer {
    private final AtomicReference<Map<String, Object>> pending = new AtomicReference<>();
    private long intervalStart, tickStart, totalTicks, sampleTicks, sumTickNanos, maxTickNanos;
    private ScheduledExecutorService writer;
    private Path output;
    private final Gson gson = new Gson();
    private final AtomicReference<List<BlockPos>> watched = new AtomicReference<>(List.of());
    private volatile String requestId;
    private long requestModified;

    @Override public void onInitialize() {
        String configured = System.getProperty("vibrato.telemetry.path");
        if (configured == null || configured.isBlank()) return;
        output = Path.of(configured);
        writer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "conductor-server-telemetry");
            thread.setDaemon(true);
            return thread;
        });
        writer.scheduleWithFixedDelay(this::flush, 0, 250, TimeUnit.MILLISECONDS);
        // Explicit per-run starting conditions override pregenerated template metadata.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String assigned = System.getProperty("vibrato.assignedGamemode");
            if (assigned != null) server.setDefaultGameMode(GameMode.byName(assigned, GameMode.SURVIVAL));
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.nanoTime();
            if (intervalStart == 0) intervalStart = now;
            long duration = Math.max(0, now - tickStart);
            totalTicks++; sampleTicks++; sumTickNanos += duration; maxTickNanos = Math.max(maxTickNanos, duration);
            if (now - intervalStart < 1_000_000_000L) return;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("wall", System.currentTimeMillis() / 1000.0);
            data.put("ticks", totalTicks);
            data.put("monotonicSeconds", now / 1e9);
            data.put("sampleTicks", sampleTicks);
            data.put("sampleSeconds", (now - intervalStart) / 1e9);
            data.put("tps", sampleTicks * 1e9 / (now - intervalStart));
            data.put("meanTickMs", sumTickNanos / 1e6 / sampleTicks);
            data.put("maxTickMs", maxTickNanos / 1e6);
            data.put("players", server.getCurrentPlayerCount());
            data.put("source", "fabric_end_server_tick");
            List<Map<String, Object>> players = new ArrayList<>();
            for (var player : server.getPlayerManager().getPlayerList()) {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("uuid", player.getUuidAsString());
                entry.put("username", player.getGameProfile().getName());
                entry.put("mode", player.interactionManager.getGameMode().getName());
                entry.put("permissionLevel", server.getPermissionLevel(player.getGameProfile()));
                var ability = player.getAbilities();
                entry.put("abilities", Map.of("creativeMode", ability.creativeMode, "allowFlying", ability.allowFlying,
                        "flying", ability.flying, "invulnerable", ability.invulnerable, "allowModifyWorld", ability.allowModifyWorld));
                entry.put("noClip", player.noClip);
                entry.put("position", List.of(player.getX(), player.getY(), player.getZ()));
                var inventory = new TreeMap<String, Integer>();
                for (int slot = 0; slot < player.getInventory().size(); slot++) {
                    var stack = player.getInventory().getStack(slot);
                    if (!stack.isEmpty()) inventory.merge(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                }
                entry.put("inventory", inventory);
                entry.put("timeOfDay", player.getServerWorld().getTimeOfDay());
                var blocks = new TreeMap<String, Object>();
                for (BlockPos pos : watched.get()) blocks.put(pos.getX()+","+pos.getY()+","+pos.getZ(),
                        Registries.BLOCK.getId(player.getServerWorld().getBlockState(pos).getBlock()).toString());
                entry.put("watchedBlocks", blocks);
                entry.put("requestId", requestId);
                players.add(entry);
            }
            data.put("playerSnapshots", players);
            pending.set(data);
            intervalStart = now; sampleTicks = 0; sumTickNanos = 0; maxTickNanos = 0;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            flush();
            writer.shutdown();
        });
    }

    private void pollRequests() {
        Path file = output.resolveSibling("telemetry-request.json");
        try {
            if (!Files.isRegularFile(file)) return;
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified == requestModified) return;
            JsonObject request = gson.fromJson(Files.readString(file), JsonObject.class);
            List<BlockPos> positions = new ArrayList<>();
            if (request.has("blocks")) for (var coordinate : request.getAsJsonArray("blocks")) {
                if (positions.size() >= 16) break;
                var xyz = coordinate.getAsJsonArray();
                if (xyz.size() == 3) positions.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
            }
            requestId = request.has("requestId") ? request.get("requestId").getAsString() : null;
            watched.set(List.copyOf(positions));
            requestModified = modified;
        } catch (IOException | RuntimeException ignored) { }
    }

    private synchronized void flush() {
        pollRequests();
        Map<String, Object> data = pending.getAndSet(null);
        if (data == null) return;
        Path temp = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(temp, gson.toJson(data) + "\n");
            try { Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) {
            System.err.println("[Conductor telemetry] " + error.getMessage());
        }
    }
}
