package dev.vibrato.social;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.Subscription;
import dev.vibrato.eventbus.events.ChatMessageEvent;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.ui.MessagePriority;

import java.util.Optional;
import java.util.Random;

/**
 * Issues one server slash command after a typing pause, then listens for the reply for three seconds. A line the
 * {@link CommandPolicy} refuses finishes at once as skipped, and is logged.
 */
public class CommandTask extends Task {

    private static final int REPLY_WINDOW_TICKS = 60;

    private final String _line;
    private final Random _rng = new Random();

    private String _command;
    private boolean _skipped;
    private boolean _sent;
    private int _typingTicks;
    private int _elapsedTicks;
    private int _windowTicks;
    private String _ownName = "";
    private String _reply;
    private Subscription<ChatMessageEvent> _replySubscription;

    public CommandTask(String line) {
        _line = line;
    }

    /** True when the policy refused the line. */
    public boolean wasSkipped() {
        return _skipped;
    }

    /** The first chat or system line that arrived after the command was sent, if any. */
    public Optional<String> lastReply() {
        return Optional.ofNullable(_reply);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _skipped = false;
        _sent = false;
        _reply = null;
        _windowTicks = 0;
        _elapsedTicks = 0;
        CommandPolicy.Decision decision = CommandPolicy.DEFAULT.check(_line);
        if (!decision.allowed()) {
            _skipped = true;
            Debug.logWarning("Skipped command \"" + _line + "\": " + decision.reason());
            return;
        }
        _command = _line.trim().replaceFirst("^/", "").trim();
        _typingTicks = TypingDelay.ticks(_command, _rng);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_skipped || (_sent && _windowTicks >= REPLY_WINDOW_TICKS)) {
            endReplyWindow();
            return null;
        }
        if (_sent) {
            ++_windowTicks;
            return null;
        }
        if (_elapsedTicks < _typingTicks) {
            ++_elapsedTicks;
            setDebugState("Typing command");
            return null;
        }
        if (!ChatRateLimiter.shared().tryAcquire(System.currentTimeMillis())) {
            setDebugState("Waiting for chat rate limit");
            return null;
        }
        _ownName = mod.getPlayer().getName().getString();
        mod.getMessageSender().enqueueChat("/" + _command, MessagePriority.TIMELY);
        _replySubscription = EventBus.subscribe(ChatMessageEvent.class, evt -> onChat(evt.message.getString()));
        _sent = true;
        return null;
    }

    private void onChat(String text) {
        if (_reply != null || text.isBlank() || text.startsWith("<" + _ownName + ">") || text.startsWith("You ")) {
            return;
        }
        _reply = text;
    }

    private void endReplyWindow() {
        if (_replySubscription != null) {
            EventBus.unsubscribe(_replySubscription);
            _replySubscription = null;
            if (_reply != null) {
                Debug.logMessage("Reply to \"" + _command + "\": " + _reply);
            }
        }
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _skipped || (_sent && _windowTicks >= REPLY_WINDOW_TICKS);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        endReplyWindow();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CommandTask task && task._line.equals(_line);
    }

    @Override
    protected String toDebugString() {
        return "Running command \"" + _line + "\"";
    }
}
