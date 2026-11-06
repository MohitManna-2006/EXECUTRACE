package io.executrace.core;

import java.util.UUID;
public record Fill(FillKey key, UUID childId, long quantityShares, long priceMicroUsd, long feeMicroUsd) {
    public Fill {
        Checks.present(key, "key"); Checks.id(childId, "childId"); Checks.positive(quantityShares, "quantityShares");
        Checks.positive(priceMicroUsd, "priceMicroUsd"); Checks.nonnegative(feeMicroUsd, "feeMicroUsd");
        Math.multiplyExact(quantityShares, priceMicroUsd);
    }
}
