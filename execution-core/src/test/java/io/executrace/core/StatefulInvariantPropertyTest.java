package io.executrace.core;

import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import static org.assertj.core.api.Assertions.*;

class StatefulInvariantPropertyTest {
    @Property(tries = 100)
    void randomizedUniqueAndDuplicateFillsMatchIndependentCounters(@ForAll int seed) {
        Random random = new Random(seed);
        OmsState state = TestOrders.open(1_000);
        long referenceFilled = 0;
        int execution = 0;
        for (int step = 0; step < 100; step++) {
            long remaining = 1_000 - referenceFilled;
            if (remaining == 0) break;
            long qty = 1 + random.nextInt((int)Math.min(remaining, 50));
            Fill fill = TestOrders.fill("E-" + execution++, qty);
            state = OmsReducer.apply(state, new BusinessEvent.FillReceived(fill));
            referenceFilled += qty;
            if (random.nextBoolean()) state = OmsReducer.apply(state, new BusinessEvent.FillReceived(fill));
            assertThat(state.parents().get(TestOrders.PARENT).filledQty()).as("seed=%s step=%s",seed,step)
                    .isEqualTo(referenceFilled);
            assertThat(state.children().get(TestOrders.CHILD).leavesQty()).isEqualTo(1_000-referenceFilled);
            assertThat(state.fills()).hasSize(execution);
        }
    }
}
