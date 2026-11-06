package io.executrace.core;

import java.math.BigDecimal;

/** USD per share in integer micro-USD; conversion rejects excess precision. */
public record Price(long microUsd) {
    public Price { Checks.positive(microUsd, "priceMicroUsd"); }
    public static Price parse(String decimalUsd) {
        if (decimalUsd == null) throw new IllegalArgumentException("price required");
        return new Price(new BigDecimal(decimalUsd).movePointRight(6).longValueExact());
    }
    public long notional(long shares) { return Math.multiplyExact(microUsd, Checks.positive(shares, "shares")); }
    public void requireTick(long tickMicroUsd) {
        Checks.positive(tickMicroUsd, "tickMicroUsd");
        if (microUsd % tickMicroUsd != 0) throw new IllegalArgumentException("price is off tick");
    }
}
