package io.executrace.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32C;

/** One-connection, single-writer SQLite journal. A commit includes event, dedupe and outbox. */
public final class SqliteJournal implements AutoCloseable {
    public static final String GENESIS_DIGEST = "0".repeat(64);
    private final Connection db;
    private final CommitHook hook;
    public record Dedupe(long offset, String payloadHash, boolean accepted, String reason, String projectionDigestSha256) {}

    public SqliteJournal(Path file) { this(file, CommitHook.NONE); }
    public SqliteJournal(Path file, CommitHook hook) {
        this.hook = Checks.present(hook, "hook");
        try {
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=FULL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS events (offset INTEGER PRIMARY KEY, event_id TEXT NOT NULL UNIQUE, schema_version INTEGER NOT NULL, reducer_version INTEGER NOT NULL, event_type TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, event_time_ns INTEGER NOT NULL, receive_utc TEXT NOT NULL, processing_utc TEXT NOT NULL, payload_json TEXT NOT NULL, payload_crc32c INTEGER NOT NULL, previous_digest TEXT NOT NULL, digest TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS request_dedupe (scope TEXT NOT NULL, request_id TEXT NOT NULL, payload_hash TEXT NOT NULL, offset INTEGER NOT NULL, accepted INTEGER NOT NULL, reason TEXT NOT NULL, projection_digest TEXT NOT NULL, PRIMARY KEY(scope, request_id), FOREIGN KEY(offset) REFERENCES events(offset))");
                s.execute("CREATE TABLE IF NOT EXISTS outbox (source_offset INTEGER PRIMARY KEY, command_type TEXT NOT NULL, client_order_id TEXT NOT NULL, payload_json TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','ATTEMPTED','CONFIRMED','UNKNOWN')), FOREIGN KEY(source_offset) REFERENCES events(offset))");
                s.execute("CREATE TABLE IF NOT EXISTS snapshots (last_offset INTEGER PRIMARY KEY, reducer_version INTEGER NOT NULL, journal_digest TEXT NOT NULL, state_json TEXT NOT NULL, state_sha256 TEXT NOT NULL, FOREIGN KEY(last_offset) REFERENCES events(offset))");
            }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
                if (!r.next() || !"ok".equals(r.getString(1))) throw new IllegalStateException("SQLite integrity check failed");
            }
        } catch (SQLException e) { throw new IllegalStateException("journal open failed", e); }
    }
    public synchronized Dedupe dedupe(String scope, UUID requestId) {
        try (PreparedStatement p = db.prepareStatement("SELECT offset,payload_hash,accepted,reason,projection_digest FROM request_dedupe WHERE scope=? AND request_id=?")) {
            p.setString(1, scope); p.setString(2, requestId.toString());
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? new Dedupe(r.getLong(1), r.getString(2), r.getInt(3) == 1, r.getString(4), r.getString(5)) : null;
            }
        } catch (SQLException e) { throw new IllegalStateException("dedupe read failed", e); }
    }
    public synchronized JournalEnvelope append(String scope, UUID requestId, String payloadHash, long eventTimeNs,
                                                BusinessEvent event, boolean accepted, String reason, String projectionDigestSha256,
                                                OutboxEntry outbox) {
        Checks.text(scope, "scope", 128); Checks.id(requestId, "requestId");
        Checks.nonnegative(eventTimeNs, "eventTimeNs");
        byte[] payload = CanonicalJson.bytes(event);
        CRC32C crc = new CRC32C(); crc.update(payload);
        try {
            db.setAutoCommit(false);
            long offset = 1; String previous = GENESIS_DIGEST;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT offset,digest FROM events ORDER BY offset DESC LIMIT 1")) {
                if (r.next()) { offset = Math.addExact(r.getLong(1), 1); previous = r.getString(2); }
            }
            UUID eventId = UUID.randomUUID();
            String eventType = event.getClass().getSimpleName();
            String entityType = entityType(event);
            String entityId = entityId(event, eventId);
            String receiveUtc = Instant.now().toString();
            String processingUtc = Instant.now().toString();
            JournalEnvelope.Unsigned unsigned = new JournalEnvelope.Unsigned(1, OmsReducer.VERSION, eventId, offset,
                    eventType, entityType, entityId, eventTimeNs, receiveUtc, processingUtc, event, crc.getValue(), previous);
            String digest = CanonicalJson.sha256(CanonicalJson.bytes(unsigned));
            JournalEnvelope envelope = new JournalEnvelope(1, OmsReducer.VERSION, eventId, offset, eventType, entityType,
                    entityId, eventTimeNs, receiveUtc, processingUtc, event, crc.getValue(), previous, digest);
            try (PreparedStatement p = db.prepareStatement("INSERT INTO events VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                p.setLong(1, offset); p.setString(2, eventId.toString()); p.setInt(3, 1); p.setInt(4, OmsReducer.VERSION);
                p.setString(5, eventType); p.setString(6, entityType); p.setString(7, entityId);
                p.setLong(8, eventTimeNs); p.setString(9, receiveUtc); p.setString(10, processingUtc);
                p.setString(11, new String(payload, StandardCharsets.UTF_8)); p.setLong(12, crc.getValue());
                p.setString(13, previous); p.setString(14, digest); p.executeUpdate();
            }
            try (PreparedStatement p = db.prepareStatement("INSERT INTO request_dedupe VALUES(?,?,?,?,?,?,?)")) {
                p.setString(1, scope); p.setString(2, requestId.toString()); p.setString(3, payloadHash);
                p.setLong(4, offset); p.setInt(5, accepted ? 1 : 0); p.setString(6, reason); p.setString(7, projectionDigestSha256); p.executeUpdate();
            }
            if (outbox != null) try (PreparedStatement p = db.prepareStatement("INSERT INTO outbox VALUES(?,?,?,?,?)")) {
                p.setLong(1, offset); p.setString(2, outbox.commandType()); p.setString(3, outbox.clientOrderId());
                p.setString(4, outbox.payloadJson()); p.setString(5, "PENDING"); p.executeUpdate();
            }
            hook.at("beforeCommit");
            db.commit(); return envelope;
        } catch (Exception e) {
            try { db.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
            throw new IllegalStateException("journal commit failed", e);
        } finally {
            try { db.setAutoCommit(true); } catch (SQLException e) { throw new IllegalStateException("journal transaction reset failed", e); }
        }
    }
    public synchronized List<JournalEnvelope> readAll() {
        List<JournalEnvelope> result = new ArrayList<>();
        long expected = 1; String previous = GENESIS_DIGEST;
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT offset,event_id,schema_version,reducer_version,event_type,entity_type,entity_id,event_time_ns,receive_utc,processing_utc,payload_json,payload_crc32c,previous_digest,digest FROM events ORDER BY offset")) {
            while (r.next()) {
                long offset = r.getLong(1); if (offset != expected++) throw new IllegalStateException("journal offset gap at " + offset);
                if (r.getInt(3) != 1 || r.getInt(4) != OmsReducer.VERSION) throw new IllegalStateException("unsupported journal version at " + offset);
                byte[] payloadBytes = r.getString(11).getBytes(StandardCharsets.UTF_8);
                CRC32C crc = new CRC32C(); crc.update(payloadBytes);
                if (crc.getValue() != r.getLong(12)) throw new IllegalStateException("payload CRC mismatch at " + offset);
                BusinessEvent event = CanonicalJson.read(payloadBytes, BusinessEvent.class);
                if (!java.util.Arrays.equals(payloadBytes, CanonicalJson.bytes(event))) throw new IllegalStateException("noncanonical payload at " + offset);
                JournalEnvelope e = new JournalEnvelope(1, OmsReducer.VERSION, UUID.fromString(r.getString(2)), offset,
                        r.getString(5), r.getString(6), r.getString(7), r.getLong(8), r.getString(9), r.getString(10),
                        event, r.getLong(12), r.getString(13), r.getString(14));
                if (!event.getClass().getSimpleName().equals(e.eventType()) || !entityType(event).equals(e.entityType()) ||
                        !entityId(event, e.eventId()).equals(e.entityId()))
                    throw new IllegalStateException("event identity mismatch at " + offset);
                if (!previous.equals(e.previousDigestSha256()) ||
                        !CanonicalJson.sha256(CanonicalJson.bytes(e.unsigned())).equals(e.digestSha256()))
                    throw new IllegalStateException("journal digest mismatch at " + offset);
                result.add(e); previous = e.digestSha256();
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw new IllegalStateException("journal read failed", e); }
    }
    public synchronized List<OutboxEntry> pendingOutbox() {
        List<OutboxEntry> result = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT source_offset,command_type,client_order_id,payload_json,state FROM outbox WHERE state IN ('PENDING','UNKNOWN') ORDER BY source_offset")) {
            while (r.next()) result.add(new OutboxEntry(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)));
            return List.copyOf(result);
        } catch (SQLException e) { throw new IllegalStateException("outbox read failed", e); }
    }
    public synchronized void markOutboxUnknown(long offset) {
        try (PreparedStatement p = db.prepareStatement("UPDATE outbox SET state='UNKNOWN' WHERE source_offset=? AND state='PENDING'")) {
            p.setLong(1, offset); if (p.executeUpdate() != 1) throw new IllegalStateException("outbox not pending");
        } catch (SQLException e) { throw new IllegalStateException("outbox update failed", e); }
    }
    public synchronized void saveSnapshot(long offset, OmsState state) {
        if (offset <= 0) throw new IllegalArgumentException("snapshot requires committed offset");
        List<JournalEnvelope> events = readAll();
        if (offset > events.size()) throw new IllegalArgumentException("snapshot beyond journal");
        byte[] bytes = CanonicalJson.bytes(state);
        try (PreparedStatement p = db.prepareStatement("INSERT OR REPLACE INTO snapshots VALUES(?,?,?,?,?)")) {
            p.setLong(1, offset); p.setInt(2, OmsReducer.VERSION); p.setString(3, events.get((int)offset-1).digestSha256());
            p.setString(4, new String(bytes, StandardCharsets.UTF_8)); p.setString(5, CanonicalJson.sha256(bytes)); p.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("snapshot write failed", e); }
    }
    public record LoadedSnapshot(long lastOffset, OmsState state) {}
    public synchronized LoadedSnapshot validSnapshot(List<JournalEnvelope> events) {
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_offset,reducer_version,journal_digest,state_json,state_sha256 FROM snapshots ORDER BY last_offset DESC")) {
            while (r.next()) {
                long offset = r.getLong(1); byte[] bytes = r.getString(4).getBytes(StandardCharsets.UTF_8);
                if (r.getInt(2) != OmsReducer.VERSION || offset > events.size() || offset <= 0) continue;
                if (!events.get((int)offset-1).digestSha256().equals(r.getString(3)) ||
                        !CanonicalJson.sha256(bytes).equals(r.getString(5))) continue;
                try { return new LoadedSnapshot(offset, CanonicalJson.read(bytes, OmsState.class)); }
                catch (IllegalArgumentException ignored) { /* fall back to older snapshot or genesis */ }
            }
            return null;
        } catch (SQLException e) { throw new IllegalStateException("snapshot read failed", e); }
    }
    private static String entityType(BusinessEvent event) {
        return event instanceof BusinessEvent.ParentAccepted ? "ParentOrder" :
                event instanceof BusinessEvent.Quarantined ? "JournalEvent" : "ChildOrder";
    }
    private static String entityId(BusinessEvent event, UUID fallback) {
        if (event instanceof BusinessEvent.ParentAccepted e) return e.parent().id().toString();
        if (event instanceof BusinessEvent.ChildCreated e) return e.child().id().toString();
        if (event instanceof BusinessEvent.FillReceived e) return e.fill().childId().toString();
        if (event instanceof BusinessEvent.Acknowledged e) return e.childId().toString();
        if (event instanceof BusinessEvent.CancelRequested e) return e.childId().toString();
        if (event instanceof BusinessEvent.CancelConfirmed e) return e.childId().toString();
        if (event instanceof BusinessEvent.CancelRejected e) return e.childId().toString();
        if (event instanceof BusinessEvent.ReplaceRequested e) return e.childId().toString();
        if (event instanceof BusinessEvent.ReplaceConfirmed e) return e.childId().toString();
        if (event instanceof BusinessEvent.ReplaceRejected e) return e.childId().toString();
        if (event instanceof BusinessEvent.Expired e) return e.childId().toString();
        if (event instanceof BusinessEvent.Suspended e) return e.childId().toString();
        if (event instanceof BusinessEvent.Resumed e) return e.childId().toString();
        if (event instanceof BusinessEvent.ReconciliationRequired e) return e.childId().toString();
        if (event instanceof BusinessEvent.BrokerRejected e) return e.childId().toString();
        if (event instanceof BusinessEvent.UnknownOutcome e) return e.childId().toString();
        return fallback.toString();
    }
    @Override public void close() {
        try { db.close(); } catch (SQLException e) { throw new IllegalStateException("journal close failed", e); }
    }
}
