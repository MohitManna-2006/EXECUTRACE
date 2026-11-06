package io.executrace.core;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Phase-1 local single writer. A later bounded queue may submit to this owner thread. */
public final class SingleWriterSequencer implements AutoCloseable {
    private final SqliteJournal journal;
    private OmsState state;
    public SingleWriterSequencer(Path file) {
        journal = new SqliteJournal(file);
        List<JournalEnvelope> events = journal.readAll();
        ReplayVerifier.Result genesis = ReplayVerifier.fromGenesis(events);
        OmsState snapshot = journal.validSnapshot(events);
        if (snapshot != null) {
            long offset = journal.latestSnapshotOffset(events);
            ReplayVerifier.Result tail = ReplayVerifier.fromSnapshot(snapshot, offset, events);
            if (!tail.projectionDigestSha256().equals(genesis.projectionDigestSha256()))
                throw new IllegalStateException("snapshot/replay projection mismatch");
        }
        state = genesis.state();
    }
    public synchronized CommandReceipt submit(String scope, UUID requestId, long eventTimeNs, BusinessEvent input) {
        Checks.text(scope, "scope", 128); Checks.id(requestId, "requestId"); Checks.present(input, "event");
        String hash = CanonicalJson.sha256(CanonicalJson.bytes(input));
        SqliteJournal.Dedupe old = journal.dedupe(scope, requestId);
        if (old != null) {
            if (!old.payloadHash().equals(hash)) throw new DomainException("requestId reused with different payload");
            return new CommandReceipt(old.offset(), true, old.accepted(), old.reason(), old.projectionDigestSha256());
        }
        OmsState next; BusinessEvent stored = input; boolean accepted = true; String reason = "accepted";
        try { next = OmsReducer.apply(state, input); }
        catch (DomainException | IllegalArgumentException | ArithmeticException e) {
            reason = e.getMessage() == null ? "invalid transition" : e.getMessage();
            stored = new BusinessEvent.Quarantined(reason, input.getClass().getSimpleName());
            next = OmsReducer.apply(state, stored); accepted = false;
        }
        OutboxEntry outbox = accepted ? outboxFor(input, state) : null;
        String digest = ReplayVerifier.digest(next);
        JournalEnvelope committed = journal.append(scope, requestId, hash, eventTimeNs, stored, accepted, reason, digest, outbox);
        state = next; // publish only after durable commit
        return new CommandReceipt(committed.sequencerOffset(), false, accepted, reason, digest);
    }
    private static OutboxEntry outboxFor(BusinessEvent event, OmsState before) {
        String type; String clientId;
        if (event instanceof BusinessEvent.ChildCreated e) { type = "SUBMIT"; clientId = e.child().clientOrderId(); }
        else if (event instanceof BusinessEvent.CancelRequested e) {
            type = "CANCEL"; clientId = before.children().get(e.childId()).clientOrderId();
        } else if (event instanceof BusinessEvent.ReplaceRequested e) {
            type = "REPLACE"; clientId = e.replacement().newClientOrderId();
        } else return null;
        return new OutboxEntry(0, type, clientId, new String(CanonicalJson.bytes(event), StandardCharsets.UTF_8), "PENDING");
    }
    public synchronized OmsState state() { return state; }
    public synchronized List<JournalEnvelope> events() { return journal.readAll(); }
    public synchronized List<OutboxEntry> pendingOutbox() { return journal.pendingOutbox(); }
    public synchronized void snapshot() {
        long offset = events().size(); if (offset > 0) journal.saveSnapshot(offset, state);
    }
    public synchronized ReplayVerifier.Result verifyReplay() { return ReplayVerifier.fromGenesis(journal.readAll()); }
    @Override public void close() { journal.close(); }
}
