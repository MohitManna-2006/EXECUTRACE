package io.executrace.core;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class OutboxAtomicityTest {
    @TempDir Path dir;
    @Test void childIntentAndOutboxSurviveRestartTogether() {
        Path db = dir.resolve("outbox.db");
        try (var s = new SingleWriterSequencer(db)) {
            s.submit("a",TestOrders.request(1),0,new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
            s.submit("a",TestOrders.request(2),1,new BusinessEvent.ChildCreated(TestOrders.child(10)));
            assertThat(s.pendingOutbox()).singleElement().satisfies(o -> {
                assertThat(o.sourceOffset()).isEqualTo(2);
                assertThat(o.commandType()).isEqualTo("SUBMIT");
                assertThat(o.clientOrderId()).isEqualTo("C-1");
            });
        }
        try (var s = new SingleWriterSequencer(db)) {
            assertThat(s.pendingOutbox()).hasSize(1);
            assertThat(s.state().children()).hasSize(1);
        }
    }
}
