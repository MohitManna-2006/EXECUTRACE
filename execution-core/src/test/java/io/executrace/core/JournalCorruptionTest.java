package io.executrace.core;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class JournalCorruptionTest {
    @TempDir Path dir;
    @Test void tamperedPayloadFailsClosedAtFirstOffset() throws Exception {
        Path db = dir.resolve("bad.db");
        try (var s = new SingleWriterSequencer(db)) {
            s.submit("a",TestOrders.request(1),0,new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + db); var st = c.createStatement()) {
            st.executeUpdate("UPDATE events SET payload_json='{}' WHERE offset=1");
        }
        assertThatThrownBy(() -> new SingleWriterSequencer(db)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRC mismatch at 1");
    }
}
