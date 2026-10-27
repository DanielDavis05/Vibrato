package dev.vibrato.director;

/**
 * Single entry point that fills a registry with every built-in activity. Each domain class is owned by exactly one
 * workstream (see PLAN.md) so parallel work never edits the same file.
 */
public final class BuiltinActivities {

    private BuiltinActivities() {}

    public static ActivityRegistry createRegistry() {
        ActivityRegistry registry = new ActivityRegistry();
        registerAll(registry);
        return registry;
    }

    public static void registerAll(ActivityRegistry registry) {
        CoreActivities.register(registry);          // D : wraps existing get/goto/food/equip/deposit tasks
        TradeEnchantActivities.register(registry);  // T1: villager trading, enchanting, anvil, grindstone, brewing
        LifeActivities.register(registry);          // T2: sleep, farming, breeding, fishing, food, xp leveling
        TravelActivities.register(registry);        // T3: transport and exploration
        BuildActivities.register(registry);         // T4: building, chests, inventory organisation
        SocialActivities.register(registry);        // T5: chat, commands, bot-to-bot, advancements
        CombatActivities.register(registry);       // T6: mobs, combat, gear upgrades
    }
}
