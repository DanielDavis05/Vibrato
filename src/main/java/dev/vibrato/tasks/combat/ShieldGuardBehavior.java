package dev.vibrato.tasks.combat;

import dev.vibrato.Vibrato;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.PlayerSlot;
import baritone.api.utils.input.Input;
import net.minecraft.item.Items;

/**
 * Raises the off-hand shield while a threat is imminent and lowers it otherwise. The decision is pure
 * ({@link #classify}, {@link #shouldRaise}); {@link #update} only applies it to the input.
 */
public final class ShieldGuardBehavior {

    public enum Threat {
        NONE,
        ARROW_LINE,
        BLAST,
        CHARGE
    }

    private boolean _raised = false;

    /** Priority: a creeper blast beats a charge, which beats a skeleton lining up an arrow. */
    public static Threat classify(boolean arrowLineOfSight, boolean creeperBlastImminent, boolean chargeImminent) {
        if (creeperBlastImminent) return Threat.BLAST;
        if (chargeImminent) return Threat.CHARGE;
        if (arrowLineOfSight) return Threat.ARROW_LINE;
        return Threat.NONE;
    }

    /**
     * The shield only goes up when there is something to block, we have one, and neither eating nor
     * drawing a bow (both use the same right-click input).
     */
    public static boolean shouldRaise(boolean threatened, boolean hasShield, boolean eating, boolean shooting) {
        return threatened && hasShield && !eating && !shooting;
    }

    /** Applies the decision for this tick. Does nothing while eating, since the food chain owns right-click then. */
    public void update(Vibrato mod, boolean threatened, boolean shooting) {
        if (mod.getFoodChain().isTryingToEat()) {
            _raised = false;
            return;
        }
        boolean raise = shouldRaise(threatened, hasShieldInOffHand(), false, shooting);
        if (raise) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
        } else if (_raised) {
            mod.getInputControls().release(Input.CLICK_RIGHT);
        }
        _raised = raise;
    }

    public void lower(Vibrato mod) {
        if (_raised) {
            mod.getInputControls().release(Input.CLICK_RIGHT);
            _raised = false;
        }
    }

    public boolean isRaised() {
        return _raised;
    }

    public static boolean hasShieldInOffHand() {
        return StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() == Items.SHIELD;
    }
}
