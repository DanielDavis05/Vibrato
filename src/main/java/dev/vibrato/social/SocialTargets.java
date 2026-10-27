package dev.vibrato.social;

import dev.vibrato.Vibrato;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/** Finds other players and bots near us, which is all the social tasks need to pick a target. */
public final class SocialTargets {

    private SocialTargets() {}

    public static Optional<PlayerEntity> nearestOtherPlayer(Vibrato mod, double maxDistance, Set<String> exclude) {
        PlayerEntity self = mod.getPlayer();
        return mod.getWorld().getPlayers().stream()
                .filter(player -> player != self)
                .filter(player -> !exclude.contains(player.getName().getString()))
                .filter(player -> player.distanceTo(self) <= maxDistance)
                .min(Comparator.comparingDouble(player -> player.squaredDistanceTo(self)))
                .map(player -> (PlayerEntity) player);
    }

    public static Optional<String> nearestOtherPlayerName(Vibrato mod, double maxDistance) {
        return nearestOtherPlayer(mod, maxDistance, Set.of()).map(player -> player.getName().getString());
    }
}
