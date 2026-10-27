package dev.vibrato.mixins;

import dev.vibrato.packetcoverage.PacketCoverage;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bundles are assembled before ClientConnection sees them; count their handled contents. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PacketCoverageBundleMixin {
    @Redirect(method = "onBundle", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private <T extends PacketListener> void vibrato$handledBundlePacket(Packet<T> packet, T listener) {
        var context = PacketCoverage.capture();
        packet.apply(listener);
        PacketCoverage.success(context, "PLAY", packet.getPacketId().side().name(), packet.getPacketId().id().toString());
    }
}
