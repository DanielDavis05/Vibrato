package dev.vibrato.human.wire;

/**
 * Reasons to pass Baritone's keys through unshaped this tick. Each is a plain flag the Minecraft side fills in.
 *
 * @param killSwitch       {@code -Dvibrato.humanKeys=off}
 * @param notControlled    the player's input is not Baritone's, or Baritone has no path to follow
 * @param inFluid          in water or lava
 * @param climbing         on a ladder, vine or scaffolding
 * @param flying           elytra or creative flight
 * @param riding           mounted on a vehicle
 * @param sneaking         sneak held
 * @param interacting      Baritone is breaking or placing (attack or use held)
 * @param safetyChain      {@code NavSafetyChain}, {@code MobDefenseChain} or {@code MLGBucketFallChain} is in charge
 */
public record Bypass(
        boolean killSwitch,
        boolean notControlled,
        boolean inFluid,
        boolean climbing,
        boolean flying,
        boolean riding,
        boolean sneaking,
        boolean interacting,
        boolean safetyChain
) {

    public static final Bypass NONE = new Bypass(false, false, false, false, false, false, false, false, false);

    public boolean any() {
        return killSwitch || notControlled || inFluid || climbing || flying || riding || sneaking || interacting
                || safetyChain;
    }

    /**
     * Name of the first reason that applies, for debug logs, or {@code "none"}.
     */
    public String reason() {
        if (killSwitch) return "killSwitch";
        if (notControlled) return "notControlled";
        if (inFluid) return "inFluid";
        if (climbing) return "climbing";
        if (flying) return "flying";
        if (riding) return "riding";
        if (sneaking) return "sneaking";
        if (interacting) return "interacting";
        if (safetyChain) return "safetyChain";
        return "none";
    }
}
