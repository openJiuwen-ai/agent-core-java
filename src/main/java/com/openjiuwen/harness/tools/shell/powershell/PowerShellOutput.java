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
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Smart output truncation and large-output persistence.
 *
 * <p>Mirrors Python's {@code PowerShellOutput} in
 * {@code openjiuwen/harness/tools/shell/powershell/_output.py}.
 */
public final class PowerShellOutput {

    private static final Path OUTPUT_DIR = Path.of(System.getProperty("java.io.tmpdir"), "openjiuwen_powershell_outputs");
    private static final String PERSISTED_OUTPUT_TAG = "<persisted-output>";
    private static final String PERSISTED_OUTPUT_CLOSING_TAG = "</persisted-output>";
    private static final int PREVIEW_SIZE_BYTES = 2000;
    private static final Pattern LEADING_BLANK_LINES = Pattern.compile("^(\\s*\\n)+");

    private PowerShellOutput() {
    }

    public static String truncateOutput(String text, int maxChars, double headRatio) {
        if (text == null) {
            return null;
        }
        if (maxChars == 0 || text.length() <= maxChars) {
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

    public static PersistResult persistLargeOutput(String stdout, String stderr) {
        String combined = stdout == null ? "" : stdout;
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

    public static RenderedContent renderToolContent(CommandOutput output, boolean isError) {
        if (isError) {
            String merged = merge(output.stderr(), output.stdout());
            StringBuilder content = new StringBuilder("Exit code " + output.exitCode());
            if (!merged.isEmpty()) {
                content.append('\n').append(merged);
            }
            return new RenderedContent(prependWarning(content.toString(), output.warning()), true);
        }

        String merged = merge(output.stdout(), output.stderr());
        String processed = merged.isEmpty()
                ? merged
                : stripTrailingWhitespace(LEADING_BLANK_LINES.matcher(merged).replaceFirst(""));
        if (output.maxOutputChars() > 0 && merged.length() > output.maxOutputChars()) {
            PersistResult persisted = persistLargeOutput(output.stdout(), output.stderr());
            Preview preview = generatePreview(processed, PREVIEW_SIZE_BYTES);
            processed = buildPersistedMessage(persisted.path(), persisted.byteCount(), preview.content(), preview.hasMore());
        }
        return new RenderedContent(prependWarning(processed, output.warning()), false);
    }

    public static String renderPartialOnFailure(CommandOutput output, String failureMessage) {
        if ((output.stdout() == null || output.stdout().isEmpty())
                && (output.stderr() == null || output.stderr().isEmpty())) {
            return null;
        }
        RenderedContent content = renderToolContent(output, true);
        return content.content().isEmpty() ? failureMessage : failureMessage + "\n" + content.content();
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

    private static String buildPersistedMessage(String filepath, int originalSize, String preview, boolean hasMore) {
        StringBuilder builder = new StringBuilder();
        builder.append(PERSISTED_OUTPUT_TAG).append('\n');
        builder.append("Output too large (").append(formatFileSize(originalSize)).append("). Full output saved to: ")
                .append(filepath)
                .append("\n\n");
        builder.append("Preview (first ").append(formatFileSize(PREVIEW_SIZE_BYTES)).append("):\n");
        builder.append(preview);
        builder.append(hasMore ? "\n...\n" : "\n");
        builder.append(PERSISTED_OUTPUT_CLOSING_TAG);
        return builder.toString();
    }

    private static String prependWarning(String content, String warning) {
        if (warning == null || warning.isEmpty()) {
            return content;
        }
        if (content == null || content.isEmpty()) {
            return warning;
        }
        return warning + "\n" + content;
    }

    private static String merge(String first, String second) {
        String safeFirst = first == null ? "" : first;
        String safeSecond = second == null ? "" : second;
        if (safeFirst.isEmpty()) {
            return safeSecond;
        }
        if (safeSecond.isEmpty()) {
            return safeFirst;
        }
        return safeFirst + "\n" + safeSecond;
    }

    private static Preview generatePreview(String content, int maxBytes) {
        if (content.length() <= maxBytes) {
            return new Preview(content, false);
        }
        String truncated = content.substring(0, maxBytes);
        int lastNewline = truncated.lastIndexOf('\n');
        int cut = lastNewline > maxBytes * 0.5 ? lastNewline : maxBytes;
        return new Preview(content.substring(0, cut), true);
    }

    private static String formatFileSize(double sizeInBytes) {
        double kb = sizeInBytes / 1024.0;
        if (kb < 1) {
            if (Math.rint(sizeInBytes) == sizeInBytes) {
                return ((long) sizeInBytes) + " bytes";
            }
            return sizeInBytes + " bytes";
        }
        if (kb < 1024) {
            return stripTrailingZero(kb) + "KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return stripTrailingZero(mb) + "MB";
        }
        double gb = mb / 1024.0;
        return stripTrailingZero(gb) + "GB";
    }

    private static String stripTrailingWhitespace(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String stripTrailingZero(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
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

    private record Preview(String content, boolean hasMore) {
    }

    public record PersistResult(String path, int byteCount) {
    }

    /**
     * Bundled command execution result and rendering configuration.
     *
     * <p>Mirrors Python's {@code CommandOutput} in
     * {@code openjiuwen/harness/tools/shell/powershell/_output.py}.
     */
    public record CommandOutput(
            String stdout,
            String stderr,
            int exitCode,
            String warning,
            int maxOutputChars
    ) {
    }

    public record RenderedContent(String content, boolean isError) {
    }
}
