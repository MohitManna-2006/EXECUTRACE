package io.executrace.core;

import java.nio.file.Path;

/** Runs only as a subprocess launched by CrashWindowTest. */
public final class CrashChild {
    private CrashChild() {}
    public static void main(String[] args) {
        CommitHook hook = point -> {
            if (point.equals(args[1])) Runtime.getRuntime().halt(86);
        };
        try (var writer = new SingleWriterSequencer(Path.of(args[0]), hook)) {
            writer.submit("account", TestOrders.request(1), 0,
                    new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
        }
    }
}
