package dev.vibrato.mixins;

import net.minecraft.network.NetworkState;
import net.minecraft.network.handler.EncoderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EncoderHandler.class)
public interface PacketCoverageEncoderAccessor {
    @Accessor("state") NetworkState<?> vibrato$getState();
}
