package dev.vibrato.protocolfixture.mixin;

import dev.vibrato.protocolfixture.TriggerKeepAlive;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class CommonFixtureMixin implements TriggerKeepAlive {
    @Shadow private long lastKeepAliveTime;
    @Shadow private boolean waitingForKeepAlive;
    @Override public void vibrato$triggerKeepAlive() {
        if (!waitingForKeepAlive) lastKeepAliveTime = 0;
    }
    @Unique private boolean vibrato$forcedKeepAlive;
    @Inject(method = "baseTick", at = @At("HEAD"))
    private void vibrato$promptKeepAlive(CallbackInfo ci) {
        if (!vibrato$forcedKeepAlive && (Object)this instanceof ServerConfigurationNetworkHandler
                && System.getProperty("vibrato.fixture.lifecycle", "normal").equals("config_common")) {
            vibrato$forcedKeepAlive = true;
            vibrato$triggerKeepAlive();
        }
    }
}
