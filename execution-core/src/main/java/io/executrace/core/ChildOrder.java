package io.executrace.core;

import java.util.UUID;

public record ChildOrder(UUID id, UUID parentId, String clientOrderId, long effectiveQty, long cumQty,
                         long canceledQty, long limitPriceMicroUsd, long fillNotionalMicroUsd,
                         OrderState state, RequestState requestState, String brokerOrderId, long revision) {
    public ChildOrder {
        Checks.id(id, "childId"); Checks.id(parentId, "parentId"); Checks.text(clientOrderId, "clientOrderId", 64);
        Checks.positive(effectiveQty, "effectiveQty"); Checks.nonnegative(cumQty, "cumQty");
        Checks.nonnegative(canceledQty, "canceledQty"); Checks.positive(limitPriceMicroUsd, "limitPriceMicroUsd");
        Checks.nonnegative(fillNotionalMicroUsd, "fillNotionalMicroUsd");
        Checks.present(state, "state"); Checks.present(requestState, "requestState"); Checks.nonnegative(revision, "revision");
        if (Math.addExact(cumQty, canceledQty) > effectiveQty) throw new IllegalArgumentException("child overfilled/canceled");
    }
    public long leavesQty() { return effectiveQty - cumQty - canceledQty; }
    public boolean liveOrUnknown() {
        return switch (state) {
            case PENDING_NEW, NEW, PARTIALLY_FILLED, UNKNOWN, SUSPENDED, RECONCILIATION_REQUIRED -> true;
            default -> false;
        };
    }
    public ChildOrder change(long newEffectiveQty, long newCumQty, long newCanceledQty, long newLimit,
                             long newNotional, OrderState newState, RequestState newRequest, String newBrokerId) {
        return new ChildOrder(id, parentId, clientOrderId, newEffectiveQty, newCumQty, newCanceledQty,
                newLimit, newNotional, newState, newRequest, newBrokerId, Math.addExact(revision, 1));
    }
}
