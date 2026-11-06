package io.executrace.core;

import java.util.Map;
import java.util.UUID;

/** Durable business projection. All maps are copied after each reduction. */
public record OmsState(Map<UUID, ParentOrder> parents, Map<UUID, ChildOrder> children,
                       Map<String, Fill> fills, Map<UUID, Replacement> replacements, java.util.Set<String> usedClientOrderIds) {
    public OmsState {
        parents = Map.copyOf(parents); children = Map.copyOf(children);
        fills = Map.copyOf(fills); replacements = Map.copyOf(replacements);
        usedClientOrderIds = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(usedClientOrderIds));
    }
    public static OmsState empty() { return new OmsState(Map.of(), Map.of(), Map.of(), Map.of(), java.util.Set.of()); }
    public long liveLeaves(UUID parentId) {
        long total = 0;
        for (ChildOrder c : children.values()) if (c.parentId().equals(parentId) && c.liveOrUnknown())
            total = Math.addExact(total, c.leavesQty());
        return total;
    }
}
