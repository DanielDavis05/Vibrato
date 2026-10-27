package dev.vibrato.fleet;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.director.ActivityRegistry;
import dev.vibrato.director.BuiltinActivities;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.director.Persona;
import dev.vibrato.director.Personas;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.ClientTickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the {@link DirectorTask} once per world session when autostart is on, and connects to the configured server
 * from the title screen.
 */
public final class FleetBootstrap {

    private static final int TICKS_PER_SECOND = 20;

    private static long _ticksSinceJoin;
    private static boolean _started;
    private static boolean _everJoined;
    private static long _lastConnectAttempt = AutostartGate.NEVER;

    private FleetBootstrap() {}

    public static void install(Vibrato mod) {
        EventBus.subscribe(ClientTickEvent.class, evt -> onClientTick(mod));
    }

    private static void onClientTick(Vibrato mod) {
        FleetConfig config = mod.getFleetConfig();
        if (!Vibrato.inGame()) {
            _ticksSinceJoin = 0;
            _started = false;
            maybeConnect(config);
            return;
        }
        _everJoined = true;
        _ticksSinceJoin++;

        boolean busy = mod.getUserTaskChain().isActive() && !mod.getUserTaskChain().isRunningIdleTask();
        long delayTicks = (long) config.startDelaySeconds() * TICKS_PER_SECOND;
        if (AutostartGate.decideStart(true, config.autostart(), _ticksSinceJoin, delayTicks, _started, busy)
                == AutostartGate.Decision.START) {
            _started = true;
            start(mod, config);
        }
    }

    private static void start(Vibrato mod, FleetConfig config) {
        Persona persona = resolvePersona(config);
        Path coverageFile = Paths.get(config.dataDir(), "coverage-" + config.botId() + ".json");
        ActivityRegistry registry = BuiltinActivities.createRegistry();
        Debug.logInternal("Director starting: bot=" + config.botId() + " persona=" + persona.name());
        mod.runUserTask(new DirectorTask(registry, config.seed(), persona, config.planLength(), coverageFile));
    }

    private static Persona resolvePersona(FleetConfig config) {
        if (config.persona() == null) {
            return Personas.forSeed(config.seed());
        }
        Persona persona = Personas.byName(config.persona());
        if (persona == null) {
            Debug.logWarning("Unknown persona '" + config.persona() + "', using one derived from the seed");
            return Personas.forSeed(config.seed());
        }
        return persona;
    }

    private static void maybeConnect(FleetConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean onTitle = client.currentScreen instanceof TitleScreen;
        long now = System.currentTimeMillis();
        if (!AutostartGate.shouldConnect(onTitle, config.autostart(), config.serverAddress() != null, _everJoined,
                now, _lastConnectAttempt)) {
            return;
        }
        _lastConnectAttempt = now;
        String address = config.serverAddress();
        Debug.logInternal("Connecting to " + address);
        ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(address),
                new ServerInfo("vibrato", address, ServerInfo.ServerType.OTHER), false, null);
    }
}
