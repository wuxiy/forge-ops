package com.forgeops.spring.boot.tracing;

import java.security.SecureRandom;

/** Minimal ULID generator (Crockford base32, 26 chars, sortable) - 与前端 SDK 格式一致。 */
public final class Ulid {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String next() {
        long time = System.currentTimeMillis();
        char[] chars = new char[26];
        for (int i = 9; i >= 0; i--) {
            chars[i] = ENCODING[(int) (time & 0x1F)];
            time >>>= 5;
        }
        byte[] entropy = new byte[10];
        RANDOM.nextBytes(entropy);
        int index = 10;
        long value = 0;
        int bits = 0;
        for (byte b : entropy) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5 && index < 26) {
                chars[index++] = ENCODING[(int) (value >>> (bits - 5)) & 0x1F];
                bits -= 5;
            }
        }
        return new String(chars);
    }
}
