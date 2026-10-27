package dev.vibrato.mixins;

import dev.vibrato.packetcoverage.PacketCoverage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class PacketCoverageConnectionMixin {
    @Shadow private NetworkSide side;
    @Unique private volatile NetworkPhase vibrato$outboundPhase = NetworkPhase.HANDSHAKING;

    @Inject(method = "transitionOutbound", at = @At("HEAD"))
    private void vibrato$outboundTransition(NetworkState<?> state, CallbackInfo ci) {
        vibrato$outboundPhase = state.id();
    }

    @Redirect(method = "handlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static <T extends PacketListener> void vibrato$handled(Packet<T> packet, T listener) {
        var context = packet.getPacketId().side() == NetworkSide.CLIENTBOUND && !(packet instanceof BundleS2CPacket) ? PacketCoverage.capture() : null;
        String phase = listener.getPhase().name();
        packet.apply(listener);
        PacketCoverage.success(context, phase, packet.getPacketId().side().name(), packet.getPacketId().id().toString());
    }

    @Redirect(method = "sendInternal", at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture vibrato$writeAndFlush(Channel channel, Object packet) {
        String phase = vibrato$writePhase(channel);
        var context = side == NetworkSide.CLIENTBOUND ? PacketCoverage.capture() : null;
        return vibrato$observeWrite(channel.writeAndFlush(packet), packet, phase, context);
    }
    @Redirect(method = "sendInternal", at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;write(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture vibrato$write(Channel channel, Object packet) {
        String phase = vibrato$writePhase(channel);
        var context = side == NetworkSide.CLIENTBOUND ? PacketCoverage.capture() : null;
        return vibrato$observeWrite(channel.write(packet), packet, phase, context);
    }
    @Unique private String vibrato$writePhase(Channel channel) {
        EncoderHandler<?> encoder = channel.pipeline().get(EncoderHandler.class);
        return encoder == null ? vibrato$outboundPhase.name() : ((PacketCoverageEncoderAccessor) encoder).vibrato$getState().id().name();
    }
    @Unique private ChannelFuture vibrato$observeWrite(ChannelFuture future, Object value, String phase, PacketCoverage.Context context) {
        if (context != null && value instanceof Packet<?> packet) {
            String direction = packet.getPacketId().side().name();
            String id = packet.getPacketId().id().toString();
            future.addListener(f -> { if (f.isSuccess()) PacketCoverage.success(context, phase, direction, id); });
        }
        return future;
    }
}
