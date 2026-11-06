package io.executrace.core;

/** Test seam for deterministic commit-window process termination; production uses NONE. */
@FunctionalInterface
public interface CommitHook {
    CommitHook NONE = point -> {};
    void at(String point);
}
