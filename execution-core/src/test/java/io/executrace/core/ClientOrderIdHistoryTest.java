package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ClientOrderIdHistoryTest {
    @Test void oldClientIdCannotBeReusedAfterConfirmedReplace() {
        var state = TestOrders.open(20);
        state = OmsReducer.apply(state,new BusinessEvent.ReplaceRequested(TestOrders.CHILD,
                new Replacement("C-2",10,185_000_000)));
        state = OmsReducer.apply(state,new BusinessEvent.ReplaceConfirmed(TestOrders.CHILD));
        var before = state;
        assertThatThrownBy(() -> OmsReducer.apply(before,new BusinessEvent.ReplaceRequested(TestOrders.CHILD,
                new Replacement("C-1",10,185_000_000))))
                .isInstanceOf(DomainException.class).hasMessageContaining("duplicate replacement client id");
        assertThat(before.usedClientOrderIds()).containsExactlyInAnyOrder("C-1","C-2");
    }
}
