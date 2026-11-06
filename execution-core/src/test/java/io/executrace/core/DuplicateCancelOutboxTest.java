package io.executrace.core;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class DuplicateCancelOutboxTest {
    @TempDir Path dir;
    @Test void repeatedCancelWithNewRequestIdCannotCreateSecondOutboxCommand() {
        try (var writer = new SingleWriterSequencer(dir.resolve("cancel.db"))) {
            writer.submit("a",TestOrders.request(1),0,new BusinessEvent.ParentAccepted(TestOrders.parent(10)));
            writer.submit("a",TestOrders.request(2),1,new BusinessEvent.ChildCreated(TestOrders.child(10)));
            writer.submit("a",TestOrders.request(3),2,new BusinessEvent.CancelRequested(TestOrders.CHILD));
            writer.submit("a",TestOrders.request(4),3,new BusinessEvent.CancelRequested(TestOrders.CHILD));
            assertThat(writer.pendingOutbox()).extracting(OutboxEntry::commandType)
                    .containsExactly("SUBMIT","CANCEL");
        }
    }
}
