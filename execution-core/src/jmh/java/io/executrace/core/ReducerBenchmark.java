package io.executrace.core;

import java.util.UUID;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Isolated pure reducer baseline; excludes SQLite, queues, broker and network. */
@State(Scope.Thread)
public class ReducerBenchmark {
    private OmsState state;
    private BusinessEvent fill;
    @Setup public void setup() {
        UUID account = new UUID(0,1), parentId = new UUID(0,2), childId = new UUID(0,3);
        ParentOrder parent = new ParentOrder(parentId,account,"AAPL",Side.BUY,50_000,185_250_000,0);
        ChildOrder child = new ChildOrder(childId,parentId,"C-1",5_000,0,0,185_250_000,0,
                OrderState.NEW,RequestState.NONE,"B-1",0);
        state = OmsReducer.apply(OmsReducer.apply(OmsState.empty(),new BusinessEvent.ParentAccepted(parent)),
                new BusinessEvent.ChildCreated(new ChildOrder(childId,parentId,"C-1",5_000,0,0,185_250_000,0,
                        OrderState.PENDING_NEW,RequestState.NONE,null,0)));
        state = OmsReducer.apply(state,new BusinessEvent.Acknowledged(childId,"B-1"));
        fill = new BusinessEvent.FillReceived(new Fill(new FillKey("S-1","E-1"),childId,100,185_250_000,0));
    }
    @Benchmark public OmsState applyUniqueFill() { return OmsReducer.apply(state,fill); }
}
