/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Smart output truncation and large-output persistence utilities.
 *
 * <p>Mirrors Python's functions in
 * {@code openjiuwen.harness.tools.shell.bash._output}.
 */
public final class BashOutputUtils {

    private static final Path OUTPUT_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "openjiuwen_bash_outputs");

    private BashOutputUtils() {
    }

    /**
     * Truncate long output while preserving both the beginning and end.
     *
     * <p>When text fits within maxChars it is returned unchanged.
     * Otherwise the first ~80% of the budget keeps the head and the last ~20% keeps the tail.
     * A gap indicator line shows how many lines were omitted.
     *
     * @param text     Raw output text
     * @param maxChars Maximum character budget
     * @param headRatio Fraction of the budget allocated to the head
     * @return The original or truncated text
     */
    public static String truncateOutput(String text, int maxChars, double headRatio) {
        if (text.length() <= maxChars) {
            return text;
        }

        int headBudget = (int) (maxChars * headRatio);
        int tailBudget = maxChars - headBudget;

        String head = text.substring(0, headBudget);
        String tail = tailBudget > 0 ? text.substring(text.length() - tailBudget) : "";
        String omitted = tailBudget > 0
                ? text.substring(headBudget, text.length() - tailBudget)
                : text.substring(headBudget);
        int omittedLines = countLines(omitted);

        return head + "\n\n... [" + omittedLines + " lines omitted] ...\n\n" + tail;
    }

    /**
     * Truncate long output with default head ratio of 0.8.
     *
     * @param text     Raw output text
     * @param maxChars Maximum character budget
     * @return The original or truncated text
     */
    public static String truncateOutput(String text, int maxChars) {
        return truncateOutput(text, maxChars, 0.8);
    }

    private static int countLines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * Write raw command output to a temp file for later retrieval.
     *
     * <p>The file is named by a content hash so identical outputs reuse the same file.
     * The caller should include the returned path in the tool result.
     *
     * @param stdout Raw standard output
     * @param stderr Raw standard error
     * @return PersistResult containing file path and total bytes
     */
    public static PersistResult persistLargeOutput(String stdout, String stderr) {
        String combined = stdout;
        if (stderr != null && !stderr.isEmpty()) {
            combined += "\n--- stderr ---\n" + stderr;
        }

        byte[] contentBytes = combined.getBytes(StandardCharsets.UTF_8);
        String digest = sha256Hex(contentBytes).substring(0, 12);

        try {
            Files.createDirectories(OUTPUT_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + OUTPUT_DIR, e);
        }

        Path path = OUTPUT_DIR.resolve("bash_" + digest + ".txt");

        if (!Files.exists(path)) {
            try {
                Files.write(path, contentBytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write output file: " + path, e);
            }
        }

        return new PersistResult(path.toString(), contentBytes.length);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Result of persisting large output.
     */
    public static final class PersistResult {
        private final String filePath;
        private final int totalBytes;

        public PersistResult(String filePath, int totalBytes) {
            this.filePath = filePath;
            this.totalBytes = totalBytes;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getTotalBytes() {
            return totalBytes;
        }
    }
}