package io.executrace.core;
public record FillKey(String brokerSessionId, String executionId) {
    public FillKey { Checks.text(brokerSessionId, "brokerSessionId", 64); Checks.text(executionId, "executionId", 128); }
}
