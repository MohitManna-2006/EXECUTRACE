package io.executrace.core;

public record OutboxEntry(long sourceOffset, String commandType, String clientOrderId, String payloadJson,
                          String state) {}
