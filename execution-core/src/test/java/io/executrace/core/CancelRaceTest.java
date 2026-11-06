package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CancelRaceTest {
    @Test void fillDuringPendingCancelDoesNotPretendCancellationSucceeded() {
        var s = OmsReducer.apply(TestOrders.open(10), new BusinessEvent.Acknowledged(TestOrders.CHILD,"B-1"));
        s = OmsReducer.apply(s, new BusinessEvent.CancelRequested(TestOrders.CHILD));
        assertThat(s.children().get(TestOrders.CHILD).leavesQty()).isEqualTo(10);
        s = OmsReducer.apply(s, new BusinessEvent.FillReceived(TestOrders.fill("E-1", 4)));
        assertThat(s.children().get(TestOrders.CHILD).requestState()).isEqualTo(RequestState.PENDING_CANCEL);
        assertThat(s.children().get(TestOrders.CHILD).leavesQty()).isEqualTo(6);
        s = OmsReducer.apply(s, new BusinessEvent.CancelConfirmed(TestOrders.CHILD, 6));
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.CANCELED);
        assertThat(s.children().get(TestOrders.CHILD).canceledQty()).isEqualTo(6);
        assertThat(s.parents().get(TestOrders.PARENT).filledQty()).isEqualTo(4);
    }
    @Test void wrongCancelQuantityIsRejected() {
        var s = OmsReducer.apply(TestOrders.open(10), new BusinessEvent.CancelRequested(TestOrders.CHILD));
        assertThatThrownBy(() -> OmsReducer.apply(s,new BusinessEvent.CancelConfirmed(TestOrders.CHILD,9)))
                .isInstanceOf(DomainException.class);
    }
}
