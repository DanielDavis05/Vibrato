package dev.vibrato.mixins;

import dev.vibrato.packetcoverage.PacketCoverage;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetworkThreadUtils.class)
public abstract class PacketCoverageThreadMixin {
    @Redirect(method = "method_11072", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static <T extends PacketListener> void vibrato$handledOnMainThread(Packet<T> packet, T listener) {
        var context = packet.getPacketId().side() == NetworkSide.CLIENTBOUND && !(packet instanceof BundleS2CPacket) ? PacketCoverage.capture() : null;
        String phase = listener.getPhase().name();
        packet.apply(listener);
        PacketCoverage.success(context, phase, packet.getPacketId().side().name(), packet.getPacketId().id().toString());
    }
}
