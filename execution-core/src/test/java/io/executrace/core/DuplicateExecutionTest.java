package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DuplicateExecutionTest {
    @Test void identicalExecutionIdIsEconomicallyIdempotent() {
        var once = OmsReducer.apply(TestOrders.open(10), new BusinessEvent.FillReceived(TestOrders.fill("E-1", 3)));
        var twice = OmsReducer.apply(once, new BusinessEvent.FillReceived(TestOrders.fill("E-1", 3)));
        assertThat(twice).isSameAs(once);
        assertThat(twice.parents().get(TestOrders.PARENT).filledQty()).isEqualTo(3);
        assertThat(twice.fills()).hasSize(1);
    }
    @Test void conflictingExecutionIdFailsClosed() {
        var once = OmsReducer.apply(TestOrders.open(10), new BusinessEvent.FillReceived(TestOrders.fill("E-1", 3)));
        assertThatThrownBy(() -> OmsReducer.apply(once, new BusinessEvent.FillReceived(TestOrders.fill("E-1", 4))))
                .isInstanceOf(DomainException.class).hasMessageContaining("conflicting");
    }
}
