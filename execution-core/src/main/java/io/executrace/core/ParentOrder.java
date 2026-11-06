package io.executrace.core;

import java.util.UUID;

public record ParentOrder(UUID id, UUID accountId, String symbol, Side side, long originalQty,
                          long limitPriceMicroUsd, long filledQty) {
    public ParentOrder {
        Checks.id(id, "parentId"); Checks.id(accountId, "accountId");
        Checks.text(symbol, "symbol", 16); Checks.present(side, "side");
        Checks.positive(originalQty, "originalQty"); Checks.positive(limitPriceMicroUsd, "limitPriceMicroUsd");
        Checks.nonnegative(filledQty, "filledQty");
        if (filledQty > originalQty) throw new IllegalArgumentException("parent overfilled");
    }
    public ParentOrder withFilled(long qty) {
        return new ParentOrder(id, accountId, symbol, side, originalQty, limitPriceMicroUsd, qty);
    }
}
