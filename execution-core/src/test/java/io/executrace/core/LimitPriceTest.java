package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LimitPriceTest {
    @Test void buyFillAboveLimitIsRejectedBeforeEconomicMutation() {
        var before = TestOrders.open(10);
        var bad = new Fill(new FillKey("S-1","E-high"),TestOrders.CHILD,1,185_250_001,0);
        assertThatThrownBy(() -> OmsReducer.apply(before,new BusinessEvent.FillReceived(bad)))
                .isInstanceOf(DomainException.class).hasMessageContaining("limit price");
        assertThat(before.parents().get(TestOrders.PARENT).filledQty()).isZero();
    }
}
