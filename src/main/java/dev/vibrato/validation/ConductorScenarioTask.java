package dev.vibrato.validation;

import baritone.api.utils.input.Input;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded real client actions with independent dedicated-server observations as the success oracle. */
public final class ConductorScenarioTask extends Task {
    public static final List<String> NAMES = List.of("creative_inventory", "creative_build", "adventure_inspect", "spectator_explore", "operator_commands");
    private final String scenario;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final String requestId = UUID.randomUUID().toString();
    private long started, stageAt;
    private int stage;
    private BlockPos target;
    private String originalBlock;
    private double initialY;
    private long initialTime;
    private boolean complete, written;

    public ConductorScenarioTask(String scenario) {
        if (!NAMES.contains(scenario)) throw new IllegalArgumentException("Unknown conductor scenario: " + scenario);
        this.scenario = scenario;
    }

    @Override protected void onStart(Vibrato mod) { started = stageAt = System.nanoTime(); }
    private double seconds(long since) { return (System.nanoTime() - since) / 1e9; }
    private void advance() { stage++; stageAt = System.nanoTime(); }

    @SuppressWarnings("unchecked") public static Map<String, Object> serverPlayer(MinecraftClient client) {
        String configured = System.getProperty("vibrato.serverTelemetryFile");
        if (configured == null || client.player == null) return null;
        try {
            Map<String, Object> snapshot = new ObjectMapper().readValue(Path.of(configured).toFile(), new TypeReference<>() {});
            if (!"fabric_end_server_tick".equals(snapshot.get("source"))
                    || System.currentTimeMillis()/1000.0 - ((Number)snapshot.get("wall")).doubleValue() > 5) return null;
            for (Object raw : (List<?>) snapshot.getOrDefault("playerSnapshots", List.of())) {
                Map<String, Object> player = (Map<String, Object>) raw;
                if (client.player.getUuidAsString().equals(player.get("uuid"))) return player;
            }
        } catch (IOException | RuntimeException ignored) { }
        return null;
    }

