package io.executrace.core;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Bounded admission with one thread owning all command application and journal writes. */
public final class QueuedSequencer implements AutoCloseable {
    private static final Runnable STOP = () -> {};
    private final ArrayBlockingQueue<Runnable> queue;
    private final SingleWriterSequencer core;
    private final Thread writer;
    private boolean closed;
    public QueuedSequencer(Path database, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        core = new SingleWriterSequencer(database);
        queue = new ArrayBlockingQueue<>(capacity);
        writer = Thread.ofPlatform().name("executrace-sequencer").start(() -> {
            try {
                for (;;) {
                    Runnable task = queue.take();
                    if (task == STOP) break;
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { core.close(); }
        });
    }
    public synchronized CompletableFuture<CommandReceipt> submit(String scope, UUID requestId,
                                                                    long eventTimeNs, BusinessEvent event) {
        if (closed) throw new IllegalStateException("sequencer closed");
        CompletableFuture<CommandReceipt> result = new CompletableFuture<>();
        if (!queue.offer(() -> {
            try { result.complete(core.submit(scope, requestId, eventTimeNs, event)); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        })) throw new IllegalStateException("sequencer queue full before acceptance");
        return result;
    }
    public OmsState state() { return core.state(); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { queue.put(STOP); writer.join(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
    }
}
