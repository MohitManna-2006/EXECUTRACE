package io.executrace.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value=BusinessEvent.ParentAccepted.class,name="ParentAccepted"),
    @JsonSubTypes.Type(value=BusinessEvent.ChildCreated.class,name="ChildCreated"),
    @JsonSubTypes.Type(value=BusinessEvent.Acknowledged.class,name="Acknowledged"),
    @JsonSubTypes.Type(value=BusinessEvent.FillReceived.class,name="FillReceived"),
    @JsonSubTypes.Type(value=BusinessEvent.CancelRequested.class,name="CancelRequested"),
    @JsonSubTypes.Type(value=BusinessEvent.CancelConfirmed.class,name="CancelConfirmed"),
    @JsonSubTypes.Type(value=BusinessEvent.CancelRejected.class,name="CancelRejected"),
    @JsonSubTypes.Type(value=BusinessEvent.ReplaceRequested.class,name="ReplaceRequested"),
    @JsonSubTypes.Type(value=BusinessEvent.ReplaceConfirmed.class,name="ReplaceConfirmed"),
    @JsonSubTypes.Type(value=BusinessEvent.ReplaceRejected.class,name="ReplaceRejected"),
    @JsonSubTypes.Type(value=BusinessEvent.Expired.class,name="Expired"),
    @JsonSubTypes.Type(value=BusinessEvent.Suspended.class,name="Suspended"),
    @JsonSubTypes.Type(value=BusinessEvent.Resumed.class,name="Resumed"),
    @JsonSubTypes.Type(value=BusinessEvent.ReconciliationRequired.class,name="ReconciliationRequired"),
    @JsonSubTypes.Type(value=BusinessEvent.BrokerRejected.class,name="BrokerRejected"),
    @JsonSubTypes.Type(value=BusinessEvent.UnknownOutcome.class,name="UnknownOutcome"),
    @JsonSubTypes.Type(value=BusinessEvent.Quarantined.class,name="Quarantined")
})
public sealed interface BusinessEvent {
    record ParentAccepted(ParentOrder parent) implements BusinessEvent {}
    record ChildCreated(ChildOrder child) implements BusinessEvent {}
    record Acknowledged(UUID childId, String brokerOrderId) implements BusinessEvent {}
    record FillReceived(Fill fill) implements BusinessEvent {}
    record CancelRequested(UUID childId) implements BusinessEvent {}
    record CancelConfirmed(UUID childId, long canceledShares) implements BusinessEvent {}
    record CancelRejected(UUID childId) implements BusinessEvent {}
    record ReplaceRequested(UUID childId, Replacement replacement) implements BusinessEvent {}
    record ReplaceConfirmed(UUID childId) implements BusinessEvent {}
    record ReplaceRejected(UUID childId) implements BusinessEvent {}
    record Expired(UUID childId) implements BusinessEvent {}
    record Suspended(UUID childId) implements BusinessEvent {}
    record Resumed(UUID childId) implements BusinessEvent {}
    record ReconciliationRequired(UUID childId, String reason) implements BusinessEvent {}
    record BrokerRejected(UUID childId, String reason) implements BusinessEvent {}
    record UnknownOutcome(UUID childId) implements BusinessEvent {}
    record Quarantined(String reason, String source) implements BusinessEvent {}
}
