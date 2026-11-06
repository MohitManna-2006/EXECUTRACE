package io.executrace.core;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class CrashWindowTest {
    @TempDir Path dir;
    @Test void deathBeforeCommitLeavesNoAcceptedOrder() throws Exception {
        Path db = dir.resolve("before.db");
        assertThat(runChild(db,"beforeCommit")).isEqualTo(86);
        try (var writer = new SingleWriterSequencer(db)) {
            assertThat(writer.events()).isEmpty();
            assertThat(writer.state().parents()).isEmpty();
        }
    }
    @Test void deathAfterCommitBeforePublicationReplaysAcceptedOrder() throws Exception {
        Path db = dir.resolve("after.db");
        assertThat(runChild(db,"afterCommitBeforePublish")).isEqualTo(86);
        try (var writer = new SingleWriterSequencer(db)) {
            assertThat(writer.events()).hasSize(1);
            assertThat(writer.state().parents()).containsKey(TestOrders.PARENT);
            assertThat(writer.submit("account",TestOrders.request(1),0,
                    new BusinessEvent.ParentAccepted(TestOrders.parent(10))).duplicateRequest()).isTrue();
        }
    }
    private int runChild(Path db, String point) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-cp", System.getProperty("java.class.path"), CrashChild.class.getName(), db.toString(), point)
                .redirectErrorStream(true).start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly(); throw new AssertionError("crash child hung");
        }
        return process.exitValue();
    }
}
