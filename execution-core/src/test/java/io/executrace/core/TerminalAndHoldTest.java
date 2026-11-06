package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalAndHoldTest {
    @Test void expiryReleasesLeavesOnlyWhenConfirmed() {
        var s = OmsReducer.apply(TestOrders.open(10),new BusinessEvent.FillReceived(TestOrders.fill("E-1",3)));
        s = OmsReducer.apply(s,new BusinessEvent.Expired(TestOrders.CHILD));
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.EXPIRED);
        assertThat(s.children().get(TestOrders.CHILD).canceledQty()).isEqualTo(7);
        assertThat(s.parents().get(TestOrders.PARENT).filledQty()).isEqualTo(3);
    }
    @Test void suspensionAndReconciliationHoldLeaves() {
        var s = OmsReducer.apply(TestOrders.open(10),new BusinessEvent.Acknowledged(TestOrders.CHILD,"B-1"));
        s = OmsReducer.apply(s,new BusinessEvent.Suspended(TestOrders.CHILD));
        assertThat(s.liveLeaves(TestOrders.PARENT)).isEqualTo(10);
        s = OmsReducer.apply(s,new BusinessEvent.Resumed(TestOrders.CHILD));
        s = OmsReducer.apply(s,new BusinessEvent.ReconciliationRequired(TestOrders.CHILD,"gap"));
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.RECONCILIATION_REQUIRED);
        assertThat(s.liveLeaves(TestOrders.PARENT)).isEqualTo(10);
    }
    @Test void replaceRejectKeepsOldEffectiveQuantity() {
        var s = OmsReducer.apply(TestOrders.open(10),new BusinessEvent.ReplaceRequested(TestOrders.CHILD,
                new Replacement("C-2",5,185_000_000)));
        s = OmsReducer.apply(s,new BusinessEvent.ReplaceRejected(TestOrders.CHILD));
        assertThat(s.children().get(TestOrders.CHILD).effectiveQty()).isEqualTo(10);
        assertThat(s.children().get(TestOrders.CHILD).requestState()).isEqualTo(RequestState.NONE);
    }
}
