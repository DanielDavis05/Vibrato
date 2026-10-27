package dev.vibrato.eventbus;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventBusTest {
    private record Tick() {}
    private record Inner() {}

    @Test void changesDuringNestedPublicationDoNotInvalidateOuterSnapshot() {
        List<String> calls = new ArrayList<>();
        List<Subscription<?>> cleanup = new ArrayList<>();
        cleanup.add(EventBus.subscribe(Inner.class, ignored -> calls.add("inner")));
        Subscription<?>[] removed = new Subscription<?>[1];
        cleanup.add(EventBus.subscribe(Tick.class, ignored -> {
            calls.add("first");
            EventBus.publish(new Inner());
            EventBus.unsubscribe(removed[0]);
            cleanup.add(EventBus.subscribe(Tick.class, later -> calls.add("added")));
        }));
        removed[0] = EventBus.subscribe(Tick.class, ignored -> calls.add("removed"));
        cleanup.add(removed[0]);
        EventBus.publish(new Tick());
        assertEquals(List.of("first", "inner"), calls);
        EventBus.publish(new Tick());
        assertEquals(List.of("first", "inner", "first", "inner", "added"), calls);
        cleanup.forEach(EventBus::unsubscribe);
    }
}
