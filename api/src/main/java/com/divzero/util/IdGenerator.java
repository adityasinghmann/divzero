package com.divzero.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String cuid() {
        long timestamp = System.currentTimeMillis();
        // Unsigned formatting: Math.abs(Long.MIN_VALUE) is still negative and would put a "-" in the id.
        return "c" + Long.toString(timestamp, 36)
            + Long.toUnsignedString(RANDOM.nextLong(), 36)
            + Long.toUnsignedString(RANDOM.nextLong(), 36);
    }
}
