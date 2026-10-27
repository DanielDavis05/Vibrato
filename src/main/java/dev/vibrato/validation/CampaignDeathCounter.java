package dev.vibrato.validation;

import java.util.function.Predicate;

/** Counts each observed death once, including a dead previous player replaced before a heartbeat. */
final class CampaignDeathCounter<T> {
    private T previous;
    private boolean counted;
    private int deaths;

    int observe(T current, Predicate<T> dead) {
        if (previous != null && !counted && dead.test(previous)) {
            deaths++;
            counted = true;
        }
        if (current != null && current != previous) {
            previous = current;
            counted = false;
        }
        if (current != null && !counted && dead.test(current)) {
            deaths++;
            counted = true;
        }
        return deaths;
    }
}
