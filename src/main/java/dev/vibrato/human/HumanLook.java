package dev.vibrato.human;

import baritone.api.utils.Rotation;
import baritone.vibrato.RotationSmoother;
import dev.vibrato.human.motion.HumanProfile;
import dev.vibrato.human.motion.Population;
import dev.vibrato.human.motion.SensitivityGrid;
import dev.vibrato.human.motion.Submovements;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;
import java.util.random.RandomGenerator;

/**
 * Human-like camera for Baritone. Each axis follows a WindMouse-style model: a pull toward the target, a decaying wind
 * term and a speed cap. The acceleration limit gives a bell-shaped speed profile (ease in, cruise, ease out).
 * <p>
 * A new target (one that has moved more than {@link #RETARGET_DEG} from the last planned target) is split by
 * {@link Submovements} into a ballistic primary move and a few corrections. Each submovement caps the axis speed on a
 * sine bell over its ticks, so a turn is a short sequence of bell-shaped moves. Overshoot comes from that plan: a primary
 * that passes the target is brought back by the corrections, with the chance and size the profile sets. A plan is
 * drawn from the seed and the turn count, so the first tick of a turn already follows it and {@link #fork} predicts it.
 * <p>
 * With a mouse sensitivity source set, each tick's yaw and pitch move snaps to the mouse grid. The sub-step remainder
 * carries to the next tick, so the average speed is unchanged.
 * <p>
 * {@link #smooth} only reads the axis state, so Baritone can call it several times per tick. The state advances once
 * per tick in {@link #onTick}, from the last request.
 */
public final class HumanLook implements RotationSmoother {

    /** A target that moves further than this from the last planned target starts a new plan. */
    private static final double RETARGET_DEG = 2;
    private static final double WIND_FULL_DISTANCE_DEG = 10;
    private static final double WIND_DAMPING = 0.7;

    private final long seed;
    private final HumanLookConfig cfg;
    private final HumanProfile profile;
    private final RandomGenerator rng;
    private final Axis yaw;
    private final Axis pitch;
    private final boolean simulation;
    private DoubleSupplier sensitivity;
    private Rotation lastPrevious;
    private Rotation lastDesired;

    public HumanLook(long seed, HumanLookConfig cfg) {
        this(seed, cfg, HumanProfile.typical(Population.DEFAULT));
    }

    public HumanLook(long seed, HumanLookConfig cfg, HumanProfile profile) {
        this(seed, cfg, profile, false);
    }

