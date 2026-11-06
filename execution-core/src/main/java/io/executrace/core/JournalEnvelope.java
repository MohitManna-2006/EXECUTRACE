package io.executrace.core;

import java.util.UUID;

public record JournalEnvelope(int schemaVersion, int reducerVersion, UUID eventId, long sequencerOffset,
                              String eventType, String entityType, String entityId, long eventTimeNs,
                              String receiveTimeUtc, String processingTimeUtc, BusinessEvent payload,
                              long payloadCrc32c, String previousDigestSha256, String digestSha256) {
    public record Unsigned(int schemaVersion, int reducerVersion, UUID eventId, long sequencerOffset,
                           String eventType, String entityType, String entityId, long eventTimeNs,
                           String receiveTimeUtc, String processingTimeUtc, BusinessEvent payload,
                           long payloadCrc32c, String previousDigestSha256) {}
    public Unsigned unsigned() { return new Unsigned(schemaVersion, reducerVersion, eventId, sequencerOffset,
            eventType, entityType, entityId, eventTimeNs, receiveTimeUtc, processingTimeUtc, payload,
            payloadCrc32c, previousDigestSha256); }
}
