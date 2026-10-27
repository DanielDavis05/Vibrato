package dev.vibrato.eventbus.events;

import dev.vibrato.tasksystem.Task;

/** A real task lifecycle start, including starts too short to appear in sampled heartbeats. */
public record TaskStartedEvent(Task task) {}
