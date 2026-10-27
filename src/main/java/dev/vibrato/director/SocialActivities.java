package dev.vibrato.director;

import dev.vibrato.Vibrato;
import dev.vibrato.advancement.AdvancementGoal;
import dev.vibrato.advancement.AdvancementGoals;
import dev.vibrato.advancement.AdvancementReportTask;
import dev.vibrato.advancement.PursueAdvancementTask;
import dev.vibrato.social.ChatTask;
import dev.vibrato.social.CommandPolicy;
import dev.vibrato.social.CommandTask;
import dev.vibrato.social.EmoteTask;
import dev.vibrato.social.FollowFriendTask;
import dev.vibrato.social.GreetNearbyPlayersTask;
import dev.vibrato.social.MessageBank;
import dev.vibrato.social.ReplyToWhisperTask;
import dev.vibrato.social.RequestHelpTask;
import dev.vibrato.social.ShareItemsTask;
import dev.vibrato.social.SocialTargets;
import dev.vibrato.tasksystem.Task;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Owned by workstream T5 (see PLAN.md). Register this domain's activities here. */
public final class SocialActivities {

    private static final List<String> ITEMS = List.of("apple", "baked_potato", "carrot", "cobblestone", "coal",
            "iron_ingot", "gold_ingot", "diamond", "emerald", "wheat");

    private static final List<String> TALK_CATEGORIES = List.of("greeting", "farewell", "small_talk", "sharing",
            "reacting_to_loot", "trade_offer");

    private SocialActivities() {}

    public static void register(ActivityRegistry registry) {
        registry.register(activity("greet_players", FeatureArea.SOCIAL, Set.of(), Set.of(Fact.of("social:greeted")),
                List.of(ParamSpec.ints("greetings", 1, 3)), v -> Duration.ofMinutes(3),
                (mod, v) -> new GreetNearbyPlayersTask(v.getInt("greetings"))));

        registry.register(activity("chat_small_talk", FeatureArea.CHAT, Set.of(), Set.of(Fact.of("social:chatted")),
                List.of(ParamSpec.choice("category", TALK_CATEGORIES.toArray(String[]::new))), v -> Duration.ofMinutes(1),
                (mod, v) -> new ChatTask(new MessageBank().render(v.getString("category"), new Random(),
                        Map.of("player", SocialTargets.nearestOtherPlayerName(mod, 32).orElse("everyone"),
                                "item", "spare wheat", "count", "2")))));

        registry.register(activity("ask_for_help", FeatureArea.CHAT, Set.of(), Set.of(Fact.of("social:asked_for_help")),
                List.of(ParamSpec.choice("item", ITEMS.toArray(String[]::new))), v -> Duration.ofMinutes(2),
                (mod, v) -> new RequestHelpTask(v.getString("item"))));

        registry.register(activity("share_items", FeatureArea.SOCIAL, Set.of(), Set.of(Fact.of("social:shared")),
                List.of(ParamSpec.choice("item", ITEMS.toArray(String[]::new)), ParamSpec.ints("count", 1, 16)),
                v -> Duration.ofMinutes(5),
                (mod, v) -> new ShareItemsTask(v.getString("item"), v.getInt("count"))));

        registry.register(activity("follow_friend", FeatureArea.SOCIAL, Set.of(), Set.of(Fact.of("social:followed")),
                List.of(ParamSpec.ints("seconds", 20, 120)), v -> Duration.ofSeconds(v.getInt("seconds") + 60),
                (mod, v) -> new FollowFriendTask(v.getInt("seconds"))));

        registry.register(activity("emote", FeatureArea.SOCIAL, Set.of(), Set.of(Fact.of("social:emoted")),
                List.of(ParamSpec.choice("kind", "jump", "sneak_spam", "spin")), v -> Duration.ofSeconds(30),
                (mod, v) -> new EmoteTask(EmoteTask.Emote.valueOf(v.getString("kind").toUpperCase()))));

        registry.register(activity("reply_whispers", FeatureArea.CHAT, Set.of(), Set.of(Fact.of("social:replied")),
                List.of(ParamSpec.ints("seconds", 30, 180)), v -> Duration.ofSeconds(v.getInt("seconds") + 30),
                (mod, v) -> new ReplyToWhisperTask(v.getInt("seconds"))));

        registry.register(activity("run_safe_command", FeatureArea.COMMAND, Set.of(), Set.of(Fact.of("command:ran")),
                List.of(ParamSpec.choice("command", CommandPolicy.SAFE_NO_ARGUMENT.toArray(String[]::new))),
                v -> Duration.ofMinutes(1), (mod, v) -> new CommandTask(v.getString("command"))));

        registry.register(activity("list_players", FeatureArea.COMMAND, Set.of(), Set.of(Fact.of("social:players_listed")),
                List.of(), v -> Duration.ofMinutes(1), (mod, v) -> new CommandTask("list")));

        registry.register(activity("check_progress", FeatureArea.ADVANCEMENT, Set.of(),
                Set.of(Fact.of("advancement:checked")), List.of(), v -> Duration.ofMinutes(1),
                (mod, v) -> new AdvancementReportTask()));

        for (AdvancementGoal goal : AdvancementGoals.ALL) {
            registry.register(activity("pursue_advancement_" + goal.path().replace('/', '_'), goal.area(), Set.of(),
                    Set.of(Fact.of("advancement:" + goal.id() + ":done")), List.of(), v -> Duration.ofMinutes(15),
                    (mod, v) -> new PursueAdvancementTask(goal)));
        }
    }

    private static Activity activity(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                                     List<ParamSpec> params, java.util.function.Function<ParamValues, Duration> budget,
                                     Factory factory) {
        return new SocialActivity(id, area, requires, provides, params, budget, factory);
    }

    @FunctionalInterface
    private interface Factory {
        Task create(Vibrato mod, ParamValues values);
    }

    private record SocialActivity(String id, FeatureArea area, Set<Fact> requires, Set<Fact> provides,
                                  List<ParamSpec> params, java.util.function.Function<ParamValues, Duration> budgetFor,
                                  Factory factory) implements Activity {

        @Override
        public Task create(Vibrato mod, ParamValues values) {
            return factory.create(mod, values);
        }

        @Override
        public Duration budget(ParamValues values) {
            return budgetFor.apply(values);
        }
    }
}
