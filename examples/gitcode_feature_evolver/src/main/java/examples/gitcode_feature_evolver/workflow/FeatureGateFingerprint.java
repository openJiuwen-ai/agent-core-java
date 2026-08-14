/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/** Computes an immutable approved-gate fingerprint from trusted identity and actual files. */
final class FeatureGateFingerprint {
    private FeatureGateFingerprint() {
    }

    static String compute(Path worktree, String head, List<String> changedPaths,
                          GateIdentity identity) {
        Path root = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        MessageDigest digest = sha256();
        update(digest, "head=" + value(head));
        update(digest, "stage=" + identity.stage());
        update(digest, "profile=" + identity.profile());
        update(digest, "selectors=" + String.join(",", identity.selectors()));
        update(digest, "image=" + identity.imageDigest());
        update(digest, "source=" + identity.sourceRevision());
        List<String> normalized = changedPaths.stream().map(FeaturePathPolicy::normalize)
                .distinct().sorted().toList();
        for (String relative : normalized) {
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || Files.isSymbolicLink(file)) {
                throw new IllegalArgumentException("Gate fingerprint contains an unsafe path");
            }
            update(digest, "path=" + relative);
            hashFile(digest, file);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static void hashFile(MessageDigest digest, Path file) {
        if (!Files.exists(file)) {
            update(digest, "deleted");
            return;
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Gate fingerprint path is not a regular file");
        }
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to fingerprint approved Gate input", ex);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    /** Immutable Controller-selected Gate identity. */
    record GateIdentity(String stage, String profile, List<String> selectors,
                        String imageDigest, String sourceRevision) {
        GateIdentity {
            selectors = selectors == null ? List.of() : List.copyOf(selectors);
            imageDigest = value(imageDigest);
            sourceRevision = value(sourceRevision);
        }
    }
}
