package dev.vibrato.trackers;

import dev.vibrato.Vibrato;

import java.util.ArrayList;

public class TrackerManager {

    private final ArrayList<Tracker> _trackers = new ArrayList<>();

    private final Vibrato _mod;

    private boolean _wasInGame = false;

    public TrackerManager(Vibrato mod) {
        _mod = mod;
    }

    public void tick() {
        boolean inGame = Vibrato.inGame();
        if (!inGame && _wasInGame) {
            // Reset when we leave our world
            for (Tracker tracker : _trackers) {
                tracker.reset();
            }
            // This is a a spaghetti. Fix at some point.
            _mod.getChunkTracker().reset(_mod);
            _mod.getMiscBlockTracker().reset();
        }
        _wasInGame = inGame;

        for (Tracker tracker : _trackers) {
            tracker.setDirty();
        }
    }

    public void addTracker(Tracker tracker) {
        tracker._mod = _mod;
        _trackers.add(tracker);
    }
}
