package dev.vibrato.tasks.build;

/**
 * Tills a nine-wide strip of farmland with a hoe. The water channel from the blueprint is not placed, so the
 * farmland is not hydrated until water is added by hand.
 */
public class BuildFarmTask extends BuildBlueprintTask {

    public BuildFarmTask(int rows) {
        super(Blueprints.farm(rows), null, Palette.OAK);
    }
}
