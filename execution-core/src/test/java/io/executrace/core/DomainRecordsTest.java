package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DomainRecordsTest {
    @Test void rejectsNegativeAndOverfilledState() {
        assertThatThrownBy(() -> TestOrders.parent(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParentOrder(TestOrders.PARENT, TestOrders.ACCOUNT, "AAPL", Side.BUY, 10, 1, 11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChildOrder(TestOrders.CHILD, TestOrders.PARENT, "C-1", 10, 8, 3, 1, 0,
                OrderState.NEW, RequestState.NONE, null, 0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void projectionCopiesMaps() {
        var m = new java.util.HashMap<java.util.UUID, ParentOrder>();
        m.put(TestOrders.PARENT, TestOrders.parent(10));
        var state = new OmsState(m, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Set.of());
        m.clear(); assertThat(state.parents()).hasSize(1);
        assertThatThrownBy(() -> state.parents().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
