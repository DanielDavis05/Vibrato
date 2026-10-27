package dev.vibrato.conductor;

import com.google.gson.Gson;
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
            data.put("sampleTicks", sampleTicks);
            data.put("sampleSeconds", (now - intervalStart) / 1e9);
            data.put("tps", sampleTicks * 1e9 / (now - intervalStart));
            data.put("meanTickMs", sumTickNanos / 1e6 / sampleTicks);
            data.put("maxTickMs", maxTickNanos / 1e6);
            data.put("players", server.getCurrentPlayerCount());
            data.put("source", "fabric_end_server_tick");
            pending.set(data);
            intervalStart = now; sampleTicks = 0; sumTickNanos = 0; maxTickNanos = 0;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            flush();
            writer.shutdown();
        });
    }

    private synchronized void flush() {
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
