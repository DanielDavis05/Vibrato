package dev.vibrato.protocolfixture.mixin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.network.packet.s2c.config.ResetChatS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationNetworkHandler.class)
public abstract class ConfigurationFixtureMixin {
    @Unique private int vibrato$delay;
    @Unique private boolean vibrato$configStarted;

    @Inject(method = "tick", at = @At("TAIL"))
    private void vibrato$resumeConfiguration(CallbackInfo ci) {
        if (vibrato$delay > 0 && --vibrato$delay == 0) {
            ((ServerConfigurationNetworkHandler)(Object)this).sendConfigurations();
        }
    }
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
        } else if (mode.equals("config_transfer")) {
            int port = Integer.getInteger("vibrato.fixture.transferPort", 25566);
            self.sendPacket(new ServerTransferS2CPacket("127.0.0.1", port));
            ci.cancel();
        } else if (mode.equals("config_pack")) {
            // No remote service: client can decline, or report a failed localhost download.
            self.sendPacket(new ResourcePackSendS2CPacket(UUID.fromString("99794d14-220a-4882-a609-38d4adf3b157"),
                    "http://127.0.0.1:1/fixture.zip", "", false, Optional.empty()));
        } else if (mode.equals("config_common") && !vibrato$configStarted) {
            vibrato$configStarted = true;
            vibrato$delay = 40;
            self.sendPacket(ResetChatS2CPacket.INSTANCE);
            self.sendPacket(new CommonPingS2CPacket(123));
            self.sendPacket(new StoreCookieS2CPacket(Identifier.of("vibrato", "fixture"), new byte[] {1}));
            self.sendPacket(new CustomReportDetailsS2CPacket(Map.of("fixture", "test")));
            self.sendPacket(new ServerLinksS2CPacket(List.of()));
            self.sendPacket(new ResourcePackRemoveS2CPacket(Optional.empty()));
            ci.cancel();
        }
    }
}
