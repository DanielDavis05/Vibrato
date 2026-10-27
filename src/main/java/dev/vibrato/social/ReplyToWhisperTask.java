package dev.vibrato.social;

import dev.vibrato.Vibrato;
import dev.vibrato.butler.ButlerConfig;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.eventbus.Subscription;
import dev.vibrato.eventbus.events.ChatMessageEvent;
import dev.vibrato.tasksystem.Task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;

/**
 * Listens for whispers for a while and answers the conversational ones from authorized players with a
 * {@link MessageBank} line. The Butler still receives and runs every whisper itself. This task only adds replies,
 * and skips anything the Butler would refuse or treat as a command.
 */
public class ReplyToWhisperTask extends Task {

    private static final int MAX_REPLIES = 3;

    private final int _seconds;
    private final Deque<WhisperTriage.Whisper> _queue = new ArrayDeque<>();
    private final MessageBank _bank = new MessageBank();
    private final Random _rng = new Random();

    private Subscription<ChatMessageEvent> _subscription;
    private String _ourName = "";
    private String _prefix = "@";
    private CommandTask _reply;
    private boolean _done;
    private int _ticks;
    private int _replies;

    public ReplyToWhisperTask(int seconds) {
        if (seconds < 1) {
            throw new IllegalArgumentException("seconds must be at least 1");
        }
        _seconds = seconds;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _queue.clear();
        _done = false;
        _ticks = 0;
        _replies = 0;
        _reply = null;
        _ourName = mod.getPlayer().getName().getString();
        _prefix = mod.getModSettings().getCommandPrefix();
        endListening();
        _subscription = EventBus.subscribe(ChatMessageEvent.class, evt -> onChat(evt.message.getString()));
    }

    private void onChat(String raw) {
        WhisperTriage.parse(_ourName, ButlerConfig.getInstance().whisperFormats, raw).ifPresent(whisper -> {
            if (WhisperTriage.isConversational(whisper.text(), _prefix,
                    name -> Vibrato.getCommandExecutor().get(name) != null)) {
                _queue.add(whisper);
            }
        });
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_done) {
            return null;
        }
        if (++_ticks >= _seconds * 20 || _replies >= MAX_REPLIES) {
            _done = true;
            endListening();
            return null;
        }
        if (_reply != null) {
            if (!_reply.isFinished(mod)) {
                return _reply;
            }
            _reply = null;
        }
        while (!_queue.isEmpty()) {
            WhisperTriage.Whisper whisper = _queue.poll();
            if (!mod.getButler().isUserAuthorized(whisper.from())) {
                continue;
            }
            String text = _bank.render(WhisperTriage.categoryFor(whisper.text()), _rng,
                    Map.of("player", whisper.from()));
            _replies++;
            _reply = new CommandTask("msg " + whisper.from() + " " + text);
            return _reply;
        }
        setDebugState("Listening for whispers");
        return null;
    }

    private void endListening() {
        EventBus.unsubscribe(_subscription);
        _subscription = null;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        endListening();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ReplyToWhisperTask task && task._seconds == _seconds;
    }

    @Override
    protected String toDebugString() {
        return "Replying to whispers for " + _seconds + "s";
    }
}
