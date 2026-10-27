package dev.vibrato.protocolfixture.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separate process modes: responses remain subject to vanilla's unexpected-response handling. */
@Mixin(ServerLoginNetworkHandler.class)
public abstract class LoginFixtureMixin {
    @Shadow @Final private ClientConnection connection;
    @Shadow private String profileName;
    @Shadow abstract void startVerify(GameProfile profile);
    @Unique private boolean vibrato$awaitingResponse;
    // Force the native encrypted handshake while telling the client that session authentication
    // is disabled. onKey still performs vanilla's nonce validation and installs both ciphers.
    @Redirect(method = "onHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isOnlineMode()Z"))
    private boolean vibrato$encryptOffline(MinecraftServer server) {
        return System.getProperty("vibrato.fixture.lifecycle", "normal").equals("login_encrypt") || server.isOnlineMode();
    }
    @ModifyArg(method = "onHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/login/LoginHelloS2CPacket;<init>(Ljava/lang/String;[B[BZ)V"), index = 3)
    private boolean vibrato$authenticationFlag(boolean original) {
        return !System.getProperty("vibrato.fixture.lifecycle", "normal").equals("login_encrypt") && original;
    }
    @Redirect(method = "onKey", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V"))
    private void vibrato$offlineProfile(Thread authentication) {
        if (System.getProperty("vibrato.fixture.lifecycle", "normal").equals("login_encrypt")) {
            startVerify(Uuids.getOfflinePlayerProfile(profileName));
        } else {
            authentication.start();
        }
    }
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
