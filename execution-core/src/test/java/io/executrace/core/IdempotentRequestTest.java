package io.executrace.core;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IdempotentRequestTest {
    @TempDir Path dir;
    @Test void requestRetryReturnsOriginalReceiptAfterRestart() {
        Path db = dir.resolve("dedupe.db"); var input = new BusinessEvent.ParentAccepted(TestOrders.parent(10));
        CommandReceipt first;
        try (var s = new SingleWriterSequencer(db)) { first = s.submit("account",TestOrders.request(1),0,input); }
        try (var s = new SingleWriterSequencer(db)) {
            var retry = s.submit("account",TestOrders.request(1),99,input);
            assertThat(retry.offset()).isEqualTo(first.offset());
            assertThat(retry.projectionDigestSha256()).isEqualTo(first.projectionDigestSha256());
            assertThat(retry.duplicateRequest()).isTrue();
            assertThat(s.events()).hasSize(1);
            assertThatThrownBy(() -> s.submit("account",TestOrders.request(1),100,
                    new BusinessEvent.ParentAccepted(TestOrders.parent(11))))
                    .isInstanceOf(DomainException.class);
        }
    }
}
