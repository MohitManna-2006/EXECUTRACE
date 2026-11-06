package io.executrace.core;

import java.util.UUID;

final class TestOrders {
    static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PARENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID CHILD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static ParentOrder parent(long qty) { return new ParentOrder(PARENT, ACCOUNT, "AAPL", Side.BUY, qty, 185_250_000, 0); }
    static ChildOrder child(long qty) { return new ChildOrder(CHILD, PARENT, "C-1", qty, 0, 0, 185_250_000,
            0, OrderState.PENDING_NEW, RequestState.NONE, null, 0); }
    static OmsState open(long qty) {
        return OmsReducer.apply(OmsReducer.apply(OmsState.empty(), new BusinessEvent.ParentAccepted(parent(qty))),
                new BusinessEvent.ChildCreated(child(qty)));
    }
    static Fill fill(String id, long qty) { return new Fill(new FillKey("S-1", id), CHILD, qty, 185_250_000, 0); }
    static UUID request(long number) { return new UUID(0, number); }
}
