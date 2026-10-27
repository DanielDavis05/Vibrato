package dev.vibrato.protocolfixture.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.network.packet.s2c.login.UnknownLoginQueryRequestPayload;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separate process modes: responses remain subject to vanilla's unexpected-response handling. */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class LoginFixtureMixin {
    @Shadow @Final private ClientConnection connection;
    @Unique private boolean vibrato$awaitingResponse;
    @Inject(method = "onHello", at = @At("RETURN"))
    private void vibrato$afterHello(LoginHelloC2SPacket packet, CallbackInfo ci) {
        String mode = System.getProperty("vibrato.fixture.lifecycle", "normal");
        if (mode.equals("login_query")) {
            vibrato$awaitingResponse = true;
            connection.send(new LoginQueryRequestS2CPacket(123, new UnknownLoginQueryRequestPayload(Identifier.of("vibrato", "fixture"))));
        } else if (mode.equals("login_cookie")) {
            vibrato$awaitingResponse = true;
            connection.send(new CookieRequestS2CPacket(Identifier.of("vibrato", "fixture")));
        } else if (mode.equals("login_disconnect")) {
            ((ServerLoginNetworkHandler)(Object)this).disconnect(Text.literal("Fixture login disconnect"));
        }
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vibrato$waitForResponse(CallbackInfo ci) {
        if (vibrato$awaitingResponse) ci.cancel();
    }
}
