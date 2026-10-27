package dev.vibrato.human;

import baritone.api.utils.Rotation;
import baritone.vibrato.RotationSmoother;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Human-like camera for Baritone. Each axis follows a WindMouse-style model: a pull toward the target, a decaying wind
 * term and a speed cap. The acceleration limit gives a bell-shaped speed profile (ease in, cruise, ease out). Turns
 * over 60 degrees may overshoot the target slightly before correcting back onto it.
 * <p>
 * {@link #smooth} only reads the axis state, so Baritone can call it several times per tick. The state advances once
 * per tick in {@link #onTick}, from the last request.
 */
public final class HumanLook implements RotationSmoother {

    private static final double OVERSHOOT_TURN_DEG = 60;
    private static final double REARM_DEG = 10;
    private static final double WIND_FULL_DISTANCE_DEG = 10;
    private static final double WIND_DAMPING = 0.7;

    private final HumanLookConfig cfg;
    private final RandomGenerator rng;
    private final Axis yaw;
    private final Axis pitch;
    private Rotation lastPrevious;
    private Rotation lastDesired;

    public HumanLook(long seed, HumanLookConfig cfg) {
        this.cfg = cfg;
        this.rng = new SplittableRandom(seed);
        this.yaw = new Axis(cfg.maxYawDegPerTick(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        this.pitch = new Axis(cfg.maxPitchDegPerTick(), -90, 90);
    }

    @Override
    public Rotation smooth(Rotation previous, Rotation desired) {
        lastPrevious = previous;
        lastDesired = desired;
        Rotation smoothed = next(previous, desired);
        LookTrace.observe(previous, desired, smoothed);
        return smoothed;
    }

    @Override
    public void onTick() {
        LookTrace.endTick();
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
        yaw.advance(previous.getYaw(), shortestYawTarget(previous, desired), applied.getYaw());
        pitch.advance(previous.getPitch(), desired.getPitch(), applied.getPitch());
    }

    /**
     * Baritone's targets are absolute yaw, so the yaw target is taken on the shortest arc from the current yaw.
     */
    private static double shortestYawTarget(Rotation previous, Rotation desired) {
        return previous.getYaw() + Rotation.normalizeYaw(desired.getYaw() - previous.getYaw());
    }

    private Rotation next(Rotation previous, Rotation desired) {
        double yawNext = yaw.next(previous.getYaw(), shortestYawTarget(previous, desired));
        double pitchNext = pitch.next(previous.getPitch(), desired.getPitch());
        return new Rotation((float) yawNext, (float) pitchNext);
    }

    /**
     * Motion state of one axis. The velocity is the last applied step, so the speed profile carries across ticks.
     */
    private final class Axis {

        private final double maxSpeed;
        private final double accel;
        private final double min;
        private final double max;
        private double velocity;
        private double wind;
        private double overshoot;
        private boolean armed;

        Axis(double maxSpeed, double min, double max) {
            this.maxSpeed = maxSpeed;
            this.accel = cfg.gain() * maxSpeed;
            this.min = min;
            this.max = max;
        }

        /**
         * Pure: the angle this axis moves to on this tick, given the current angle and the desired one.
         */
        double next(double current, double desired) {
            double target = limit(desired + (overshooting(current, desired) ? overshoot : 0));
            double error = target - current;
            if (error == 0) {
                return current;
            }
            double dir = Math.signum(error);
            double distance = Math.abs(error);
            double along = velocity * dir;
            double brakeLimited = Math.min(maxSpeed, brakingSpeed(distance));
            double speed = Math.max(along - accel, Math.min(along + accel, brakeLimited));
            double windPush = wind * Math.min(1, distance / WIND_FULL_DISTANCE_DEG);
            double move = Math.max(-maxSpeed, Math.min(maxSpeed, dir * speed + windPush));
            move = Math.min(move * dir, distance) * dir;
            return limit(current + move);
        }

        /**
         * Largest speed from which the axis can still brake to a stop inside {@code distance}, given the accel limit.
         */
        private double brakingSpeed(double distance) {
            return (-3 * accel + Math.sqrt(9 * accel * accel + 8 * accel * distance)) / 2;
        }

        private boolean overshooting(double current, double desired) {
            if (overshoot == 0) {
                return false;
            }
            double remaining = limit(desired + overshoot) - current;
            return Math.abs(remaining) > cfg.arrivalToleranceDeg() && Math.signum(remaining) == Math.signum(overshoot);
        }

        /**
         * Commits one tick: {@code applied} is the angle the camera actually took from {@code previous}.
         */
        void advance(double previous, double desired, double applied) {
            velocity = applied - previous;
            if (cfg.windStrength() > 0) {
                wind = WIND_DAMPING * wind + cfg.windStrength() * rng.nextGaussian();
            }
            if (!overshooting(applied, desired)) {
                overshoot = 0;
            }
            double error = desired - applied;
            if (!armed) {
                if (Math.abs(error) > OVERSHOOT_TURN_DEG) {
                    armed = true;
                    if (rng.nextDouble() < cfg.overshootChance()) {
                        overshoot = Math.signum(error) * cfg.overshootMaxDeg() * (0.4 + 0.6 * rng.nextDouble());
                    }
                }
            } else if (Math.abs(error) < REARM_DEG) {
                armed = false;
            }
        }

        /**
         * Drops all motion, used on ticks where Baritone is not aiming.
         */
        void rest() {
            velocity = 0;
            wind = 0;
            overshoot = 0;
            armed = false;
        }

        private double limit(double angle) {
            return Math.max(min, Math.min(max, angle));
        }
    }
}
