package io.executrace.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure, versioned business reducer. No clock, IO, random source, or outbound adapter. */
public final class OmsReducer {
    public static final int VERSION = 1;
    private OmsReducer() {}

    public static OmsState apply(OmsState before, BusinessEvent event) {
        Map<UUID, ParentOrder> parents = new HashMap<>(before.parents());
        Map<UUID, ChildOrder> children = new HashMap<>(before.children());
        Map<String, Fill> fills = new HashMap<>(before.fills());
        Map<UUID, Replacement> replacements = new HashMap<>(before.replacements());
        if (event instanceof BusinessEvent.ParentAccepted e) {
            ParentOrder p = e.parent();
            require(p.filledQty() == 0, "new parent cannot have fills");
            require(!parents.containsKey(p.id()), "duplicate parent");
            parents.put(p.id(), p);
        } else if (event instanceof BusinessEvent.ChildCreated e) {
            ChildOrder c = e.child();
            ParentOrder p = parent(parents, c.parentId());
            require(!children.containsKey(c.id()), "duplicate child");
            require(c.state() == OrderState.PENDING_NEW && c.requestState() == RequestState.NONE
                    && c.cumQty() == 0 && c.canceledQty() == 0 && c.revision() == 0,
                    "child must begin pending new and unfilled");
            require(children.values().stream().noneMatch(x -> x.clientOrderId().equals(c.clientOrderId())),
                    "duplicate client order id");
            require(Math.addExact(Math.addExact(p.filledQty(), before.liveLeaves(p.id())), c.effectiveQty()) <= p.originalQty(),
                    "parent capacity exceeded");
            children.put(c.id(), c);
        } else if (event instanceof BusinessEvent.Acknowledged e) {
            ChildOrder c = child(children, e.childId());
            Checks.text(e.brokerOrderId(), "brokerOrderId", 64);
            if (c.state() == OrderState.NEW && e.brokerOrderId().equals(c.brokerOrderId())) return before;
            require(c.state() == OrderState.PENDING_NEW || c.state() == OrderState.UNKNOWN, "ack not expected");
            children.put(c.id(), c.change(c.effectiveQty(), c.cumQty(), c.canceledQty(), c.limitPriceMicroUsd(),
                    c.fillNotionalMicroUsd(), OrderState.NEW, c.requestState(), e.brokerOrderId()));
        } else if (event instanceof BusinessEvent.FillReceived e) {
            Fill f = e.fill(); String key = fillKey(f.key());
            Fill previous = fills.get(key);
            if (previous != null) {
                require(previous.equals(f), "conflicting execution id");
                return before;
            }
            ChildOrder c = child(children, f.childId());
            require(c.liveOrUnknown(), "fill on terminal order");
            long nextCum = Math.addExact(c.cumQty(), f.quantityShares());
            require(nextCum <= c.effectiveQty() - c.canceledQty(), "fill exceeds leaves");
            long nextNotional = Math.addExact(c.fillNotionalMicroUsd(), Math.multiplyExact(f.quantityShares(), f.priceMicroUsd()));
            long nextLeaves = c.effectiveQty() - c.canceledQty() - nextCum;
            OrderState nextState = nextLeaves == 0 ? OrderState.FILLED : OrderState.PARTIALLY_FILLED;
            RequestState request = nextLeaves == 0 ? RequestState.NONE : c.requestState();
            children.put(c.id(), c.change(c.effectiveQty(), nextCum, c.canceledQty(), c.limitPriceMicroUsd(),
                    nextNotional, nextState, request, c.brokerOrderId()));
            ParentOrder p = parent(parents, c.parentId());
            parents.put(p.id(), p.withFilled(Math.addExact(p.filledQty(), f.quantityShares())));
            fills.put(key, f);
            if (nextLeaves == 0) replacements.remove(c.id());
        } else if (event instanceof BusinessEvent.CancelRequested e) {
            ChildOrder c = child(children, e.childId());
            if (c.requestState() == RequestState.PENDING_CANCEL) return before;
            require(c.liveOrUnknown() && c.requestState() == RequestState.NONE, "cancel not permitted");
            children.put(c.id(), c.change(c.effectiveQty(), c.cumQty(), c.canceledQty(), c.limitPriceMicroUsd(),
                    c.fillNotionalMicroUsd(), c.state(), RequestState.PENDING_CANCEL, c.brokerOrderId()));
        } else if (event instanceof BusinessEvent.CancelConfirmed e) {
            ChildOrder c = child(children, e.childId());
            require(c.requestState() == RequestState.PENDING_CANCEL || (c.state() == OrderState.FILLED && e.canceledShares() == 0),
                    "cancel confirmation not expected");
            require(e.canceledShares() == c.leavesQty(), "cancel quantity differs from leaves");
            OrderState state = c.leavesQty() == 0 ? OrderState.FILLED : OrderState.CANCELED;
            children.put(c.id(), c.change(c.effectiveQty(), c.cumQty(), Math.addExact(c.canceledQty(), e.canceledShares()),
                    c.limitPriceMicroUsd(), c.fillNotionalMicroUsd(), state, RequestState.NONE, c.brokerOrderId()));
        } else if (event instanceof BusinessEvent.CancelRejected e) {
            ChildOrder c = child(children, e.childId());
            require(c.requestState() == RequestState.PENDING_CANCEL, "cancel reject not expected");
            children.put(c.id(), c.change(c.effectiveQty(), c.cumQty(), c.canceledQty(), c.limitPriceMicroUsd(),
                    c.fillNotionalMicroUsd(), c.state(), RequestState.NONE, c.brokerOrderId()));
        } else if (event instanceof BusinessEvent.ReplaceRequested e) {
            ChildOrder c = child(children, e.childId()); Replacement r = e.replacement();
            require(c.liveOrUnknown() && c.requestState() == RequestState.NONE, "replace not permitted");
            require(r.newQuantityShares() >= c.cumQty(), "replace below executed quantity");
            require(children.values().stream().noneMatch(x -> x.clientOrderId().equals(r.newClientOrderId())),
                    "duplicate replacement client id");
            replacements.put(c.id(), r);
            children.put(c.id(), c.change(c.effectiveQty(), c.cumQty(), c.canceledQty(), c.limitPriceMicroUsd(),
                    c.fillNotionalMicroUsd(), c.state(), RequestState.PENDING_REPLACE, c.brokerOrderId()));
        } else if (event instanceof BusinessEvent.ReplaceConfirmed e) {
            ChildOrder c = child(children, e.childId()); Replacement r = replacements.get(c.id());
            require(c.requestState() == RequestState.PENDING_REPLACE && r != null, "replace confirmation not expected");
            require(r.newQuantityShares() >= c.cumQty(), "late replace below filled quantity");
            ParentOrder p = parent(parents, c.parentId());
            long otherLeaves = 0;
            for (ChildOrder other : children.values()) if (!other.id().equals(c.id()) && other.parentId().equals(p.id()) && other.liveOrUnknown())
                otherLeaves = Math.addExact(otherLeaves, other.leavesQty());
            require(Math.addExact(Math.addExact(p.filledQty(), otherLeaves), r.newQuantityShares() - c.cumQty()) <= p.originalQty(),
                    "replace exceeds parent capacity");
            OrderState state = c.cumQty() == r.newQuantityShares() ? OrderState.FILLED :
                    c.cumQty() == 0 ? OrderState.NEW : OrderState.PARTIALLY_FILLED;
            children.put(c.id(), new ChildOrder(c.id(), c.parentId(), r.newClientOrderId(), r.newQuantityShares(),
                    c.cumQty(), 0, r.newLimitPriceMicroUsd(), c.fillNotionalMicroUsd(), state,
                    RequestState.NONE, c.brokerOrderId(), Math.addExact(c.revision(), 1)));
            replacements.remove(c.id());
        } else if (event instanceof BusinessEvent.BrokerRejected e) {
            ChildOrder c = child(children, e.childId());
            Checks.text(e.reason(), "reason", 256);
            require(c.state() == OrderState.PENDING_NEW && c.cumQty() == 0, "reject after acceptance/fill");
            children.put(c.id(), c.change(c.effectiveQty(), 0, c.effectiveQty(), c.limitPriceMicroUsd(), 0,
                    OrderState.REJECTED, RequestState.NONE, c.brokerOrderId()));
        } else if (event instanceof BusinessEvent.UnknownOutcome e) {
            ChildOrder c = child(children, e.childId());
            require(c.state() == OrderState.PENDING_NEW, "unknown outcome only for pending new");
            children.put(c.id(), c.change(c.effectiveQty(), c.cumQty(), c.canceledQty(), c.limitPriceMicroUsd(),
                    c.fillNotionalMicroUsd(), OrderState.UNKNOWN, c.requestState(), c.brokerOrderId()));
        } else if (!(event instanceof BusinessEvent.Quarantined)) {
            throw new DomainException("unrecognized event");
        }
        OmsState after = new OmsState(parents, children, fills, replacements);
        for (ParentOrder p : after.parents().values())
            require(Math.addExact(p.filledQty(), after.liveLeaves(p.id())) <= p.originalQty(), "parent overcommitted");
        return after;
    }
    public static String fillKey(FillKey key) { return key.brokerSessionId().length() + ":" + key.brokerSessionId() + key.executionId(); }
    private static ParentOrder parent(Map<UUID, ParentOrder> m, UUID id) {
        ParentOrder p = m.get(id); if (p == null) throw new DomainException("unknown parent " + id); return p;
    }
    private static ChildOrder child(Map<UUID, ChildOrder> m, UUID id) {
        ChildOrder c = m.get(id); if (c == null) throw new DomainException("unknown child " + id); return c;
    }
    private static void require(boolean valid, String reason) { if (!valid) throw new DomainException(reason); }
}