    private HumanLook(long seed, HumanLookConfig cfg, HumanProfile profile, boolean simulation) {
        this.seed = seed;
        this.cfg = cfg;
        this.profile = profile;
        this.rng = new SplittableRandom(seed);
        this.simulation = simulation;
        this.yaw = new Axis(cfg.maxYawDegPerTick(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true, 1);
        this.pitch = new Axis(cfg.maxPitchDegPerTick(), -90, 90, false, 2);
    }

    /**
     * Sets the mouse sensitivity, in [0, 1], that each tick's turn snaps to. Null turns the quantization off.
     */
    public void setSensitivitySource(DoubleSupplier mouseSensitivity) {
        this.sensitivity = mouseSensitivity;
    }

    @Override
    public HumanLook fork() {
        // The copy shares the seed, so it draws the same plans for the same turns. Only the wind noise to come is an
        // estimate: the live stream is never touched. The motion, the current plan and the pending request are exact.
        HumanLook copy = new HumanLook(seed, cfg, profile, true);
        copy.sensitivity = sensitivity;
        copy.yaw.copyState(yaw);
        copy.pitch.copyState(pitch);
        copy.lastPrevious = lastPrevious;
        copy.lastDesired = lastDesired;
        return copy;
    }

    @Override
    public Rotation smooth(Rotation previous, Rotation desired) {
        lastPrevious = previous;
        lastDesired = desired;
        Rotation smoothed = next(previous, desired);
        if (!simulation) LookTrace.observe(previous, desired, smoothed);
        return smoothed;
    }

    @Override
    public void onTick() {
        if (!simulation) LookTrace.endTick();
        if (lastPrevious == null) {
            yaw.rest();
            pitch.rest();
            return;
        }
        Rotation previous = lastPrevious;
        Rotation desired = lastDesired;
        lastPrevious = null;
        lastDesired = null;
        Rotation applied = next(previous, desired);
        double yawTarget = shortestYawTarget(previous, desired);
        yaw.advance(previous.getYaw(), yawTarget, yaw.next(previous.getYaw(), yawTarget), applied.getYaw());
        pitch.advance(previous.getPitch(), desired.getPitch(), pitch.next(previous.getPitch(), desired.getPitch()), applied.getPitch());
    }

    /**
     * Baritone's targets are absolute yaw, so the yaw target is taken on the shortest arc from the current yaw.
     */
    private static double shortestYawTarget(Rotation previous, Rotation desired) {
        return previous.getYaw() + Rotation.normalizeYaw(desired.getYaw() - previous.getYaw());
    }

    /**
     * Pure: the rotation the camera takes on this tick, given the current one and the desired one.
     */
    private Rotation next(Rotation previous, Rotation desired) {
        double sens = sensitivity();
        double yawRaw = yaw.next(previous.getYaw(), shortestYawTarget(previous, desired));
        double pitchRaw = pitch.next(previous.getPitch(), desired.getPitch());
        return new Rotation(
                (float) yaw.snap(previous.getYaw(), yawRaw, sens),
                (float) pitch.snap(previous.getPitch(), pitchRaw, sens)
        );
    }

    /** Mouse sensitivity clamped to [0, 1], or NaN when there is no source, which means no quantization. */
    private double sensitivity() {
        if (sensitivity == null) {
            return Double.NaN;
        }
        double value = sensitivity.getAsDouble();
        return Double.isNaN(value) ? Double.NaN : Math.max(0, Math.min(1, value));
    }

    /**
     * Where an axis is in its plan: the submovements, their absolute end angles, and the current submovement and the
     * ticks spent on it. Immutable, so forks can share it.
     */
    private record Plan(List<Submovements.Step> moves, double[] ends, int index, int elapsed) {

        /** The plan one tick later. */
        Plan stepped() {
            if (index >= moves.size()) {
                return this;
            }
            if (elapsed + 1 >= moves.get(index).ticks()) {
                return new Plan(moves, ends, index + 1, 0);
            }
            return new Plan(moves, ends, index, elapsed + 1);
        }
    }

    /**
     * The signed move of one submovement on tick {@code elapsed}. It is a sine bell over the submovement's ticks, so the
     * moves of one submovement sum to its amplitude.
     */
    private static double bell(Submovements.Step step, int elapsed) {
        int ticks = step.ticks();
        double total = 0;
        for (int j = 0; j < ticks; j++) {
            total += Math.sin(Math.PI * (j + 0.5) / ticks);
        }
        return step.amplitude() * Math.sin(Math.PI * (elapsed + 0.5) / ticks) / total;
    }

    /**
     * Motion state of one axis. The velocity is the last applied step, so the speed profile carries across ticks.
     */
    private final class Axis {

        private final double maxSpeed;
        private final double accel;
        private final double min;
        private final double max;
        private final boolean circular;
        private final long salt;
        private double velocity;
        private double wind;
        private double carry;
        private long turns;
        private double plannedTarget;
        private Plan plan;

        Axis(double maxSpeed, double min, double max, boolean circular, long salt) {
            this.maxSpeed = maxSpeed;
            this.accel = cfg.gain() * maxSpeed;
            this.min = min;
            this.max = max;
            this.circular = circular;
            this.salt = salt;
        }

        void copyState(Axis source) {
            velocity = source.velocity;
            wind = source.wind;
            carry = source.carry;
            turns = source.turns;
            plannedTarget = source.plannedTarget;
            plan = source.plan;
        }

        /**
         * Pure: the angle this axis moves to on this tick, before the mouse grid. While a plan is running the axis
         * heads for the current submovement's end, at most as fast as that submovement's bell. Otherwise it heads for
         * the desired angle.
         */
        double next(double current, double desired) {
            Plan active = activePlan(current, desired);
            boolean following = active.index() < active.moves().size();
            double target = following ? limit(active.ends()[active.index()]) : desired;
            double error = target - current;
            if (error == 0) {
                return current;
            }
            double dir = Math.signum(error);
            double distance = Math.abs(error);
            double cap = following
                    ? Math.min(maxSpeed, Math.abs(bell(active.moves().get(active.index()), active.elapsed())))
                    : maxSpeed;
            double brakeLimited = following ? cap : Math.min(cap, brakingSpeed(distance));
            double along = velocity * dir;
            double speed = Math.max(along - accel, Math.min(along + accel, brakeLimited));
            double windPush = wind * Math.min(1, distance / WIND_FULL_DISTANCE_DEG);
            double move = Math.max(-maxSpeed, Math.min(maxSpeed, dir * speed + windPush));
            move = Math.min(move * dir, distance) * dir;
            return limit(current + move);
        }

        /**
         * Pure: snaps a raw move to the mouse grid, adding the remainder carried from earlier ticks. Returns the raw move
         * when {@code sens} is NaN (no source).
         */
        double snap(double current, double raw, double sens) {
            if (Double.isNaN(sens) || raw == current) {
                return raw;
            }
            double step = SensitivityGrid.degreesPerPixel(sens);
            double largest = Math.floor(maxSpeed / step) * step;
            double delta = SensitivityGrid.quantize(raw - current + carry, sens);
            delta = Math.max(-largest, Math.min(largest, delta));
            return limit(current + delta);
        }

        /**
         * Commits one tick. {@code raw} is the move before the mouse grid and {@code applied} the one the camera took
         * from {@code previous}.
         */
        void advance(double previous, double desired, double raw, double applied) {
            velocity = applied - previous;
            carry = raw == applied ? 0 : carry + raw - applied;
            if (cfg.windStrength() > 0) {
                wind = WIND_DAMPING * wind + cfg.windStrength() * rng.nextGaussian();
            }
            if (isNewTarget(desired)) {
                plan = planFor(previous, desired).stepped();
                plannedTarget = desired;
                turns++;
            } else {
                plan = plan.stepped();
            }
        }

        /**
         * Drops all motion, used on ticks where Baritone is not aiming.
         */
        void rest() {
            velocity = 0;
            wind = 0;
            carry = 0;
            plan = null;
        }

        /** The plan this tick follows: the stored one while the target stays put, otherwise a fresh plan from {@code start}. */
        private Plan activePlan(double start, double target) {
            if (isNewTarget(target)) {
                return planFor(start, target);
            }
            return plan;
        }

        private boolean isNewTarget(double target) {
            return plan == null || Math.abs(difference(target, plannedTarget)) > RETARGET_DEG;
        }

        /**
         * Splits the error from {@code start} into submovements, drawn from this axis's seed for the current turn. Pure:
         * the same inputs always give the same plan. Zero-amplitude moves are dropped.
         */
        private Plan planFor(double start, double target) {
            RandomGenerator planRng = new SplittableRandom(seed + salt * 0x9E3779B97F4A7C15L + turns * 0xBF58476D1CE4E5B9L);
            List<Submovements.Step> kept = new ArrayList<>();
            List<Double> endAngles = new ArrayList<>();
            double position = start;
            for (Submovements.Step step : Submovements.plan(difference(target, start), profile, planRng)) {
                if (step.amplitude() == 0) {
                    continue;
                }
                position += step.amplitude();
                kept.add(step);
                endAngles.add(position);
            }
            return new Plan(List.copyOf(kept), endAngles.stream().mapToDouble(Double::doubleValue).toArray(), 0, 0);
        }

        /**
         * Largest speed from which the axis can still brake to a stop inside {@code distance}, given the accel limit.
         */
        private double brakingSpeed(double distance) {
            return (-3 * accel + Math.sqrt(9 * accel * accel + 8 * accel * distance)) / 2;
        }

        /** The signed gap from {@code b} to {@code a}, on the shortest arc for yaw. */
        private double difference(double a, double b) {
            return circular ? Rotation.normalizeYaw((float) (a - b)) : a - b;
        }

        private double limit(double angle) {
            return Math.max(min, Math.min(max, angle));
        }
    }
}
