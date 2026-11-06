package io.executrace.core;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnapshotRecoveryTest {
    @TempDir Path dir;
    @Test void validSnapshotAndTailMatchGenesisReplay() {
        Path db = dir.resolve("snap.db"); String digest;
        try (var s = new SingleWriterSequencer(db)) {
            s.submit("a",TestOrders.request(1),0,new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
            s.submit("a",TestOrders.request(2),1,new BusinessEvent.ChildCreated(TestOrders.child(10)));
            s.snapshot();
            s.submit("b",TestOrders.request(3),2,new BusinessEvent.FillReceived(TestOrders.fill("E-1",3)));
            digest = ReplayVerifier.digest(s.state());
        }
        try (var s = new SingleWriterSequencer(db)) { assertThat(ReplayVerifier.digest(s.state())).isEqualTo(digest); }
    }
    @Test void corruptSnapshotFallsBackToGenesis() throws Exception {
        Path db = dir.resolve("corrupt-snapshot.db"); String digest;
        try (var s = new SingleWriterSequencer(db)) {
            s.submit("a",TestOrders.request(1),0,new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
            s.snapshot(); digest = ReplayVerifier.digest(s.state());
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + db); var st = c.createStatement()) {
            st.executeUpdate("UPDATE snapshots SET state_json='corrupt'");
        }
        try (var s = new SingleWriterSequencer(db)) { assertThat(ReplayVerifier.digest(s.state())).isEqualTo(digest); }
    }
}
