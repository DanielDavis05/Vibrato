package dev.vibrato.nav;

public enum UnstuckAction {
    JUMP,
    WATER_FORWARD_SPRINT,
    RECENTER_ON_BLOCK,
    SIDESTEP_LEFT,
    SIDESTEP_RIGHT,
    BACK_OFF_AND_REPATH,
    BREAK_BLOCKING_BLOCK,
    PLACE_STEP_BLOCK,
    DIG_UP,
    DIG_DOWN,
    SWIM_TO_SURFACE,
    CLIMB_OUT_OF_WATER,
    ABANDON_TASK,
    ESCAPE_SURFACE;

    /** Water rescues must take movement control from Baritone before applying their inputs. */
    public boolean cancelsPath() {
        return this == BACK_OFF_AND_REPATH || this == ABANDON_TASK
                || this == SWIM_TO_SURFACE || this == CLIMB_OUT_OF_WATER;
    }
}
