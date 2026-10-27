package dev.vibrato.tasks.build;

/** A round tower with an internal ladder and platforms. */
public class BuildTowerTask extends BuildBlueprintTask {

    public BuildTowerTask(Palette palette, int radius, int height) {
        super(Blueprints.tower(radius, height), null, palette);
    }
}
