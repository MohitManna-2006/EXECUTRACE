package io.executrace.core;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Independent arithmetic model across 20 fixed seeds and 10,000 short scenarios. */
class ReferenceModelTest {
    @Test void fillsDuplicatesAndCancelRacesMatchReferenceCounters() {
        int operations = 0;
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            for (int scenario = 0; scenario < 500; scenario++) {
                long quantity = 1 + random.nextInt(200);
                OmsState actual = TestOrders.open(quantity);
                long referenceFilled = 0;
                boolean cancel = random.nextBoolean();
                if (cancel) {
                    actual = OmsReducer.apply(actual, new BusinessEvent.CancelRequested(TestOrders.CHILD));
                    operations++;
                }
                int chunks = 1 + random.nextInt(5);
                for (int chunk = 0; chunk < chunks && referenceFilled < quantity; chunk++) {
                    long remaining = quantity - referenceFilled;
                    long fillQty = 1 + random.nextInt((int)remaining);
                    Fill f = TestOrders.fill("E-" + chunk, fillQty);
                    actual = OmsReducer.apply(actual, new BusinessEvent.FillReceived(f));
                    referenceFilled += fillQty; operations++;
                    if (random.nextBoolean()) {
                        actual = OmsReducer.apply(actual, new BusinessEvent.FillReceived(f)); operations++;
                    }
                    assertThat(actual.parents().get(TestOrders.PARENT).filledQty())
                            .as("seed=%s scenario=%s chunk=%s",seed,scenario,chunk).isEqualTo(referenceFilled);
                    assertThat(actual.children().get(TestOrders.CHILD).leavesQty()).isEqualTo(quantity-referenceFilled);
                }
                if (cancel) {
                    actual = OmsReducer.apply(actual, new BusinessEvent.CancelConfirmed(TestOrders.CHILD,quantity-referenceFilled));
                    operations++;
                    assertThat(actual.children().get(TestOrders.CHILD).canceledQty()).isEqualTo(quantity-referenceFilled);
                    assertThat(actual.liveLeaves(TestOrders.PARENT)).isZero();
                }
            }
        }
        assertThat(operations).isGreaterThan(10_000);
    }
}
