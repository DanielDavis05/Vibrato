package dev.vibrato.nav;

public enum UnstuckAction {
    JUMP,
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

    /** Only these actions may cancel the path Baritone is following; the others move the bot around it. */
    public boolean cancelsPath() {
        return this == BACK_OFF_AND_REPATH || this == ABANDON_TASK;
    }
}
