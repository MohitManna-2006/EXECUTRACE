package io.executrace.core;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class JournalEnvelopeContractTest {
    @TempDir Path dir;
    @Test void persistedEnvelopeCarriesVersionIdentityClocksAndChecksums() {
        try (var writer = new SingleWriterSequencer(dir.resolve("envelope.db"))) {
            writer.submit("a",TestOrders.request(1),42,new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
            var event = writer.events().getFirst();
            assertThat(event.schemaVersion()).isEqualTo(1);
            assertThat(event.reducerVersion()).isEqualTo(1);
            assertThat(event.eventType()).isEqualTo("ParentAccepted");
            assertThat(event.entityType()).isEqualTo("ParentOrder");
            assertThat(event.entityId()).isEqualTo(TestOrders.PARENT.toString());
            assertThat(event.eventTimeNs()).isEqualTo(42);
            assertThat(event.receiveTimeUtc()).isNotBlank();
            assertThat(event.processingTimeUtc()).isNotBlank();
            assertThat(event.digestSha256()).matches("[0-9a-f]{64}");
            assertThat(event.payloadCrc32c()).isPositive();
        }
    }
}
