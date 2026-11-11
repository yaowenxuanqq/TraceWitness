package com.tracewitness.platform.seed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Seeds {
    private Seeds() {
    }

    public static long subsystem(long masterSeed, String subsystem) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((masterSeed + ":" + subsystem).getBytes(StandardCharsets.UTF_8));
            int bits = ((hash[0] & 0xff) << 24)
                    | ((hash[1] & 0xff) << 16)
                    | ((hash[2] & 0xff) << 8)
                    | (hash[3] & 0xff);
            return Integer.toUnsignedLong(bits);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
