package dev.vibrato.eventbus.events;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Hand;

public class BlockInteractEvent {
    public BlockHitResult hitResult;
    public ClientWorld world;
    public Hand hand;

    public BlockInteractEvent(BlockHitResult hitResult, ClientWorld world, Hand hand) {
        this.hitResult = hitResult;
        this.world = world;
        this.hand = hand;
    }
}