    @Override protected Task onTick(Vibrato mod) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (seconds(started) > 40) { reject("server-confirmed scenario goal exceeded 40 seconds"); return null; }
        Map<String, Object> server = serverPlayer(client);
        if (server == null) return null;
        String expected = switch (scenario) {
            case "creative_inventory", "creative_build" -> "creative";
            case "adventure_inspect" -> "adventure";
            case "spectator_explore" -> "spectator";
            default -> "survival";
        };
        String actual = client.interactionManager.getCurrentGameMode().getName();
        if (!expected.equals(actual) || !expected.equals(server.get("mode"))) {
            reject("assigned mode mismatch: expected " + expected + ", client=" + actual + ", server=" + server.get("mode"));
            return null;
        }
        int permission = ((Number)server.get("permissionLevel")).intValue();
        if ((scenario.equals("operator_commands") && permission < 2) || (!scenario.equals("operator_commands") && permission != 0)) {
            reject("unexpected operator permission level " + permission); return null;
        }
        evidence.put("actualMode", actual); evidence.put("permissionLevel", permission);
        evidence.put("permissionSource", "server_observer");
        evidence.put("abilities", server.get("abilities"));
        try {
            switch (scenario) {
                case "creative_inventory" -> inventoryAndFlight(mod, client, server);
                case "creative_build" -> creativeBuild(client, server);
                case "adventure_inspect" -> adventure(client, server);
                case "spectator_explore" -> spectator(mod, client, server);
                case "operator_commands" -> operator(client, server);
            }
        } catch (IOException | RuntimeException error) { reject("scenario action failed: " + error); }
        return null;
    }

    @SuppressWarnings("unchecked") private boolean inventoryStone(Map<String, Object> server) {
        Object count = ((Map<String, Object>)server.get("inventory")).get("minecraft:stone");
        return count instanceof Number number && number.intValue() >= 64;
    }
    private void request(BlockPos position) throws IOException {
        Path heartbeat = Path.of(System.getProperty("vibrato.serverTelemetryFile"));
        write(heartbeat.resolveSibling("telemetry-request.json"), Map.of("requestId", requestId,
                "blocks", List.of(List.of(position.getX(), position.getY(), position.getZ()))));
    }
    @SuppressWarnings("unchecked") private String watched(Map<String, Object> server) {
        if (!requestId.equals(server.get("requestId")) || target == null) return null;
        return (String)((Map<?, ?>)server.get("watchedBlocks")).get(target.getX()+","+target.getY()+","+target.getZ());
    }
    @SuppressWarnings("unchecked") private double serverY(Map<String, Object> server) {
        return ((Number)((List<?>)server.get("position")).get(1)).doubleValue();
    }
    private void creativeStone(MinecraftClient client) {
        client.player.getInventory().selectedSlot = 0;
        client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(0));
        client.interactionManager.clickCreativeStack(new ItemStack(Items.STONE, 64), 36);
    }

    private void inventoryAndFlight(Vibrato mod, MinecraftClient client, Map<String, Object> server) {
        if (stage == 0) { creativeStone(client); advance(); }
        else if (stage == 1 && inventoryStone(server)) {
            evidence.put("inventoryConfirmed", Map.of("minecraft:stone", 64));
            evidence.put("events", List.of(event("inventory_interact", "minecraft:stone")));
            initialY = serverY(server);
            client.player.getAbilities().flying = true;
            client.player.sendAbilitiesUpdate();
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            advance();
        } else if (stage == 2 && Boolean.TRUE.equals(((Map<?, ?>)server.get("abilities")).get("flying")) && serverY(server) > initialY + 1) {
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.JUMP, false);
            evidence.put("flight", Map.of("flying", true, "startY", initialY, "endY", serverY(server), "serverConfirmed", true));
            finish();
        }
    }

    private void creativeBuild(MinecraftClient client, Map<String, Object> server) throws IOException {
        if (stage == 0) { creativeStone(client); advance(); }
        else if (stage == 1 && inventoryStone(server)) {
            BlockPos origin = client.player.getBlockPos();
            outer: for (int distance = 1; distance <= 2; distance++) for (Direction direction : Direction.Type.HORIZONTAL)
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = origin.offset(direction, distance).up(dy);
                    if (client.world.isAir(candidate) && client.world.getBlockState(candidate.down()).isSolidBlock(client.world, candidate.down())) {
                        target = candidate; break outer;
                    }
                }
            if (target == null) { reject("no reachable supported air block for placement"); return; }
            request(target); advance();
        } else if (stage == 2 && watched(server) != null) {
            originalBlock = watched(server);
            BlockPos support = target.down();
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(support).add(0, .5, 0), Direction.UP, support, false));
            advance();
        } else if (stage == 3 && "minecraft:stone".equals(watched(server))) {
            evidence.put("placedBlock", List.of(target.getX(), target.getY(), target.getZ()));
            evidence.put("beforeBlock", originalBlock); evidence.put("afterBlock", "minecraft:stone");
            evidence.put("events", List.of(event("item_use", "minecraft:stone"), event("block_statechange", "minecraft:stone")));
            finish();
        }
    }

    private void adventure(MinecraftClient client, Map<String, Object> server) throws IOException {
        if (stage == 0) {
            target = client.player.getBlockPos().down();
            if (client.world.isAir(target)) { reject("adventure restriction needs a solid block under the player"); return; }
            request(target); advance();
        } else if (stage == 1 && watched(server) != null) {
            originalBlock = watched(server);
            client.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, Direction.UP));
            client.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, Direction.UP));
            advance();
        } else if (stage == 2 && seconds(stageAt) >= 2 && originalBlock.equals(watched(server))
                && !Boolean.TRUE.equals(((Map<?, ?>)server.get("abilities")).get("allowModifyWorld"))) {
            evidence.put("restriction", Map.of("action", "block_break", "target", originalBlock, "attempted", true, "denied", true, "serverConfirmed", true));
            finish();
        }
    }

    private void spectator(Vibrato mod, MinecraftClient client, Map<String, Object> server) throws IOException {
        if (stage == 0) {
            target = client.player.getBlockPos().down();
            if (client.world.isAir(target)) { reject("spectator collision probe needs solid ground"); return; }
            initialY = serverY(server); request(target); advance();
        } else if (stage == 1 && watched(server) != null) {
            originalBlock = watched(server);
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            advance();
        } else if (stage == 2 && serverY(server) < target.getY() - 1 && Boolean.TRUE.equals(server.get("noClip"))
                && Boolean.TRUE.equals(((Map<?, ?>)server.get("abilities")).get("flying")) && originalBlock.equals(watched(server))) {
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
            evidence.put("collision", Map.of("startY", initialY, "endY", serverY(server), "groundBlock", originalBlock,
                    "groundY", target.getY(), "noClip", true, "serverConfirmed", true));
            finish();
        }
    }

    private void operator(MinecraftClient client, Map<String, Object> server) {
        if (stage == 0) {
            initialTime = ((Number)server.get("timeOfDay")).longValue();
            client.player.networkHandler.sendChatCommand("time add 1200"); advance();
        } else if (stage == 1 && ((Number)server.get("timeOfDay")).longValue() - initialTime >= 1200) {
            evidence.put("operatorCommand", Map.of("command", "time add 1200", "success", true, "serverConfirmed", true,
                    "beforeTime", initialTime, "afterTime", server.get("timeOfDay")));
            finish();
        }
    }

    private Map<String, Object> event(String kind, String id) { return Map.of("kind", kind, "id", id, "success", true, "serverConfirmed", true); }
    private void finish() { complete = true; evidence.put("serverConfirmed", true); report("completed"); }
    private void reject(String reason) { fail(reason); evidence.put("serverConfirmed", false); evidence.put("failureReason", reason); report("failed"); }
    private void report(String status) {
        if (written) return;
        written = true;
        try { write(MinecraftClient.getInstance().runDirectory.toPath().resolve("scenario-result.json"),
                Map.of("scenario", scenario, "status", status, "source", "client_actions_plus_server_observer", "evidence", evidence)); }
        catch (IOException error) { System.err.println("[Conductor scenario] " + error.getMessage()); }
    }
    private void write(Path path, Object value) throws IOException {
        Path temp = path.resolveSibling(path.getFileName()+".tmp");
        mapper.writeValue(temp.toFile(), value);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    @Override public boolean isFinished(Vibrato mod) { return complete; }
    @Override protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.JUMP, false);
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
        if (!complete && failureReason() == null) reject("scenario cancelled before authoritative confirmation");
    }
    @Override protected boolean isEqual(Task other) { return other instanceof ConductorScenarioTask task && scenario.equals(task.scenario); }
    @Override protected String toDebugString() { return "Conductor scenario " + scenario + " stage " + stage; }
}
