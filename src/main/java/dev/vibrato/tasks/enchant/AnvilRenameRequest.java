package dev.vibrato.tasks.enchant;

import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Same ordering as the vanilla anvil name field: update local state, then notify the server once. */
final class AnvilRenameRequest {
    private AnvilRenameRequest() {}

    static boolean apply(String name, Predicate<String> updateLocal, Consumer<RenameItemC2SPacket> send) {
        if (!updateLocal.test(name)) return false;
        send.accept(new RenameItemC2SPacket(name));
        return true;
    }
}
