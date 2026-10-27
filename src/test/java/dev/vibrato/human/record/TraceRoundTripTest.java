package dev.vibrato.human.record;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TraceRoundTripTest {

    private static final TraceEvent.Header HEADER = new TraceEvent.Header(
            1, "human", "1.21.1", 2, 0.5, 144, "");

    private static String write(List<TraceEvent> events) throws IOException {
        StringWriter sink = new StringWriter();
        try (TraceWriter writer = new TraceWriter(sink, () -> 0)) {
            for (TraceEvent event : events) {
                writer.write(event);
            }
        }
        return sink.toString();
    }

    private static List<TraceEvent> read(String text) throws IOException {
        return TraceReader.read(new StringReader(text));
    }

    @Test
    void everyEventKindRoundTrips() throws IOException {
        List<TraceEvent> events = List.of(
                HEADER,
                new TraceEvent.Mouse(123456, 3.0, -1.0),
                new TraceEvent.Key(123460, "forward", true),
                new TraceEvent.Key(123480, "sprint", false),
                new TraceEvent.Tick(1042, 123500, 0.0, 64.0, 0.0, 0.0, 0.0, 0.0, 10.0, 5.0,
                        true, false, 1.5, 64.0, 0.5));

        assertEquals(events, read(write(events)));
    }

    @Test
    void absentNextFieldsStayNullAndAreNotWritten() throws IOException {
        TraceEvent.Tick idle = new TraceEvent.Tick(7, 100, 1.25, 64.0, -3.5, 0.1, 0.0, -0.2, -45.0, 12.5,
                false, true, null, null, null);

        String text = write(List.of(idle));

        assertFalse(text.contains("nextX"), text);
        assertFalse(text.contains("nextY"), text);
        assertFalse(text.contains("nextZ"), text);
        assertEquals(idle, read(text).get(0));
    }

    @Test
    void partialNextFieldsRoundTrip() throws IOException {
        TraceEvent.Tick partial = new TraceEvent.Tick(8, 116, 0.0, 64.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                true, false, null, 65.0, null);

        assertEquals(partial, read(write(List.of(partial))).get(0));
    }

    @Test
    void stringsWithQuotesBackslashesAndControlsRoundTrip() throws IOException {
        TraceEvent.Header tricky = new TraceEvent.Header(1, "bot", "1.21.1", 3, 0.25, 60,
                "say \"hi\" \\ back\nline\ttab \u0001 café");

        String text = write(List.of(tricky));

        assertEquals(1, text.split("\n", -1).length - 1, "one object per line");
        assertEquals(tricky, read(text).get(0));
    }

    @Test
    void doublesAreWrittenWithPointAndNoExponent() throws IOException {
        String text = write(List.of(new TraceEvent.Mouse(1, 3.0, -0.000001)));

        assertTrue(text.contains("\"dx\":3.0"), text);
        assertTrue(text.contains("\"dy\":-0.000001"), text);
    }

    @Test
    void nonFiniteValuesAreRejected() {
        TraceEvent bad = new TraceEvent.Mouse(1, Double.NaN, 0.0);
        assertThrows(IllegalArgumentException.class, () -> write(List.of(bad)));
    }

    @Test
    void flushesAtMostOncePerSecond() throws IOException {
        AtomicLong clock = new AtomicLong(0);
        int[] flushes = {0};
        Writer counting = new StringWriter() {
            @Override
            public void flush() {
                flushes[0]++;
            }
        };
        TraceWriter writer = new TraceWriter(counting, clock::get);

        clock.set(500);
        writer.write(HEADER);
        clock.set(999);
        writer.write(HEADER);
        assertEquals(0, flushes[0]);

        clock.set(1000);
        writer.write(HEADER);
        assertEquals(1, flushes[0]);

        clock.set(1500);
        writer.write(HEADER);
        assertEquals(1, flushes[0]);

        clock.set(2000);
        writer.write(HEADER);
        assertEquals(2, flushes[0]);
    }

    @Test
    void blankLinesAreSkipped() throws IOException {
        List<TraceEvent> events = new ArrayList<>();
        events.add(HEADER);
        events.add(new TraceEvent.Key(5, "use", true));

        String text = write(events).replace("\n", "\n\n");

        assertEquals(events, read(text));
    }
}
