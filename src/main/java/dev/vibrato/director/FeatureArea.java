package dev.vibrato.director;

/**
 * Coarse categories of Minecraft gameplay. The planner tracks how often each one has been exercised per bot and
 * boosts under-used areas so that every part of the game is eventually hit.
 */
public enum FeatureArea {
    GATHER,
    CRAFT,
    SMELT,
    BUILD,
    EXPLORE,
    SLEEP,
    TRADE,
    ENCHANT,
    GEAR,
    LEVEL,
    ADVANCEMENT,
    COMBAT,
    TRANSPORT,
    INVENTORY,
    CHEST,
    FARM,
    SOCIAL,
    COMMAND,
    CHAT
}
