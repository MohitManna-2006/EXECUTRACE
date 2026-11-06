package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InvalidTransitionTest {
    @Test void overfillAndPrematureCancelConfirmationFail() {
        var s = TestOrders.open(10);
        assertThatThrownBy(() -> OmsReducer.apply(s,new BusinessEvent.FillReceived(TestOrders.fill("E-1",11))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OmsReducer.apply(s,new BusinessEvent.CancelConfirmed(TestOrders.CHILD,10)))
                .isInstanceOf(DomainException.class);
    }
    @Test void unknownOutcomeRetainsLeaves() {
        var s = OmsReducer.apply(TestOrders.open(10),new BusinessEvent.UnknownOutcome(TestOrders.CHILD));
        assertThat(s.children().get(TestOrders.CHILD).state()).isEqualTo(OrderState.UNKNOWN);
        assertThat(s.liveLeaves(TestOrders.PARENT)).isEqualTo(10);
    }
}
