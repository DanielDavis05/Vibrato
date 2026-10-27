package dev.vibrato.tasks.movement;

/** Search time advances only on ticks owned by the search, never during other chains. */
final class SearchActiveClock {
    private long ticks;
    double tick() { return ++ticks / 20.0; }
    double elapsed() { return ticks / 20.0; }
}
