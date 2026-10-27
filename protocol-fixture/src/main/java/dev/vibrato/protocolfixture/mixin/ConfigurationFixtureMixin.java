package dev.vibrato.protocolfixture.mixin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationNetworkHandler.class)
public abstract class ConfigurationFixtureMixin {
    @Inject(method = "sendConfigurations", at = @At("HEAD"), cancellable = true)
    private void vibrato$configScenarios(CallbackInfo ci) {
        ServerCommonNetworkHandler self = (ServerCommonNetworkHandler)(Object)this;
        String mode = System.getProperty("vibrato.fixture.lifecycle", "normal");
        if (mode.equals("config_disconnect")) {
            self.disconnect(Text.literal("Fixture configuration disconnect"));
            ci.cancel();
        } else if (mode.equals("config_cookie")) {
            self.sendPacket(new CookieRequestS2CPacket(Identifier.of("vibrato", "fixture")));
            ci.cancel();
        } else if (mode.equals("config_common")) {
            self.sendPacket(new CommonPingS2CPacket(123));
            self.sendPacket(new StoreCookieS2CPacket(Identifier.of("vibrato", "fixture"), new byte[] {1}));
            self.sendPacket(new CustomReportDetailsS2CPacket(Map.of("fixture", "test")));
            self.sendPacket(new ServerLinksS2CPacket(List.of()));
            self.sendPacket(new ResourcePackRemoveS2CPacket(Optional.empty()));
        }
    }
}
