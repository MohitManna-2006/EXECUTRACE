package io.executrace.core;

import java.util.List;

/** Business replay has no dispatcher, timer, or other side-effect dependency. */
public final class ReplayVerifier {
    private ReplayVerifier() {}
    public record Result(long lastOffset, OmsState state, String projectionDigestSha256) {}
    public static Result fromGenesis(List<JournalEnvelope> events) {
        OmsState state = OmsState.empty(); long expected = 1;
        for (JournalEnvelope event : events) {
            if (event.sequencerOffset() != expected++) throw new IllegalStateException("replay offset gap");
            if (event.reducerVersion() != OmsReducer.VERSION) throw new IllegalStateException("unsupported reducer version");
            state = OmsReducer.apply(state, event.payload());
        }
        return new Result(expected - 1, state, digest(state));
    }
    public static Result fromSnapshot(OmsState snapshot, long offset, List<JournalEnvelope> events) {
        OmsState state = snapshot;
        if (offset < 0 || offset > events.size()) throw new IllegalArgumentException("snapshot offset invalid");
        for (long i = offset; i < events.size(); i++) {
            JournalEnvelope event = events.get((int)i);
            if (event.sequencerOffset() != i + 1) throw new IllegalStateException("replay offset gap");
            state = OmsReducer.apply(state, event.payload());
        }
        return new Result(events.size(), state, digest(state));
    }
    public static String digest(OmsState state) { return CanonicalJson.sha256(CanonicalJson.bytes(state)); }
}
