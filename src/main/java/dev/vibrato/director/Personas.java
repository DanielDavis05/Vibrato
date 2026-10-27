package dev.vibrato.director;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

import static dev.vibrato.director.FeatureArea.*;

/** Built-in personas. Specialists boost a few areas and damp a few, so bots with different seeds also diverge. */
public final class Personas {

    public static final Persona GENERALIST = Persona.GENERALIST;

    public static final Persona BUILDER = new Persona("builder", Map.of(
            BUILD, 3.0, CHEST, 2.0, INVENTORY, 1.5, TRANSPORT, 0.6, COMBAT, 0.5));

    public static final Persona MINER = new Persona("miner", Map.of(
            GATHER, 3.0, SMELT, 2.0, CRAFT, 1.5, EXPLORE, 1.5, FARM, 0.5, SOCIAL, 0.4));

    public static final Persona EXPLORER = new Persona("explorer", Map.of(
            EXPLORE, 3.0, TRANSPORT, 2.5, ADVANCEMENT, 1.5, SLEEP, 0.6, CHEST, 0.5));

    public static final Persona TRADER = new Persona("trader", Map.of(
            TRADE, 3.0, ENCHANT, 2.0, SOCIAL, 1.5, COMBAT, 0.6, FARM, 0.7));

    public static final Persona FIGHTER = new Persona("fighter", Map.of(
            COMBAT, 3.0, GEAR, 2.0, ENCHANT, 1.5, FARM, 0.6, CHAT, 0.5));

    public static final Persona FARMER = new Persona("farmer", Map.of(
            FARM, 3.0, CHEST, 1.5, INVENTORY, 1.5, COMBAT, 0.5, EXPLORE, 0.6));

    public static final Persona SOCIALITE = new Persona("socialite", Map.of(
            SOCIAL, 3.0, CHAT, 2.5, COMMAND, 1.5, ADVANCEMENT, 1.5, GATHER, 0.5));

    private static final List<Persona> ALL = List.of(
            GENERALIST, BUILDER, MINER, EXPLORER, TRADER, FIGHTER, FARMER, SOCIALITE);

    private Personas() {}

    /** Deterministic pick over all personas, generalist included. */
    public static Persona forSeed(long seed) {
        return ALL.get(new SplittableRandom(seed).nextInt(ALL.size()));
    }

    /** Case-insensitive lookup; null if unknown. */
    public static Persona byName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Persona persona : ALL) {
            if (persona.name().equals(wanted)) {
                return persona;
            }
        }
        return null;
    }

    public static List<Persona> all() {
        return ALL;
    }
}
