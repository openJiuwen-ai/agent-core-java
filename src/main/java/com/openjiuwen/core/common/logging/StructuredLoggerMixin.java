/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mixin-style utility for structured logging with auto-format detection.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.common.logging.structured_logger.StructuredLoggerMixin}.
 * Automatically detects and applies either percent-style (%s, %d) or brace-style ({}, {0}) formatting.
 */
public final class StructuredLoggerMixin {

    private static final Pattern PERCENT_PATTERN = Pattern.compile("%[sdf]");
    private static final Pattern BRACE_POSITIONAL_PATTERN = Pattern.compile("\\{}");
    private static final Pattern BRACE_INDEXED_PATTERN = Pattern.compile("\\{(\\d+)\\}");

    private StructuredLoggerMixin() {
        // Utility class - no instances
    }

    /**
     * Auto-format message based on placeholder style.
     * <p>
     * Mirrors Python's {@code _auto_format_message}.
     * Detects whether the message uses percent-style (%s, %d) or brace-style ({}, {0}) placeholders
     * and formats accordingly.
     *
     * @param message The message template
     * @param args The arguments to substitute
     * @return The formatted message
     */
    public static String autoFormatMessage(String message, Object[] args) {
        if (args == null || args.length == 0) {
            return message;
        }

        // Check for percent-style formatting
        Matcher percentMatcher = PERCENT_PATTERN.matcher(message);
        if (percentMatcher.find()) {
            return formatPercentStyle(message, args);
        }

        // Check for indexed brace style {0}, {1}
        Matcher indexedMatcher = BRACE_INDEXED_PATTERN.matcher(message);
        if (indexedMatcher.find()) {
            return formatIndexedBraceStyle(message, args);
        }

        // Check for positional brace style {}
        Matcher positionalMatcher = BRACE_POSITIONAL_PATTERN.matcher(message);
        if (positionalMatcher.find()) {
            return formatPositionalBraceStyle(message, args);
        }

        // No placeholders found, return original message
        return message;
    }

    private static String formatPercentStyle(String message, Object[] args) {
        // Simple percent-style formatting
        Object[] convertedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            convertedArgs[i] = args[i];
        }
        // Use String.format pattern matching
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            if (i < message.length() - 1 && message.charAt(i) == '%') {
                char next = message.charAt(i + 1);
                if (next == 's' || next == 'd' || next == 'f') {
                    if (argIndex < args.length) {
                        result.append(args[argIndex]);
                        argIndex++;
                        i += 2;
                        continue;
                    }
                }
            }
            result.append(message.charAt(i));
            i++;
        }
        return result.toString();
    }

    private static String formatIndexedBraceStyle(String message, Object[] args) {
        // Indexed brace style {0}, {1}, etc.
        Pattern pattern = Pattern.compile("\\{(\\d+)\\}");
        Matcher matcher = pattern.matcher(message);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < args.length) {
                matcher.appendReplacement(result, String.valueOf(args[index]));
            } else {
                matcher.appendReplacement(result, matcher.group());
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String formatPositionalBraceStyle(String message, Object[] args) {
        // Positional brace style {} - replace sequentially
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            if (i < message.length() - 1 && message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    result.append(args[argIndex]);
                    argIndex++;
                    i += 2;
                    continue;
                }
            }
            result.append(message.charAt(i));
            i++;
        }
        return result.toString();
    }
}