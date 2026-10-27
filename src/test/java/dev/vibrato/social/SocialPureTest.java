package dev.vibrato.social;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SocialPureTest {

    @Test
    void typingDelayIsBoundedPerCharacterAndCapped() {
        Random rng = new Random(1);
        assertEquals(0, TypingDelay.millis("", rng));
        long oneChar = TypingDelay.millis("a", new Random(2));
        assertTrue(oneChar >= TypingDelay.MIN_MS_PER_CHAR && oneChar <= TypingDelay.MAX_MS_PER_CHAR);
        assertEquals(TypingDelay.CAP_MS, TypingDelay.millis("x".repeat(500), rng));
        assertTrue(TypingDelay.ticks("hello", new Random(3)) >= 5 * TypingDelay.MIN_MS_PER_CHAR / 50);
    }

    @Test
    void rateLimiterAllowsFourPerMinuteWithSixSecondGap() {
        ChatRateLimiter limiter = new ChatRateLimiter(4, 6000);
        assertTrue(limiter.tryAcquire(0));
        assertFalse(limiter.tryAcquire(5999), "min gap must hold");
        assertTrue(limiter.tryAcquire(6000));
        assertTrue(limiter.tryAcquire(12000));
        assertTrue(limiter.tryAcquire(18000));
        assertFalse(limiter.tryAcquire(30000), "fifth send inside the minute is refused");
        assertEquals(60000, limiter.nextAllowedAt(30000));
        assertTrue(limiter.tryAcquire(60000), "window slid past the first send");
    }

    @Test
    void rateLimiterRejectsBadConfig() {
        assertThrows(IllegalArgumentException.class, () -> new ChatRateLimiter(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChatRateLimiter(1, -1));
    }

    @Test
    void policyAllowsSafeCommands() {
        CommandPolicy policy = CommandPolicy.DEFAULT;
        assertTrue(policy.check("list").allowed());
        assertTrue(policy.check("/list").allowed());
        assertTrue(policy.check("seed").allowed());
        assertTrue(policy.check("help").allowed());
        assertTrue(policy.check("help trade").allowed());
        assertTrue(policy.check("msg Steve hello there").allowed());
        assertTrue(policy.check("w Steve_1 hi").allowed());
        assertTrue(policy.check("tell Steve hi").allowed());
        assertTrue(policy.check("me waves").allowed());
    }

    @Test
    void policyDeniesKillAndAdminAndUnknown() {
        CommandPolicy policy = CommandPolicy.DEFAULT;
        assertFalse(policy.check("kill").allowed());
        assertFalse(policy.check("kill Steve").allowed());
        assertFalse(policy.check("op Steve").allowed());
        assertFalse(policy.check("stop").allowed());
        assertFalse(policy.check("gamemode creative").allowed());
        assertFalse(policy.check("unknowncmd").allowed());
        assertEquals("'kill' is never allowed", policy.check("kill").reason());
    }

    @Test
    void policyDisablesTriggerByDefault() {
        assertFalse(CommandPolicy.DEFAULT.check("trigger foo").allowed());
        assertTrue(new CommandPolicy(true).check("trigger foo").allowed());
        assertFalse(CommandPolicy.DEFAULT.allowedNames().contains("trigger"));
    }

    @Test
    void policyValidatesArguments() {
        CommandPolicy policy = CommandPolicy.DEFAULT;
        assertFalse(policy.check("list everyone").allowed());
        assertFalse(policy.check("seed 1").allowed());
        assertFalse(policy.check("msg @a hi").allowed(), "selectors are not player names");
        assertFalse(policy.check("msg ab hi").allowed(), "names need 3 to 16 characters");
        assertFalse(policy.check("msg Steve").allowed(), "message text is required");
        assertFalse(policy.check("me").allowed());
        assertFalse(policy.check("help a b").allowed());
        assertFalse(policy.check("help ../etc").allowed());
        assertFalse(policy.check("me " + "x".repeat(300)).allowed());
    }

    @Test
    void policyRefusesChainingAndControlCharacters() {
        CommandPolicy policy = CommandPolicy.DEFAULT;
        assertFalse(policy.check("list; kill Steve").allowed());
        assertFalse(policy.check("me hi\nop Steve").allowed());
        assertFalse(policy.check("").allowed());
        assertFalse(policy.check(null).allowed());
    }

    @Test
    void safeNoArgumentListIsAllowedByPolicy() {
        for (String name : CommandPolicy.SAFE_NO_ARGUMENT) {
            assertTrue(CommandPolicy.DEFAULT.check(name).allowed(), name);
        }
    }

    @Test
    void messageBankFillsPlaceholdersAndNeverRepeatsBackToBack() {
        MessageBank bank = new MessageBank();
        Random rng = new Random(42);
        Map<String, String> ctx = Map.of("player", "Alex", "item", "iron ingot", "count", "3");
        String previous = null;
        for (int i = 0; i < 200; ++i) {
            String line = bank.render("asking_for_help", rng, ctx);
            assertFalse(line.contains("{"), line);
            assertNotEquals(previous, line);
            previous = line;
        }
    }

    @Test
    void messageBankIsDeterministicPerSeed() {
        Map<String, String> ctx = Map.of("player", "Alex", "item", "bread", "count", "2");
        List<String> a = renderSeq(new Random(7), ctx);
        List<String> b = renderSeq(new Random(7), ctx);
        assertEquals(a, b);
    }

    @Test
    void messageBankRejectsMissingPlaceholderAndUnknownCategory() {
        MessageBank bank = new MessageBank();
        assertThrows(IllegalArgumentException.class, () -> bank.render("sharing", new Random(1), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> bank.render("nope", new Random(1), Map.of()));
    }

    @Test
    void everyTemplateOnlyUsesKnownPlaceholders() {
        for (String category : MessageBank.CATEGORIES) {
            for (String template : MessageBank.templatesOf(category)) {
                for (String name : MessageBank.placeholdersOf(template)) {
                    assertTrue(List.of("player", "item", "count").contains(name), template);
                }
            }
        }
    }

    private static List<String> renderSeq(Random rng, Map<String, String> ctx) {
        MessageBank bank = new MessageBank();
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < 20; ++i) {
            out.add(bank.render("trade_offer", rng, ctx));
        }
        return out;
    }

    @Test
    void whisperTriageSeparatesConversationFromCommands() {
        assertTrue(WhisperTriage.isConversational("how are you", "@", w -> false));
        assertFalse(WhisperTriage.isConversational("@get iron 1", "@", w -> false));
        assertFalse(WhisperTriage.isConversational("get iron 1", "@", w -> w.equals("get")));
        assertFalse(WhisperTriage.isConversational("   ", "@", w -> false));
        assertFalse(WhisperTriage.isConversational(null, "@", w -> false));
        assertEquals("greeting", WhisperTriage.categoryFor("Hey there"));
        assertEquals("small_talk", WhisperTriage.categoryFor("what are you building"));
    }
}
