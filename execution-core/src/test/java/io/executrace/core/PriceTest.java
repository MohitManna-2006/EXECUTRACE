package io.executrace.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PriceTest {
    @Test void exactFixedPointConversion() {
        assertThat(Price.parse("185.250000").microUsd()).isEqualTo(185_250_000);
        assertThat(Price.parse("0.000001").microUsd()).isEqualTo(1);
        assertThatThrownBy(() -> Price.parse("0.0000001")).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Price.parse("NaN")).isInstanceOf(NumberFormatException.class);
    }
    @Test void rejectsOffTickAndOverflow() {
        assertThatThrownBy(() -> new Price(3).requireTick(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Price(Long.MAX_VALUE).notional(2)).isInstanceOf(ArithmeticException.class);
    }
}
