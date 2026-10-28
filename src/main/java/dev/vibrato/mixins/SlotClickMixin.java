package dev.vibrato.mixins;

import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.events.SlotClickChangedEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Mixin(ScreenHandler.class)
public class SlotClickMixin {
    @Unique private final Deque<List<ItemStack>> vibrato$beforeClicks = new ArrayDeque<>();

    // onSlotClick is the normal entry point. internalOnSlotClick can call itself
    // recursively for special operations, so intercepting that recursive call
    // alone misses ordinary pickup and deposit clicks.
    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void vibrato$beforeClick(int slotIndex, int button, SlotActionType actionType,
                                     PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        List<ItemStack> before = new ArrayList<>(handler.slots.size());
        for (Slot slot : handler.slots) before.add(slot.getStack().copy());
        vibrato$beforeClicks.push(before);
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void vibrato$afterClick(int slotIndex, int button, SlotActionType actionType,
                                    PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        List<ItemStack> before = vibrato$beforeClicks.pop();
        if (player != MinecraftClient.getInstance().player || player.currentScreenHandler != handler) return;
        for (int i = 0; i < before.size() && i < handler.slots.size(); i++) {
            ItemStack after = handler.slots.get(i).getStack();
            if (!ItemStack.areEqual(before.get(i), after)) {
                dev.vibrato.util.slots.Slot slot = dev.vibrato.util.slots.Slot.getFromCurrentScreen(i);
                EventBus.publish(new SlotClickChangedEvent(slot, before.get(i), after.copy()));
            }
        }
    }
}
