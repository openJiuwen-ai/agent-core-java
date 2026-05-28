/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared helpers for logging backend implementations.
 * <p>
 * Mirrors Python's {@code base_impl.py} module from
 * <code>openjiuwen/core/common/logging/base_impl.py</code>.
 */
public class BaseImpl {

    private static final Logger LOGGER = Logger.getLogger(BaseImpl.class.getName());

    /**
     * Map internal logger types to the label rendered in outputs.
     *
     * @param logType internal log type
     * @return rendered label
     */
    public static String resolveLogTypeLabel(String logType) {
        return "performance".equals(logType) ? "perf" : logType;
    }

    /**
     * Apply the configured filename pattern to a log file path.
     *
     * @param baseFilename base filename
     * @param pattern pattern to apply
     * @return formatted filename
     */
    public static String formatLogFilename(String baseFilename, String pattern) {
        String dirPath = "";
        String fileName = baseFilename;

        int lastSep = baseFilename.lastIndexOf('/');
        if (lastSep >= 0) {
            dirPath = baseFilename.substring(0, lastSep);
            fileName = baseFilename.substring(lastSep + 1);
        }

        String namePart = fileName;
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx >= 0) {
            namePart = fileName.substring(0, dotIdx);
            ext = fileName.substring(dotIdx);
        }

        ZonedDateTime now = ZonedDateTime.now();
        Map<String, String> replacements = new HashMap<>();
        replacements.put("name", namePart);
        replacements.put("ext", ext);
        replacements.put("pid", String.valueOf(ProcessHandle.current().pid()));
        replacements.put("timestamp", now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        replacements.put("date", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        replacements.put("time", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        replacements.put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));

        String formattedName = pattern;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            formattedName = formattedName.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        if (!pattern.contains("{ext}") && !ext.isEmpty() && !formattedName.endsWith(ext)) {
            formattedName = formattedName + ext;
        }

        if (!dirPath.isEmpty()) {
            return dirPath + "/" + formattedName;
        }
        return formattedName;
    }

    /**
     * Auto-detect placeholder style and format the message.
     * Supports both brace-style ({}) and percent-style (%s, %d).
     *
     * @param msg message template
     * @param args arguments
     * @return formatted message
     */
    public static String autoFormatMessage(String msg, Object... args) {
        if (args == null || args.length == 0) {
            return msg;
        }

        // Check for brace-style placeholders
        if (msg.contains("{}") || msg.matches(".\\{\\d+\\}.")) {
            return formatBraceStyle(msg, args);
        }

        // Check for percent-style placeholders
        if (msg.contains("%s") || msg.contains("%d") || msg.contains("%f")) {
            return formatPercentStyle(msg, args);
        }

        return msg;
    }

    private static String formatBraceStyle(String msg, Object[] args) {
        int argIndex = 0;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < msg.length()) {
            if (msg.charAt(i) == '{') {
                int end = msg.indexOf('}', i);
                if (end >= 0) {
                    String placeholder = msg.substring(i + 1, end);
                    if (placeholder.isEmpty() || placeholder.matches("\\d+")) {
                        int idx = placeholder.isEmpty() ? argIndex : Integer.parseInt(placeholder);
                        if (idx < args.length) {
                            result.append(args[idx]);
                            if (placeholder.isEmpty()) {
                                argIndex++;
                            }
                        }
                        i = end + 1;
                        continue;
                    }
                }
            }
            result.append(msg.charAt(i));
            i++;
        }
        return result.toString();
    }

    private static String formatPercentStyle(String msg, Object[] args) {
        // Simple percent-style formatting
        int argIndex = 0;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < msg.length()) {
            if (msg.charAt(i) == '%' && i + 1 < msg.length() && argIndex < args.length) {
                char format = msg.charAt(i + 1);
                if (format == 's' || format == 'd' || format == 'f') {
                    result.append(args[argIndex]);
                    argIndex++;
                    i += 2;
                    continue;
                }
            }
            result.append(msg.charAt(i));
            i++;
        }
        return result.toString();
    }

    /**
     * Escape control characters in log messages.
     *
     * @param text input text
     * @return escaped text
     */
    public static String escapeControlChars(String text) {
        return text
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\b", "\\b");
    }
}