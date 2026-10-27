package dev.vibrato.tasks.build;

/** A fenced animal pen with a gate. */
public class BuildPenTask extends BuildBlueprintTask {

    public BuildPenTask(Palette palette, int width, int depth) {
        super(Blueprints.pen(width, depth), null, palette);
    }
}
