package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReplaceTransitionTest {
    @Test void replacementKeepsExecutedQuantityAndChangesClientChain() {
        var s = TestOrders.open(20);
        s = OmsReducer.apply(s,new BusinessEvent.FillReceived(TestOrders.fill("E-1",5)));
        s = OmsReducer.apply(s,new BusinessEvent.ReplaceRequested(TestOrders.CHILD,new Replacement("C-2",12,185_000_000)));
        assertThat(s.children().get(TestOrders.CHILD).effectiveQty()).isEqualTo(20);
        s = OmsReducer.apply(s,new BusinessEvent.ReplaceConfirmed(TestOrders.CHILD));
        var c = s.children().get(TestOrders.CHILD);
        assertThat(c.effectiveQty()).isEqualTo(12);
        assertThat(c.cumQty()).isEqualTo(5);
        assertThat(c.leavesQty()).isEqualTo(7);
        assertThat(c.clientOrderId()).isEqualTo("C-2");
    }
    @Test void lateFillCanMakeReplacementInvalid() {
        var s = OmsReducer.apply(TestOrders.open(20),new BusinessEvent.ReplaceRequested(TestOrders.CHILD,new Replacement("C-2",5,185_000_000)));
        s = OmsReducer.apply(s,new BusinessEvent.FillReceived(TestOrders.fill("E-1",6)));
        var beforeAck = s;
        assertThatThrownBy(() -> OmsReducer.apply(beforeAck,new BusinessEvent.ReplaceConfirmed(TestOrders.CHILD)))
                .isInstanceOf(DomainException.class);
    }
}
