package dev.vibrato.human.record;

import baritone.api.utils.BetterBlockPos;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.Subscription;
import dev.vibrato.eventbus.events.ClientTickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.EmptyChunk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Records player motion to {@code <game run directory>/traces/<name>-<source>.jsonl} for fitting the human motion model. Samples the
 * player once per client tick, emits key transitions by diffing the movement keys, and forwards raw mouse deltas from
 * {@link dev.vibrato.mixins.MouseRecorderMixin}. Everything runs on the client thread and never throws into the game
 * loop: an I/O or unexpected failure is logged and recording stops.
 */
public final class MotionRecorder {

    private static final int TRACE_VERSION = 1;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String[] KEY_NAMES = {
            "forward", "back", "left", "right", "jump", "sprint", "sneak", "attack", "use"
    };

    private static MotionRecorder current;
    // Mirrors current != null without the lock, so the mouse mixin can skip work on every cursor event.
    private static volatile boolean recording;

    private final Vibrato mod;
    private final TraceWriter writer;
    private final Path file;
    private final boolean[] keyDown = new boolean[KEY_NAMES.length];
    private final KeyBinding[] keys;
    private final Subscription<ClientTickEvent> subscription;
    private long tickCount;

    private MotionRecorder(Vibrato mod, TraceWriter writer, Path file, KeyBinding[] keys) {
        this.mod = mod;
        this.writer = writer;
        this.file = file;
        this.keys = keys;
        this.subscription = EventBus.subscribe(ClientTickEvent.class, event -> onTick());
    }

    /**
     * Starts recording, replacing any recording already in progress. {@code source} is {@code "human"} or
     * {@code "bot"}.
     */
    public static synchronized void start(Vibrato mod, String name, String source) throws IOException {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("trace name must be letters, digits, '_' or '-': " + name);
        }
        if (!source.equals("human") && !source.equals("bot")) {
            throw new IllegalArgumentException("trace source must be human or bot: " + source);
        }
        stop();
        Path file = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("traces").resolve(name + "-" + source + ".jsonl");
        TraceWriter writer = TraceWriter.open(file);
        try {
            GameOptions options = MinecraftClient.getInstance().options;
            writer.write(new TraceEvent.Header(
                    TRACE_VERSION, source, SharedConstants.getGameVersion().getName(),
                    options.getGuiScale().getValue(), options.getMouseSensitivity().getValue(),
                    options.getMaxFps().getValue(), name));
            current = new MotionRecorder(mod, writer, file, keyBindings(options));
            recording = true;
        } catch (IOException | RuntimeException e) {
            writer.close();
            throw e;
        }
        Debug.logMessage("Recording " + source + " trace to " + file.toAbsolutePath());
    }

    /**
     * Stops recording and closes the trace. Does nothing when not recording.
     */
    public static synchronized void stop() {
        MotionRecorder recorder = current;
        if (recorder == null) {
            return;
        }
        current = null;
        recording = false;
        EventBus.unsubscribe(recorder.subscription);
        try {
            recorder.writer.close();
            Debug.logMessage("Saved " + recorder.tickCount + " ticks to " + recorder.file.toAbsolutePath());
        } catch (IOException e) {
            Debug.logError("Failed to close motion trace " + recorder.file + ": " + e.getMessage());
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    /**
     * Raw cursor movement in pixels since the last cursor event. Called from the mouse mixin, on the client thread.
     */
    public static synchronized void onMouse(double dx, double dy) {
        if (!recording || current == null) {
            return;
        }
        current.emit(new TraceEvent.Mouse(nowMillis(), dx, dy));
    }

    /**
     * Monotonic milliseconds for trace timestamps. Shared by every event source so they line up.
     */
    public static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private void onTick() {
        if (!Vibrato.inGame()) {
            return;
        }
        try {
            synchronized (MotionRecorder.class) {
                if (current != this) {
                    return;
                }
                long ms = nowMillis();
                for (int i = 0; i < keys.length; i++) {
                    boolean down = keys[i].isPressed();
                    if (down != keyDown[i]) {
                        keyDown[i] = down;
                        emit(new TraceEvent.Key(ms, KEY_NAMES[i], down));
                    }
                }
                emit(sampleTick(ms));
            }
        } catch (RuntimeException e) {
            Debug.logError("Motion recording stopped after an error: " + e);
            stop();
        }
    }

    private TraceEvent.Tick sampleTick(long ms) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Vec3d velocity = player.getVelocity();
        Double nextX = null;
        Double nextY = null;
        Double nextZ = null;
        var executor = mod.getClientBaritone().getPathingBehavior().getCurrent();
        if (executor != null) {
            List<BetterBlockPos> positions = executor.getPath().positions();
            int next = executor.getPosition() + 1;
            if (next >= 0 && next < positions.size()) {
                BetterBlockPos node = positions.get(next);
                nextX = node.getX() + 0.5;
                nextY = (double) node.getY(); // block centres in x and z, feet level in y
                nextZ = node.getZ() + 0.5;
            }
        }
        Double edge = edgeAhead(player, velocity);
        return new TraceEvent.Tick(
                ++tickCount, ms,
                player.getX(), player.getY(), player.getZ(),
                velocity.x, velocity.y, velocity.z,
                player.getYaw(), player.getPitch(),
                player.isOnGround(), player.isSprinting(),
                nextX, nextY, nextZ, edge);
    }

    /**
     * Distance to the first gap or drop ahead of the player, or null when there is none or no world is loaded.
     */
    private static Double edgeAhead(ClientPlayerEntity player, Vec3d velocity) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        double[] heading = EdgeProbe.heading(velocity.x, velocity.z, player.getYaw());
        int feetBlock = (int) Math.floor(player.getY());
        return EdgeProbe.distanceToEdge(player.getX(), player.getY(), player.getZ(), heading[0], heading[1],
                (x, z) -> highestSolidGround(world, x, z, feetBlock));
    }

    /**
     * Highest solid block in the column from one block above feet level down to two below it. Returns
     * {@link EdgeProbe#UNKNOWN} for an unloaded chunk and {@link EdgeProbe#NONE} when no solid block is in the window.
     */
    private static int highestSolidGround(ClientWorld world, int x, int z, int feetBlock) {
        if (world.getChunk(x >> 4, z >> 4) instanceof EmptyChunk) {
            return EdgeProbe.UNKNOWN;
        }
        BlockPos.Mutable pos = new BlockPos.Mutable(x, feetBlock, z);
        for (int y = feetBlock + 1; y >= feetBlock - 2; y--) {
            pos.setY(y);
            if (world.getBlockState(pos).isSolidBlock(world, pos)) {
                return y;
            }
        }
        return EdgeProbe.NONE;
    }

    private void emit(TraceEvent event) {
        if (current != this) {
            return;
        }
        try {
            writer.write(event);
        } catch (IOException e) {
            Debug.logError("Motion recording stopped, write failed: " + e.getMessage());
            stop();
        }
    }

    private static KeyBinding[] keyBindings(GameOptions options) {
        return new KeyBinding[] {
                options.forwardKey, options.backKey, options.leftKey, options.rightKey, options.jumpKey,
                options.sprintKey, options.sneakKey, options.attackKey, options.useKey
        };
    }
}
