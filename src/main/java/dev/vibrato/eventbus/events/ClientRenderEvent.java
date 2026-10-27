package adris.altoclef.eventbus.events;

import net.minecraft.client.gui.DrawContext;

public class ClientRenderEvent {
    public DrawContext context;
    public float tickDelta;

    public ClientRenderEvent(DrawContext context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
    }
}
