package dev.vibrato.advancement;

import dev.vibrato.mixins.ClientAdvancementManagerAccessor;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/** Reads the ids of completed advancements from the client's advancement manager. */
public final class ClientAdvancements {

    private ClientAdvancements() {}

    public static List<Identifier> doneIds() {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            return List.of();
        }
        Map<AdvancementEntry, AdvancementProgress> progresses =
                ((ClientAdvancementManagerAccessor) handler.getAdvancementHandler()).getAdvancementProgresses();
        return progresses.entrySet().stream()
                .filter(entry -> entry.getValue().isDone())
                .map(entry -> entry.getKey().id())
                .toList();
    }
}
