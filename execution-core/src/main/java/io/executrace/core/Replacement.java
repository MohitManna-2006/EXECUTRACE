package io.executrace.core;

public record Replacement(String newClientOrderId, long newQuantityShares, long newLimitPriceMicroUsd) {
    public Replacement {
        Checks.text(newClientOrderId, "newClientOrderId", 64);
        Checks.positive(newQuantityShares, "newQuantityShares");
        Checks.positive(newLimitPriceMicroUsd, "newLimitPriceMicroUsd");
    }
}
