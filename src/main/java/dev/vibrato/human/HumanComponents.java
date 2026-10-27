package dev.vibrato.human;

/** Independent experiment switches; safety and task execution do not depend on these layers. */
public final class HumanComponents {
    private HumanComponents() {}

    public static boolean enabled(String component) {
        return !"off".equalsIgnoreCase(System.getProperty("vibrato.human"))
                && !"off".equalsIgnoreCase(System.getProperty("vibrato." + component));
    }

    public static boolean behavior() { return enabled("humanBehavior"); }
}
