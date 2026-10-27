package dev.vibrato.protocolfixture.mixin;

import dev.vibrato.protocolfixture.TriggerReconfiguration;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ReconfigurationFixtureMixin implements TriggerReconfiguration {
    @Shadow public abstract void reconfigure();
    @Unique private boolean vibrato$fixtureReconfiguration;
    @Override public void vibrato$fixtureReconfigure() {
        vibrato$fixtureReconfiguration = true;
        reconfigure();
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "onAcknowledgeReconfiguration", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;transitionInbound(Lnet/minecraft/network/NetworkState;Lnet/minecraft/network/listener/PacketListener;)V"))
    private void vibrato$startConfiguration(ClientConnection connection, NetworkState state, PacketListener listener) {
        connection.transitionInbound(state, listener);
        if (vibrato$fixtureReconfiguration && listener instanceof ServerConfigurationNetworkHandler configuration) {
            vibrato$fixtureReconfiguration = false;
            configuration.sendConfigurations();
        }
    }
}
