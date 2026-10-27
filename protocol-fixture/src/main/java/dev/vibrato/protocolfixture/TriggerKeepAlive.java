package dev.vibrato.protocolfixture;

/** Requests an immediate native keepalive on the next server tick, unless one is already outstanding. */
public interface TriggerKeepAlive {
    void vibrato$triggerKeepAlive();
}
