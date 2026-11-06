package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReducerLifecycleTest {
    @Test void ackPartialFillAndCompletion() {
        var s = TestOrders.open(10);
        s = OmsReducer.apply(s, new BusinessEvent.Acknowledged(TestOrders.CHILD, "B-1"));
        s = OmsReducer.apply(s, new BusinessEvent.FillReceived(TestOrders.fill("E-1", 3)));
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.PARTIALLY_FILLED);
        assertThat(s.children().get(TestOrders.CHILD).leavesQty()).isEqualTo(7);
        s = OmsReducer.apply(s, new BusinessEvent.FillReceived(TestOrders.fill("E-2", 7)));
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.FILLED);
        assertThat(s.parents().get(TestOrders.PARENT).filledQty()).isEqualTo(10);
    }
    @Test void lateAckPreservesFill() {
        var s = OmsReducer.apply(TestOrders.open(10), new BusinessEvent.FillReceived(TestOrders.fill("E-1", 3)));
        s = OmsReducer.apply(s, new BusinessEvent.Acknowledged(TestOrders.CHILD, "B-1"));
        assertThat(s.children().get(TestOrders.CHILD).cumQty()).isEqualTo(3);
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.PARTIALLY_FILLED);
        assertThat(s.children().get(TestOrders.CHILD).brokerOrderId()).isEqualTo("B-1");
    }
}
