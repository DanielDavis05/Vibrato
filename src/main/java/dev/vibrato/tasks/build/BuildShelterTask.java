package dev.vibrato.tasks.build;

/** A three-high hut with door, torch, bed and chest where they fit. */
public class BuildShelterTask extends BuildBlueprintTask {

    public BuildShelterTask(Palette palette, int width, int depth) {
        super(Blueprints.hut(width, depth, 3), null, palette);
    }
}
