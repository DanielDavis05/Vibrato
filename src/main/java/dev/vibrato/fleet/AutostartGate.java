package dev.vibrato.fleet;

/** Pure decisions behind {@link FleetBootstrap}: when to start the director and when to connect to the server. */
public final class AutostartGate {

    /** Marks "no connection attempt yet". */
    public static final long NEVER = Long.MIN_VALUE;
    public static final long CONNECT_BACKOFF_MILLIS = 30_000;
    public static final long RESTART_BACKOFF_MILLIS = 60_000;

    private AutostartGate() {}

    public enum Decision {
        START, WAIT
    }

    /** Start only in game, with autostart on, once per session, after the delay, and never over a busy user task. */
    public static Decision decideStart(boolean inGame, boolean autostart, long ticksSinceJoin, long delayTicks,
                                       boolean alreadyStarted, boolean userTaskBusy) {
        if (!inGame || !autostart || alreadyStarted || userTaskBusy) {
            return Decision.WAIT;
        }
        return ticksSinceJoin >= delayTicks ? Decision.START : Decision.WAIT;
    }

    /**
     * Restart a director that is no longer the user task, unless another task owns the bot, at most once per
     * {@link #RESTART_BACKOFF_MILLIS}.
     */
    public static boolean shouldRestartDirector(boolean autostart, boolean directorRunning, boolean otherTaskBusy,
                                                long nowMillis, long lastStartMillis) {
        return autostart && !directorRunning && !otherTaskBusy && nowMillis - lastStartMillis >= RESTART_BACKOFF_MILLIS;
    }

    /**
     * Connect from the title screen when a server is configured, the bot has not joined a world in this process yet,
     * and the previous attempt (if any) is at least {@link #CONNECT_BACKOFF_MILLIS} old.
     */
    public static boolean shouldConnect(boolean onTitleScreen, boolean autostart, boolean hasServer, boolean everJoined,
                                        long nowMillis, long lastAttemptMillis) {
        if (!onTitleScreen || !autostart || !hasServer || everJoined) {
            return false;
        }
        return backoffElapsed(nowMillis, lastAttemptMillis);
    }

    /**
     * Load or create the configured singleplayer world from the title screen, once per process: no attempt after the
     * player has joined, and a new attempt only after {@link #CONNECT_BACKOFF_MILLIS} since the previous one.
     */
    public static boolean shouldEnterWorld(boolean onTitleScreen, boolean hasWorld, boolean everJoined,
                                           long nowMillis, long lastAttemptMillis) {
        if (!onTitleScreen || !hasWorld || everJoined) {
            return false;
        }
        return backoffElapsed(nowMillis, lastAttemptMillis);
    }

    private static boolean backoffElapsed(long nowMillis, long lastAttemptMillis) {
        return lastAttemptMillis == NEVER || nowMillis - lastAttemptMillis >= CONNECT_BACKOFF_MILLIS;
    }
}
