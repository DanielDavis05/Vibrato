package dev.vibrato.tasks.squashed;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.container.CraftInTableTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.container.UpgradeInSmithingTableTask;
import dev.vibrato.tasks.squashed.CataloguedResourceTask.TaskSquasher;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

public class CataloguedResourceTask extends ResourceTask {


    private final TaskSquasher _squasher;
    private final ItemTarget[] _targets;
    private final List<ResourceTask> _tasksToComplete;

    public CataloguedResourceTask(boolean squash, ItemTarget... targets) {
        super(targets);
        _squasher = new TaskSquasher();
        _targets = targets;
        _tasksToComplete = new ArrayList<>(targets.length);

        for (ItemTarget target : targets) {
            if (target != null) {
                _tasksToComplete.add(TaskCatalogue.getItemTask(target));
            }
        }

        if (squash) {
            squashTasks(_tasksToComplete);
        }
    }

    public CataloguedResourceTask(ItemTarget... targets) {
        this(true, targets);
    }

    @Override
    protected void onResourceStart(Vibrato mod) {

    }

    @Override
    protected Task onResourceTick(Vibrato mod) {
        for (ResourceTask task : _tasksToComplete) {
            for (ItemTarget target : task.getItemTargets()) {
                // If we failed to meet this task's targets, do the task.
                if (!StorageHelper.itemTargetsMetInventory(mod, target)) return task;
            }
        }
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        for (ResourceTask task : _tasksToComplete) {
            for (ItemTarget target : task.getItemTargets()) {
                if (!StorageHelper.itemTargetsMetInventory(mod, target)) return false;
            }
        }
        // All targets are met.
        return true;
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        // Useless
        return false;
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CataloguedResourceTask task) {
            return Arrays.equals(task._targets, _targets);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Get catalogued: " + ArrayUtils.toString(_targets);
    }

    private void squashTasks(List<ResourceTask> tasks) {
        _squasher.addTasks(tasks);
        tasks.clear();
        tasks.addAll(_squasher.getSquashed());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static class TaskSquasher {

        private final Map<Class, dev.vibrato.tasks.squashed.TypeSquasher> _squashMap = new HashMap<>();

        private final List<ResourceTask> _unSquashableTasks = new ArrayList<>();

        public TaskSquasher() {
            _squashMap.put(CraftInTableTask.class, new CraftSquasher());
            _squashMap.put(UpgradeInSmithingTableTask.class, new SmithingSquasher());
            //_squashMap.put(MineAndCollectTask.class)
        }

        public void addTask(ResourceTask t) {
            Class type = t.getClass();
            if (_squashMap.containsKey(type)) {
                _squashMap.get(type).add(t);
            } else {
                //Debug.logMessage("Unsquashable: " + type + ": " + t);
                _unSquashableTasks.add(t);
            }
        }

        public void addTasks(List<ResourceTask> tasks) {
            for (ResourceTask task : tasks) {
                addTask(task);
            }
        }

        public List<ResourceTask> getSquashed() {
            List<ResourceTask> result = new ArrayList<>();

            for (Class type : _squashMap.keySet()) {
                result.addAll(_squashMap.get(type).getSquashed());
            }
            result.addAll(_unSquashableTasks);

            return result;
        }
    }


}
