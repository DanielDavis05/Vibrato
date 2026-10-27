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
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the {@link DirectorTask} once per world session when autostart is on. From the title screen it either loads
 * or creates the configured singleplayer world, or connects to the configured server.
 */
public final class FleetBootstrap {

    private static final int TICKS_PER_SECOND = 20;

    private static long _ticksSinceJoin;
    private static boolean _started;
    private static boolean _manuallyStopped;
    private static long _startedAtMillis;
    private static boolean _everJoined;
    private static long _lastConnectAttempt = AutostartGate.NEVER;
    private static long _lastWorldAttempt = AutostartGate.NEVER;

    private FleetBootstrap() {}

    public static void install(Vibrato mod) {
        EventBus.subscribe(ClientTickEvent.class, evt -> onClientTick(mod));
    }

    /** Suppress automatic starts and restarts until the current world session ends. */
    public static void stopForSession() {
        _manuallyStopped = true;
    }

    private static void onClientTick(Vibrato mod) {
        FleetConfig config = mod.getFleetConfig();
        if (!Vibrato.inGame()) {
            _ticksSinceJoin = 0;
            _started = false;
            _manuallyStopped = false;
            if (config.world() != null) {
                maybeEnterWorld(config);
            } else {
                maybeConnect(config);
            }
            return;
        }
        _everJoined = true;
        _ticksSinceJoin++;
        if (_ticksSinceJoin == 1 && config.autostart()) {
            MinecraftClient.getInstance().mouse.unlockCursor();
        }

        if (_manuallyStopped) return;

        boolean busy = mod.getUserTaskChain().isActive() && !mod.getUserTaskChain().isRunningIdleTask();
        boolean directorRunning = mod.getUserTaskChain().getCurrentTask() instanceof DirectorTask;
        long now = System.currentTimeMillis();
        if (_started) {
            if (AutostartGate.shouldRestartDirector(config.autostart(), directorRunning, busy && !directorRunning,
                    now, _startedAtMillis)) {
                Debug.logInternal("Director is no longer running, restarting it");
                start(mod, config);
                _startedAtMillis = now;
            }
            return;
        }
        long delayTicks = (long) config.startDelaySeconds() * TICKS_PER_SECOND;
        if (AutostartGate.decideStart(true, config.autostart(), _ticksSinceJoin, delayTicks, false, busy)
                == AutostartGate.Decision.START) {
            _started = true;
            _startedAtMillis = now;
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

    private static void maybeEnterWorld(FleetConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean onTitle = client.currentScreen instanceof TitleScreen;
        long now = System.currentTimeMillis();
        if (!AutostartGate.shouldEnterWorld(onTitle, true, _everJoined, now, _lastWorldAttempt)) {
            return;
        }
        _lastWorldAttempt = now;
        String world = config.world();
        IntegratedServerLoader loader = client.createIntegratedServerLoader();
        if (client.getLevelStorage().levelExists(world)) {
            Debug.logInternal("Loading world " + world);
            loader.start(world, () -> Debug.logWarning("Loading world " + world + " was cancelled"));
            return;
        }
        Debug.logInternal("Creating world " + world + " seed=" + config.worldSeed());
        LevelInfo levelInfo = new LevelInfo(world, GameMode.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(config.worldSeed(), true, false);
        loader.createAndStart(world, levelInfo, generatorOptions, FleetBootstrap::defaultDimensions, client.currentScreen);
    }

    private static DimensionOptionsRegistryHolder defaultDimensions(DynamicRegistryManager registries) {
        return registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT)
                .createDimensionsRegistryHolder();
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
