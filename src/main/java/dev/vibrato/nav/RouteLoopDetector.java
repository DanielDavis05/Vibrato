package dev.vibrato.nav;

import java.util.HashSet;
import java.util.Set;

/** Detects sustained repetition of the same sequence of block transitions. */
final class RouteLoopDetector {
    private static final int MIN_PERIOD = 3;
    private static final int MAX_PERIOD = 12;
    private static final int REPETITIONS = 3;
    private static final long MIN_DURATION_TICKS = 8 * 20;
    private static final int CAPACITY = MAX_PERIOD * REPETITIONS;

    private record Cell(int x, int y, int z) {}

    private final Cell[] _cells = new Cell[CAPACITY];
    private final long[] _ticks = new long[CAPACITY];
    private int _next;
    private int _size;
    private Cell _last;
    private long _repeatingSince = -1;

    boolean update(MotionSample sample) {
        Cell cell = new Cell((int) Math.floor(sample.x()), (int) Math.floor(sample.y()),
                (int) Math.floor(sample.z()));
        if (!cell.equals(_last)) {
            _cells[_next] = cell;
            _ticks[_next] = sample.tick();
            _next = (_next + 1) % CAPACITY;
            if (_size < CAPACITY) _size++;
            _last = cell;
            int period = repeatedPeriod();
            if (period == 0) {
                _repeatingSince = -1;
            } else if (_repeatingSince < 0) {
                _repeatingSince = tickAgo(period * REPETITIONS - 1);
            }
        }
        return _repeatingSince >= 0 && sample.tick() - _repeatingSince >= MIN_DURATION_TICKS;
    }

    void reset() {
        _next = 0;
        _size = 0;
        _last = null;
        _repeatingSince = -1;
    }

    private int repeatedPeriod() {
        for (int period = MIN_PERIOD; period <= MAX_PERIOD && period * REPETITIONS <= _size; period++) {
            Set<Cell> distinct = new HashSet<>();
            boolean repeated = true;
            for (int i = 0; i < period; i++) {
                Cell newest = cellAgo(i);
                distinct.add(newest);
                if (!newest.equals(cellAgo(i + period)) || !newest.equals(cellAgo(i + period * 2))) {
                    repeated = false;
                    break;
                }
            }
            if (repeated && distinct.size() >= MIN_PERIOD) return period;
        }
        return 0;
    }

    private Cell cellAgo(int ago) {
        return _cells[Math.floorMod(_next - 1 - ago, CAPACITY)];
    }

    private long tickAgo(int ago) {
        return _ticks[Math.floorMod(_next - 1 - ago, CAPACITY)];
    }
}
