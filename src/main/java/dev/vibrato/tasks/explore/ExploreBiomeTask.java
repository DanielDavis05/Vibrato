package dev.vibrato.tasks.explore;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.SearchWithinBiomeTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Finds a biome of the given kind. Searches the loaded chunks for it with {@link SearchWithinBiomeTask}, and wanders
 * when none are loaded. Ends as soon as the bot stands in the biome and logs the spot.
 * <p>
 * Limits: a biome that is not near the bot can take many chunk loads, so the activity budget is the real cap.
 * An unknown id ends the task at once.
 */
public class ExploreBiomeTask extends Task {

    private static final int WANDER_DISTANCE = 300;

    private final String _biomeId;
    private SearchWithinBiomeTask _search;
    private boolean _found;
    private boolean _unknown;

    public ExploreBiomeTask(String biomeId) {
        _biomeId = biomeId;
        _unknown = !ExplorableBiomes.isSupported(biomeId);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _found = false;
        if (!_unknown) {
            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", _biomeId));
            _search = new SearchWithinBiomeTask(key);
        }
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_unknown) {
            setDebugState("Unknown biome " + _biomeId);
            return null;
        }
        BlockPos here = mod.getPlayer().getBlockPos();
        if (mod.getWorld().getBiome(here).matchesKey(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", _biomeId)))) {
            if (DiscoveryLog.add("biome:" + _biomeId, here)) {
                setDebugState("Found " + _biomeId);
            }
            _found = true;
            return null;
        }
        if (_search.isActive() && _search.isFinished(mod)) {
            return new TimeoutWanderTask(WANDER_DISTANCE, true);
        }
        setDebugState("Searching for " + _biomeId);
        return _search;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _unknown || _found;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        if (_search != null) {
            _search.stop(mod);
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ExploreBiomeTask task && task._biomeId.equals(_biomeId);
    }

    @Override
    protected String toDebugString() {
        return "Exploring for biome " + _biomeId;
    }
}
