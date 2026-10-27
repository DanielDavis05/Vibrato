package dev.vibrato.fixture;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.util.Identifier;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Controlled fixture for an isolated loopback test server. Expected IDs are targets, not evidence. */
public final class ProtocolFixture implements DedicatedServerModInitializer {
    public record Scenario(List<String> expectedIds, Consumer<ServerPlayerEntity> action) {}
    public static final Map<String, Scenario> SCENARIOS = new LinkedHashMap<>();
    static {
        scenario("ui", List.of("system_chat", "set_title_text", "set_subtitle_text", "set_action_bar_text", "set_titles_animation", "clear_titles", "custom_chat_completions", "tab_list"), ProtocolFixture::ui);
        scenario("common", List.of("ping", "store_cookie", "cookie_request", "resource_pack_pop", "custom_report_details"), ProtocolFixture::common);
        scenario("scoreboard", List.of("set_objective", "set_display_objective", "set_score", "reset_score", "set_player_team", "boss_event"), p -> commands(p,
                "scoreboard objectives remove vprotocol", "scoreboard objectives add vprotocol dummy", "scoreboard objectives setdisplay sidebar vprotocol", "scoreboard players set @s vprotocol 5", "scoreboard players reset @s vprotocol", "team remove vprotocol", "team add vprotocol", "team join vprotocol @s", "team modify vprotocol color blue", "team leave @s", "team remove vprotocol", "bossbar remove vibrato:fixture", "bossbar add vibrato:fixture Fixture", "bossbar set vibrato:fixture players @s", "bossbar set vibrato:fixture value 60", "bossbar remove vibrato:fixture", "scoreboard objectives remove vprotocol"));
        scenario("world", List.of("set_time", "game_event", "change_difficulty", "initialize_border", "set_border_center", "set_border_size", "set_border_lerp_size", "set_border_warning_delay", "set_border_warning_distance", "level_particles", "sound", "stop_sound", "set_default_spawn_position"), ProtocolFixture::world);
        scenario("entities", List.of("add_entity", "add_experience_orb", "set_entity_data", "set_entity_motion", "set_equipment", "update_mob_effect", "update_attributes", "set_passengers", "remove_entities"), p -> commands(p,
                "kill @e[tag=vprotocol_fixture]", "summon armor_stand ~2 ~ ~ {Tags:[\"vprotocol_fixture\"],CustomName:'\"Fixture\"',CustomNameVisible:1b}", "summon pig ~3 ~ ~ {Tags:[\"vprotocol_fixture\"]}", "summon experience_orb ~1 ~1 ~ {Value:3s}", "effect give @e[type=pig,tag=vprotocol_fixture,limit=1,sort=nearest] minecraft:speed 20 0 true", "item replace entity @e[type=armor_stand,tag=vprotocol_fixture,limit=1,sort=nearest] armor.head with minecraft:iron_helmet"));
        scenario("cleanup", List.of("remove_entities"), p -> commands(p, "kill @e[tag=vprotocol_fixture]"));
        scenario("blocks", List.of("block_update", "block_entity_data", "section_blocks_update"), p -> commands(p,
                "setblock ~2 ~ ~ minecraft:command_block", "setblock ~3 ~ ~ minecraft:structure_block", "setblock ~4 ~ ~ minecraft:jigsaw", "setblock ~2 ~ ~2 minecraft:beacon", "setblock ~3 ~ ~2 minecraft:crafter", "setblock ~4 ~ ~2 minecraft:chest", "setblock ~2 ~ ~4 minecraft:oak_sign", "setblock ~3 ~ ~4 minecraft:lectern", "setblock ~4 ~ ~4 minecraft:crafting_table"));
        scenario("inventory", List.of("container_set_content", "container_set_slot", "set_experience", "set_health", "update_attributes"), p -> commands(p,
                "give @s minecraft:book 1", "give @s minecraft:map 1", "give @s minecraft:filled_map 1", "experience add @s 1 points", "effect give @s minecraft:speed 5 0 true", "effect clear @s minecraft:speed"));
    }
    private static void scenario(String name, List<String> ids, Consumer<ServerPlayerEntity> action) {
        SCENARIOS.put(name, new Scenario(ids.stream().map(id -> "minecraft:" + id).toList(), action));
    }
    @Override public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (!(server instanceof MinecraftDedicatedServer dedicated)) throw new IllegalStateException("Fixture requires a dedicated server");
            var settings = dedicated.getProperties();
            if (!"127.0.0.1".equals(settings.serverIp) || settings.serverPort != 25576 || settings.onlineMode
                    || settings.enableQuery || settings.enableRcon) {
                throw new IllegalStateException("Fixture requires server-ip=127.0.0.1, server-port=25576, online-mode=false, enable-query=false and enable-rcon=false");
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(literal("vprotocol")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("list").executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(SCENARIOS.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().expectedIds()).toList().toString()), false);
                    return SCENARIOS.size();
                }))
                .then(literal("run").then(argument("scenario", StringArgumentType.word())
                        .suggests((ctx, builder) -> { SCENARIOS.keySet().forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "scenario");
                            Scenario scenario = SCENARIOS.get(name);
                            if (scenario == null) { ctx.getSource().sendError(Text.literal("Unknown fixture scenario: " + name)); return 0; }
                            scenario.action().accept(ctx.getSource().getPlayerOrThrow());
                            ctx.getSource().sendFeedback(() -> Text.literal("Fixture " + name + " triggered; targets=" + scenario.expectedIds()), false);
                            return 1;
                        })))));
    }
    private static void commands(ServerPlayerEntity player, String... commands) {
        ServerCommandSource source = player.getCommandSource().withLevel(4).withSilent();
        for (String command : commands) player.getServer().getCommandManager().executeWithPrefix(source, command);
    }
    private static void common(ServerPlayerEntity p) {
        var n = p.networkHandler;
        var cookie = Identifier.of("vibrato", "fixture");
        n.sendPacket(new CommonPingS2CPacket(767));
        n.sendPacket(new StoreCookieS2CPacket(cookie, new byte[] {1, 2, 3}));
        n.sendPacket(new CookieRequestS2CPacket(cookie));
        n.sendPacket(new ResourcePackRemoveS2CPacket(Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000767"))));
        n.sendPacket(new CustomReportDetailsS2CPacket(Map.of("Fixture", "Local protocol test")));
    }
    private static void ui(ServerPlayerEntity p) {
        var n = p.networkHandler;
        n.sendPacket(new GameMessageS2CPacket(Text.literal("Vibrato protocol fixture"), false));
        n.sendPacket(new TitleFadeS2CPacket(5, 20, 5));
        n.sendPacket(new TitleS2CPacket(Text.literal("Fixture")));
        n.sendPacket(new SubtitleS2CPacket(Text.literal("Protocol 767")));
        n.sendPacket(new OverlayMessageS2CPacket(Text.literal("Fixture action bar")));
        n.sendPacket(new PlayerListHeaderS2CPacket(Text.literal("Fixture"), Text.literal("Local test")));
        n.sendPacket(new ChatSuggestionsS2CPacket(ChatSuggestionsS2CPacket.Action.ADD, List.of("fixture")));
        n.sendPacket(new ChatSuggestionsS2CPacket(ChatSuggestionsS2CPacket.Action.REMOVE, List.of("fixture")));
        n.sendPacket(new ClearTitleS2CPacket(false));
    }
    private static void world(ServerPlayerEntity p) {
        var n = p.networkHandler;
        var border = p.getServerWorld().getWorldBorder();
        n.sendPacket(new WorldTimeUpdateS2CPacket(p.getServerWorld().getTime(), p.getServerWorld().getTimeOfDay(), true));
        n.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, 0));
        n.sendPacket(new DifficultyS2CPacket(p.getServerWorld().getDifficulty(), false));
        n.sendPacket(new WorldBorderInitializeS2CPacket(border));
        n.sendPacket(new WorldBorderCenterChangedS2CPacket(border));
        n.sendPacket(new WorldBorderSizeChangedS2CPacket(border));
        n.sendPacket(new WorldBorderInterpolateSizeS2CPacket(border));
        n.sendPacket(new WorldBorderWarningBlocksChangedS2CPacket(border));
        n.sendPacket(new WorldBorderWarningTimeChangedS2CPacket(border));
        commands(p, "particle minecraft:happy_villager ~ ~1 ~ 0.2 0.2 0.2 0 3 force @s", "playsound minecraft:entity.experience_orb.pickup master @s ~ ~ ~ 1 1", "stopsound @s master minecraft:entity.experience_orb.pickup", "spawnpoint @s ~ ~ ~");
    }
}
