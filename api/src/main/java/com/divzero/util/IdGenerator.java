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
        long randomA = Math.abs(RANDOM.nextLong());
        long randomB = Math.abs(RANDOM.nextLong());
        return "c" + Long.toString(timestamp, 36)
            + Long.toString(randomA, 36)
            + Long.toString(randomB, 36);
    }
}
