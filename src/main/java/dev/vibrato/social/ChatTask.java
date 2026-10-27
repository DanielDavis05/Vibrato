package dev.vibrato.social;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.ui.MessagePriority;

import java.util.Random;

/**
 * Sends one chat line after a human-like typing pause, respecting {@link ChatRateLimiter}. Commands are rejected
 * here, because they belong to {@link CommandTask}.
 */
public class ChatTask extends Task {

    private final String _text;
    private final Random _rng = new Random();

    private int _typingTicks;
    private int _elapsedTicks;
    private boolean _sent;

    public ChatTask(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Chat text must not be blank");
        }
        if (text.startsWith("/") || text.startsWith("@")) {
            throw new IllegalArgumentException("Chat text must not start with '/' or '@': " + text);
        }
        _text = text;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _typingTicks = TypingDelay.ticks(_text, _rng);
        _elapsedTicks = 0;
        _sent = false;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_sent) {
            return null;
        }
        if (_elapsedTicks < _typingTicks) {
            ++_elapsedTicks;
            setDebugState("Typing");
            return null;
        }
        if (!ChatRateLimiter.shared().tryAcquire(System.currentTimeMillis())) {
            setDebugState("Waiting for chat rate limit");
            return null;
        }
        mod.getMessageSender().enqueueChat(_text, MessagePriority.TIMELY);
        _sent = true;
        return null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _sent;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ChatTask task && task._text.equals(_text);
    }

    @Override
    protected String toDebugString() {
        return "Chatting \"" + _text + "\"";
    }
}
