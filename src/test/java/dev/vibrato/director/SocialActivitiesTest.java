package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SocialActivitiesTest {

    private static final Pattern SNAKE = Pattern.compile("[a-z][a-z0-9_]*");

    @Test
    void registersAtLeastTwelveUniqueSnakeCaseActivities() {
        ActivityRegistry registry = new ActivityRegistry();
        SocialActivities.register(registry);
        assertTrue(registry.size() >= 12, "only " + registry.size());
        for (Activity activity : registry.all()) {
            assertTrue(SNAKE.matcher(activity.id()).matches(), activity.id());
            assertNotNull(activity.area(), activity.id());
            for (ParamSpec param : activity.params()) {
                assertTrue(param.variants() >= 1, activity.id());
            }
        }
    }

    @Test
    void everyAdvancementGoalHasAnActivityThatProvidesItsFact() {
        ActivityRegistry registry = new ActivityRegistry();
        SocialActivities.register(registry);
        for (String id : Set.of("minecraft:story/mine_stone", "minecraft:story/mine_diamond")) {
            assertFalse(registry.providersOf(Fact.of("advancement:" + id + ":done")).isEmpty(), id);
        }
    }
}
