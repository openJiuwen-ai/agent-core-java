/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifies GitCode's SHA-256 webhook signature over the unmodified request body.
 *
 * @since 0.1.12
 */
public final class GitCodeWebhookVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitCodeWebhookVerifier.class);

    private GitCodeWebhookVerifier() {
    }

    /**
     * Verify a GitCode HMAC signature in constant time.
     *
     * @param body unmodified HTTP request body
     * @param signatureHeader GitCode SHA-256 signature header
     * @param secret configured webhook secret
     * @return {@code true} only when the signature is valid
     */
    public static boolean verify(byte[] body, String signatureHeader, String secret) {
        if (body == null || blank(signatureHeader) || blank(secret)
                || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            Arrays.fill(secretBytes, (byte) 0);
            return false;
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] expected = hmac.doFinal(body);
            byte[] actual = HexFormat.of().parseHex(signatureHeader.substring("sha256=".length()));
            return MessageDigest.isEqual(expected, actual);
        } catch (GeneralSecurityException ex) {
            LOGGER.error("Unable to verify GitCode webhook signature", ex);
            return false;
        } catch (IllegalArgumentException ex) {
            return false;
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
        }
    }

    /**
     * Compute a stable delivery payload hash for durable deduplication records.
     *
     * @param body unmodified HTTP request body
     * @return lowercase SHA-256 digest
     */
    public static String sha256(byte[] body) {
        try {
            byte[] requiredBody = Objects.requireNonNull(body, "body must not be null");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(requiredBody));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
