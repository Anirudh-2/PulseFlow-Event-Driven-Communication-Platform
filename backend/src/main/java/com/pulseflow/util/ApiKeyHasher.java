package com.pulseflow.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ApiKeyHasher {
    private ApiKeyHasher() {}

    public static String sha256Hex(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String rawKey, String storedSha256Hex) {
        if (storedSha256Hex == null || storedSha256Hex.isBlank()) {
            return false;
        }
        if (rawKey == null) {
            return false;
        }
        String computed = sha256Hex(rawKey);
        byte[] a = computed.getBytes(StandardCharsets.UTF_8);
        byte[] b = storedSha256Hex.getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }
}
