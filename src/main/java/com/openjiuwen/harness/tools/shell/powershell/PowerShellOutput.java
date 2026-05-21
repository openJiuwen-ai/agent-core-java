/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Smart output truncation and large-output persistence.
 *
 * <p>Mirrors Python's {@code _output.py} in
 * {@code openjiuwen.harness.tools.shell.powershell}.
 */
public final class PowerShellOutput {

    private PowerShellOutput() {
    }

    private static final Path OUTPUT_DIR = Path.of(System.getProperty("java.io.tmpdir"), "openjiuwen_powershell_outputs");

    public static String truncateOutput(String text, int maxChars, double headRatio) {
        if (text == null) {
            return null;
        }
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

    public static String truncateOutput(String text, int maxChars) {
        return truncateOutput(text, maxChars, 0.8);
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    public static PersistResult persistLargeOutput(String stdout, String stderr) {
        String combined = stdout != null ? stdout : "";
        if (stderr != null && !stderr.isEmpty()) {
            combined += "\n--- stderr ---\n" + stderr;
        }

        byte[] contentBytes = combined.getBytes(StandardCharsets.UTF_8);
        String digest = sha256Digest(contentBytes).substring(0, 12);

        try {
            Files.createDirectories(OUTPUT_DIR);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create output directory: " + OUTPUT_DIR, e);
        }

        Path filePath = OUTPUT_DIR.resolve("powershell_" + digest + ".txt");

        if (!Files.exists(filePath)) {
            try {
                Files.write(filePath, contentBytes);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write output file: " + filePath, e);
            }
        }

        return new PersistResult(filePath.toString(), contentBytes.length);
    }

    private static String sha256Digest(byte[] data) {
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
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static final class PersistResult {
        private final String path;
        private final int byteCount;

        public PersistResult(String path, int byteCount) {
            this.path = path;
            this.byteCount = byteCount;
        }

        public String getPath() {
            return path;
        }

        public int getByteCount() {
            return byteCount;
        }
    }
}