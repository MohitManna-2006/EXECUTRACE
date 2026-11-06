package io.executrace.core;
public record CommandReceipt(long offset, boolean duplicateRequest, boolean accepted, String reason,
                             String projectionDigestSha256) {}
