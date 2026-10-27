package dev.vibrato.advancement;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import net.minecraft.util.Identifier;

import java.util.List;

/** Logs how many advancements the client has, and which curated goals are still open. */
public class AdvancementReportTask extends Task {

    private boolean _done;

    @Override
    protected void onStart(Vibrato mod) {
        _done = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        AdvancementTracker tracker = new AdvancementTracker(ClientAdvancements::doneIds);
        List<Identifier> wanted = AdvancementGoals.ALL.stream()
                .map(goal -> Identifier.of("minecraft", goal.path()))
                .toList();
        List<Identifier> remaining = tracker.remainingFrom(wanted);
        mod.log("Advancements done: " + tracker.doneCount() + ". Curated goals open: " + remaining.size()
                + " of " + wanted.size() + ".");
        _done = true;
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AdvancementReportTask;
    }

    @Override
    protected String toDebugString() {
        return "Checking advancement progress";
    }
}
