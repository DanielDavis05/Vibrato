package dev.vibrato.eventbus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A global event bus for decoupled components. */
public class EventBus {
    private static final Map<Class<?>, List<Subscription<?>>> topics = new HashMap<>();

    /** Each publication uses a snapshot. New subscriptions take effect on the next (including nested) publication;
     * an unsubscribed listener is skipped even if it was already present in the snapshot. */
    public static <T> void publish(T event) {
        List<Subscription<?>> snapshot;
        synchronized (topics) {
            List<Subscription<?>> listeners = topics.get(event.getClass());
            if (listeners == null) return;
            snapshot = List.copyOf(listeners);
        }
        try {
            for (Subscription<?> raw : snapshot) {
                if (!raw.shouldDelete()) {
                    @SuppressWarnings("unchecked") Subscription<T> sub = (Subscription<T>) raw;
                    sub.accept(event);
                }
            }
        } finally {
            synchronized (topics) {
                List<Subscription<?>> listeners = topics.get(event.getClass());
                if (listeners != null) {
                    listeners.removeIf(Subscription::shouldDelete);
                    if (listeners.isEmpty()) topics.remove(event.getClass());
                }
            }
        }
    }

    public static <T> Subscription<T> subscribe(Class<T> type, Consumer<T> consumeEvent) {
        Subscription<T> sub = new Subscription<>(consumeEvent);
        synchronized (topics) {
            topics.computeIfAbsent(type, ignored -> new ArrayList<>()).add(sub);
        }
        return sub;
    }

    public static <T> void unsubscribe(Subscription<T> subscription) {
        if (subscription != null) subscription.delete();
    }
}
