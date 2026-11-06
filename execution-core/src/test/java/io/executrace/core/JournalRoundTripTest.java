package io.executrace.core;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class JournalRoundTripTest {
    @TempDir Path dir;
    @Test void restartReplaysExactProjection() {
        Path db = dir.resolve("orders.db"); String digest;
        try (var s = new SingleWriterSequencer(db)) {
            s.submit("account", TestOrders.request(1), 0, new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
            s.submit("account", TestOrders.request(2), 1, new BusinessEvent.ChildCreated(TestOrders.child(10)));
            s.submit("broker", TestOrders.request(3), 2, new BusinessEvent.FillReceived(TestOrders.fill("E-1", 3)));
            digest = ReplayVerifier.digest(s.state());
            assertThat(s.events()).extracting(JournalEnvelope::sequencerOffset).containsExactly(1L,2L,3L);
            assertThat(s.verifyReplay().projectionDigestSha256()).isEqualTo(digest);
        }
        try (var s = new SingleWriterSequencer(db)) {
            assertThat(ReplayVerifier.digest(s.state())).isEqualTo(digest);
            assertThat(s.state().parents().get(TestOrders.PARENT).filledQty()).isEqualTo(3);
        }
    }
}
