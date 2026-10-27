package dev.vibrato.human.wire;

import dev.vibrato.human.motion.HumanProfile;
import java.util.List;
import java.util.SplittableRandom;

/** Conservative, deterministic-on-seed hop decision, called once per input tick. */
public final class SprintHop {
    private final SplittableRandom random;
    private final HumanProfile profile;
    private long tick;
    private long nextHop;
    private boolean lastGround;
    private boolean hopped;
    private int runUpTicks;

    public SprintHop(long seed, HumanProfile profile) {
        random = new SplittableRandom(seed);
        this.profile = profile;
    }

    public boolean tick(KeyAdapter.Flags keys, PathView view, List<MovementKind> movements, boolean clear, boolean enabled) {
        tick++;
        boolean landing = view.onGround() && !lastGround;
        lastGround = view.onGround();
        boolean eligible = enabled && clear && keys.forward() && keys.sprint()
                && !keys.back() && !keys.left() && !keys.right() && !keys.jump() && !keys.sneak()
                && straightFlat(view.nodes(), movements);
        if (!eligible) {
            hopped = false;
            runUpTicks = 0;
            return false;
        }
        if (view.onGround()) runUpTicks++; else runUpTicks = 0;
        boolean firstHop = !hopped && runUpTicks >= 6;
        if (!view.onGround() || tick < nextHop || !(firstHop || hopped && landing)) return false;
        hopped = true;
        // The profile controls the cadence and timing jitter; require a fresh landing for each press.
        nextHop = tick + Math.max(1, Math.round(8 + profile.jumpTimingBiasTicks()
                + profile.jumpTimingSigmaTicks() * random.nextGaussian()));
        return true;
    }

    public static boolean straightFlat(List<PathView.Node> nodes, List<MovementKind> movements) {
        if (nodes.size() < 4 || movements.size() < 3) return false;
        double dx = 0, dz = 0;
        for (int i = 0; i < 3; i++) {
            if (movements.get(i) != MovementKind.TRAVERSE) return false;
            var a = nodes.get(i); var b = nodes.get(i + 1);
            double x = b.x() - a.x(), z = b.z() - a.z(), length = Math.hypot(x, z);
            if (a.y() != b.y() || length < .99 || length > 1.01) return false;
            if (i > 0 && (x * dx + z * dz) / length < Math.cos(Math.toRadians(20))) return false;
            dx = x / length; dz = z / length;
        }
        return true;
    }
}
