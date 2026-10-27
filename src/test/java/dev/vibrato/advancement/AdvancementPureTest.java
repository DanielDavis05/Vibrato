package dev.vibrato.advancement;

import dev.vibrato.director.FeatureArea;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AdvancementPureTest {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    @Test
    void everyGoalHasUniqueLowercaseNamespacedIdAndNonBlankSpec() {
        assertFalse(AdvancementGoals.ALL.isEmpty());
        Set<String> ids = AdvancementGoals.ALL.stream().map(AdvancementGoal::id).collect(Collectors.toSet());
        assertEquals(AdvancementGoals.ALL.size(), ids.size(), "ids must be unique");
        for (AdvancementGoal goal : AdvancementGoals.ALL) {
            assertTrue(ID.matcher(goal.id()).matches(), goal.id());
            assertFalse(goal.taskSpec().isBlank(), goal.id());
            assertTrue(goal.taskSpec().startsWith("get "), "only the get command is wired: " + goal.id());
        }
    }

    @Test
    void recordRejectsBlankSpec() {
        assertThrows(IllegalArgumentException.class,
                () -> new AdvancementGoal("minecraft:story/x", FeatureArea.GATHER, " "));
    }

    @Test
    void findReturnsGoalById() {
        assertTrue(AdvancementGoals.find("minecraft:story/mine_stone").isPresent());
        assertTrue(AdvancementGoals.find("minecraft:nope").isEmpty());
    }

    @Test
    void trackerReportsDoneAndRemaining() {
        Identifier mine = Identifier.of("minecraft", "story/mine_stone");
        Identifier smelt = Identifier.of("minecraft", "story/smelt_iron");
        Identifier diamond = Identifier.of("minecraft", "story/mine_diamond");
        AdvancementTracker tracker = new AdvancementTracker(() -> List.of(mine, smelt));
        assertTrue(tracker.isDone(mine));
        assertFalse(tracker.isDone(diamond));
        assertEquals(2, tracker.doneCount());
        assertEquals(List.of(diamond), tracker.remainingFrom(List.of(mine, diamond)));
    }
}
