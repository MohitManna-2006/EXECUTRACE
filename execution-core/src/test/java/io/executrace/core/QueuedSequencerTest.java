package io.executrace.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class QueuedSequencerTest {
    @TempDir Path dir;
    @Test void concurrentRequestsReceiveOneTotalOrderAndCapacityCannotOversubmit() throws Exception {
        Path db = dir.resolve("queued.db");
        try (var writer = new QueuedSequencer(db, 128); var pool = Executors.newFixedThreadPool(8)) {
            writer.submit("a",TestOrders.request(1),0,new BusinessEvent.ParentAccepted(TestOrders.parent(10))).join();
            var futures = new ArrayList<java.util.concurrent.Future<CommandReceipt>>();
            for (int i=0;i<20;i++) {
                int n=i;
                futures.add(pool.submit(() -> writer.submit("a",TestOrders.request(n+2),n+1,
                        new BusinessEvent.ChildCreated(new ChildOrder(new java.util.UUID(1,n+1),TestOrders.PARENT,
                                "C-"+n,10,0,0,185_250_000,0,OrderState.PENDING_NEW,RequestState.NONE,null,0))).join()));
            }
            long accepted=0;
            for (var f:futures) if (f.get().accepted()) accepted++;
            assertThat(accepted).isEqualTo(1);
            assertThat(writer.state().liveLeaves(TestOrders.PARENT)).isEqualTo(10);
        }
        try (var reopened = new SingleWriterSequencer(db)) {
            assertThat(reopened.state().liveLeaves(TestOrders.PARENT)).isEqualTo(10);
            assertThat(reopened.events()).hasSize(21);
        }
    }
}
