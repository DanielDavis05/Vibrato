package dev.vibrato.tasks.squashed;

import dev.vibrato.tasks.container.CraftInTableTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.util.RecipeTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CraftSquasher extends TypeSquasher<CraftInTableTask> {
    @Override
    protected List<ResourceTask> getSquashed(List<CraftInTableTask> tasks) {

        List<RecipeTarget> targetRecipies = new ArrayList<>();

        for (CraftInTableTask task : tasks) {
            targetRecipies.addAll(Arrays.asList(task.getRecipeTargets()));
        }

        //Debug.logMessage("Squashed " + targetRecipies.size());

        return Collections.singletonList(new CraftInTableTask(targetRecipies.toArray(RecipeTarget[]::new)));
    }
}
