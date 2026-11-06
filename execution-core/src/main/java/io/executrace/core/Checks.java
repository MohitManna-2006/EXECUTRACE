package io.executrace.core;

import java.util.Objects;
import java.util.UUID;

public final class Checks {
    private Checks() {}
    public static <T> T present(T value, String name) { return Objects.requireNonNull(value, name); }
    public static long positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
    public static long nonnegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }
    public static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(name + " length invalid");
        return value;
    }
    public static UUID id(UUID value, String name) { return present(value, name); }
}
