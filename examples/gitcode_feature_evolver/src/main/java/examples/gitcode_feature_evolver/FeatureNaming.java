/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Stable branch, artifact, delivery, and digest naming shared by trigger channels.
 *
 * @since 0.1.12
 */
public final class FeatureNaming {
    private FeatureNaming() {
    }

    /** Build the stable long-lived branch for one admitted Issue. */
    public static String branch(long issueIid, String title) {
        return "feature-evolving/issue-" + issueIid + "-" + slug(title);
    }

    /** Build the stable post-merge branch in the configured system-test repository. */
    public static String systemTestBranch(long issueIid, String title) {
        return "feature-evolving/system-test-issue-" + issueIid + "-" + slug(title);
    }

    /** Build the DevFlow artifact root below the configured component. */
    public static String artifactRoot(String componentRoot, long issueIid, String title) {
        String prefix = ".".equals(componentRoot) ? "" : componentRoot + "/";
        return prefix + "features/" + issueIid + "-" + slug(title);
    }

    /** Build the post-merge evidence root in the system-test repository. */
    public static String systemTestArtifactRoot(long issueIid, String title) {
        return "features/" + issueIid + "-" + slug(title);
    }

    /** Compute a lowercase SHA-256 digest for delivery auditing. */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** Compute a lowercase SHA-256 digest for UTF-8 text. */
    public static String sha256(String text) {
        String value = text == null ? "" : text;
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String slug(String title) {
        String value = title == null ? "feature" : title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (value.isBlank()) {
            value = "feature";
        }
        return value.substring(0, Math.min(value.length(), 40));
    }
}
