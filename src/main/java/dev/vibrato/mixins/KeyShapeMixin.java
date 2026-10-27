package dev.vibrato.mixins;

import dev.vibrato.human.wire.HumanKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the player's freshly ticked input to {@link HumanKeys}. Injected right after {@code Input.tick}, which is
 * where Baritone's {@code PlayerMovementInput} writes its keys, so the shaper always sees the final intended keys.
 * The raised priority makes this run after any other injection at the same point.
 */
@Mixin(value = ClientPlayerEntity.class, priority = 1500)
public abstract class KeyShapeMixin {

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/input/Input;tick(ZF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void vibrato$shapeKeys(CallbackInfo ci) {
        HumanKeys.afterInputTick((ClientPlayerEntity) (Object) this);
    }
}
