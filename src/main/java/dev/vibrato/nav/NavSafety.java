package dev.vibrato.nav;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.ClientTickEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Installs the never-stuck navigation chain and owns the per-bot {@link WaypointMemory}.
 */
public final class NavSafety {

    private static final String CONFIG_FOLDER = "vibrato";

    private static WaypointMemory _waypoints = new WaypointMemory();
    private static Path _waypointFile;
    private static boolean _wasDead;

    private NavSafety() {
    }

    /**
     * Installs the navigation chain, unless {@code -Dvibrato.nav=off} disables it. Waypoint tracking stays on either way.
     */
    public static void install(Vibrato mod) {
        String mode = System.getProperty("vibrato.nav", "on");
        if ("off".equalsIgnoreCase(mode)) {
            Debug.logInternal("Navigation safety: OFF (-Dvibrato.nav=off), unstuck actions are disabled");
        } else {
            Debug.logInternal("Navigation safety: ON");
            new NavSafetyChain(mod.getTaskRunner());
        }
        EventBus.subscribe(ClientTickEvent.class, evt -> onClientTick(mod));
    }

    /**
     * The waypoints of the bot in game. Read it through this method each time, since loading the bot's file replaces
     * the instance.
     */
    public static WaypointMemory waypoints() {
        return _waypoints;
    }

    private static void onClientTick(Vibrato mod) {
        if (!Vibrato.inGame()) {
            _wasDead = false;
            return;
        }
        ClientPlayerEntity player = mod.getPlayer();
        attach(player.getGameProfile().getName());

        boolean dead = player.isDead();
        if (dead && !_wasDead) {
            recordDeath(mod, player.getBlockPos());
        }
        _wasDead = dead;
    }

    private static void attach(String botName) {
        Path file = Paths.get(CONFIG_FOLDER, "waypoints-" + botName + ".json");
        if (file.equals(_waypointFile)) return;
        _waypointFile = file;
        _waypoints = WaypointMemory.load(file);
    }

    private static void recordDeath(Vibrato mod, BlockPos pos) {
        String dimension = mod.getWorld().getRegistryKey().getValue().toString();
        _waypoints.set(WaypointKind.DEATH, new Waypoint(WaypointKind.DEATH, dimension, pos.getX(), pos.getY(), pos.getZ()));
        try {
            _waypoints.save(_waypointFile);
        } catch (IOException e) {
            Debug.logError("Failed to save waypoints: " + e.getMessage());
        }
    }
}
