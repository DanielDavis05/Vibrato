package dev.vibrato.tasks.slot;

import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.slots.Slot;

public class MoveItemToSlotFromContainerTask extends MoveItemToSlotTask {
    public MoveItemToSlotFromContainerTask(ItemTarget toMove, Slot destination) {
        super(toMove, destination, mod -> mod.getItemStorage().getSlotsWithItemContainer(toMove.getMatches()));
    }
}
