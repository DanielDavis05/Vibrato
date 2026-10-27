package dev.vibrato.nav;

/**
 * Classifies the bot's recent motion into a {@link StuckState}.
 * <p>
 * A window of N covers the newest N samples. Every check except {@link StuckState#LOW_AIR} needs the bot to be
 * trying to move, so an idle bot is never reported as stuck.
 */
public final class StuckDetector {

    private final StuckConfig _config;
    private final MotionSample[] _history;
    private int _size;
    private int _next;
    private int _edgeRun;

    public StuckDetector(StuckConfig config) {
        _config = config;
        _history = new MotionSample[config.windowTicks()];
    }

    public StuckState update(MotionSample sample) {
        // Work interrupts a motion window: do not diagnose a stall immediately after it ends.
        if (sample.productive()) {
            reset();
            return sample.underWater() && sample.airTicksLeft() < _config.lowAirTicks()
                    ? StuckState.LOW_AIR : StuckState.OK;
        }
        _history[_next] = sample;
        _next = (_next + 1) % _history.length;
        if (_size < _history.length) {
            _size++;
        }
        _edgeRun = sample.horizontalCollision() && sample.wantsToMove() && !sample.productive() ? _edgeRun + 1 : 0;
        return classify(sample);
    }

    public void reset() {
        _size = 0;
        _next = 0;
        _edgeRun = 0;
    }

    private StuckState classify(MotionSample s) {
        if (s.underWater() && s.airTicksLeft() < _config.lowAirTicks()) {
            return StuckState.LOW_AIR;
        }
        if (s.productive()) {
            return StuckState.OK;
        }
        if (s.inWater() && s.wantsToMove() && hasWindow(_config.waterTicks())
                && distance3d(ago(_config.waterTicks() - 1), s) < _config.waterMinMove()) {
            return StuckState.WATER_DRIFT;
        }
        if (_edgeRun >= _config.edgeJamTicks()) {
            return StuckState.EDGE_JAM;
        }
        if (s.wantsToMove()) {
            if (hasWindow(_config.spinTicks()) && isSpinning()) {
                return StuckState.SPINNING;
            }
            if (hasWindow(_config.oscillationTicks()) && isOscillating()) {
                return StuckState.OSCILLATING;
            }
            if (hasWindow(_config.stationaryTicks())
                    && distance3d(ago(_config.stationaryTicks() - 1), s) < _config.stationaryMinMove()) {
                return StuckState.STATIONARY_WITH_INTENT;
            }
        }
        return StuckState.OK;
    }

    private boolean isSpinning() {
        int n = _config.spinTicks();
        double degrees = 0;
        for (int i = 0; i < n - 1; i++) {
            degrees += Math.abs(wrapDegrees(ago(i).yaw() - ago(i + 1).yaw()));
        }
        return degrees > _config.spinMinDegrees()
                && distance3d(ago(n - 1), ago(0)) < _config.spinMaxNet();
    }

    /**
     * True when the bot only ever occupies two block cells, switching between them often, and ends up near where it
     * started.
     */
    private boolean isOscillating() {
        int n = _config.oscillationTicks();
        int startX = cell(ago(n - 1).x());
        int startZ = cell(ago(n - 1).z());
        int prevX = startX;
        int prevZ = startZ;
        int otherX = 0;
        int otherZ = 0;
        boolean hasOther = false;
        int switches = 0;
        for (int i = n - 2; i >= 0; i--) {
            int cx = cell(ago(i).x());
            int cz = cell(ago(i).z());
            if (cx == prevX && cz == prevZ) {
                continue;
            }
            switches++;
            prevX = cx;
            prevZ = cz;
            if (cx == startX && cz == startZ) {
                continue;
            }
            if (!hasOther) {
                otherX = cx;
                otherZ = cz;
                hasOther = true;
            } else if (cx != otherX || cz != otherZ) {
                return false;
            }
        }
        return hasOther && switches >= _config.oscillationMinSwitches()
                && distance3d(ago(n - 1), ago(0)) < _config.oscillationMaxNet();
    }

    private boolean hasWindow(int ticks) {
        return _size >= ticks;
    }

    /** The sample {@code ticksAgo} samples before the newest one; 0 is the newest. */
    private MotionSample ago(int ticksAgo) {
        return _history[Math.floorMod(_next - 1 - ticksAgo, _history.length)];
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    /** Vertical movement counts too, so a pillar (jump, place, repeat) is progress rather than standing still. */
    private static double distance3d(MotionSample a, MotionSample b) {
        double dy = a.y() - b.y();
        return Math.sqrt(dy * dy + Math.pow(a.x() - b.x(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private static double horizontalDistance(MotionSample a, MotionSample b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }
}
