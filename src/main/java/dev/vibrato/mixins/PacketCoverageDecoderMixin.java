package dev.vibrato.mixins;

import dev.vibrato.packetcoverage.PacketCoverage;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.packet.s2c.play.BundleDelimiterS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

@Mixin(DecoderHandler.class)
public abstract class PacketCoverageDecoderMixin {
    @Shadow @Final private NetworkState<?> state;
    // This point follows decoding and the complete-frame trailing-bytes check.
    @Redirect(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean vibrato$decoded(List<Object> output, Object value) {
        boolean added = output.add(value);
        if (added && state.side() == NetworkSide.CLIENTBOUND && value instanceof BundleDelimiterS2CPacket packet) {
            PacketCoverage.wireSuccess(PacketCoverage.capture(), state.id().name(), packet.getPacketId().side().name(), packet.getPacketId().id().toString());
        }
        return added;
    }
}
