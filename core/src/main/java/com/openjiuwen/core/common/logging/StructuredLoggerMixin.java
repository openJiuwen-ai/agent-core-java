/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mixin-style utility for structured logging with auto-format detection.
 * <p>
 * Mirrors Python's {@code StructuredLoggerMixin} in
 * {@code openjiuwen/core/common/logging/base_impl.py}.
 * Automatically detects and applies either percent-style (%s, %d) or brace-style ({}, {0}) formatting.
 */
public final class StructuredLoggerMixin {

    private static final Pattern BRACE_PLACEHOLDER_PATTERN = Pattern.compile(
            "\\{(?:\\d*|[a-zA-Z_]\\w*)?(?:![rsa])?(?::[^}]*)?\\}");

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
            return String.valueOf(message);
        }

        String msg = String.valueOf(message);
        if (BRACE_PLACEHOLDER_PATTERN.matcher(msg).find()) {
            String formatted = formatBraceStyle(msg, args);
            if (formatted != null) {
                return formatted;
            }
        }

        try {
            return String.format(Locale.ROOT, msg, args);
        } catch (IllegalFormatException | NullPointerException exception) {
            return msg;
        }
    }

    private static String formatBraceStyle(String message, Object[] args) {
        StringBuilder result = new StringBuilder();
        int autoIndex = 0;
        int i = 0;
        while (i < message.length()) {
            char ch = message.charAt(i);
            if (ch == '{' && i + 1 < message.length() && message.charAt(i + 1) == '{') {
                result.append('{');
                i += 2;
                continue;
            }
            if (ch == '}' && i + 1 < message.length() && message.charAt(i + 1) == '}') {
                result.append('}');
                i += 2;
                continue;
            }
            if (ch != '{') {
                result.append(ch);
                i++;
                continue;
            }

            int end = message.indexOf('}', i + 1);
            if (end < 0) {
                return null;
            }
            String placeholder = message.substring(i + 1, end);
            ParsedPlaceholder parsed = parsePlaceholder(placeholder);
            if (parsed == null) {
                return null;
            }
            int index = parsed.index >= 0 ? parsed.index : autoIndex++;
            if (index >= args.length) {
                return null;
            }
            String formatted = formatBraceArgument(args[index], parsed);
            if (formatted == null) {
                return null;
            }
            result.append(formatted);
            i = end + 1;
        }
        return result.toString();
    }

    private static ParsedPlaceholder parsePlaceholder(String placeholder) {
        int conversionPos = placeholder.indexOf('!');
        int specPos = placeholder.indexOf(':');
        int fieldEnd = placeholder.length();
        if (conversionPos >= 0) {
            fieldEnd = Math.min(fieldEnd, conversionPos);
        }
        if (specPos >= 0) {
            fieldEnd = Math.min(fieldEnd, specPos);
        }

        String field = placeholder.substring(0, fieldEnd);
        int index = -1;
        if (!field.isEmpty()) {
            if (!field.chars().allMatch(Character::isDigit)) {
                return null;
            }
            index = Integer.parseInt(field);
        }

        Character conversion = null;
        if (conversionPos >= 0) {
            int conversionEnd = specPos >= 0 && specPos > conversionPos ? specPos : placeholder.length();
            if (conversionEnd != conversionPos + 2) {
                return null;
            }
            conversion = placeholder.charAt(conversionPos + 1);
            if (conversion != 'r' && conversion != 's' && conversion != 'a') {
                return null;
            }
        }

        String spec = specPos >= 0 ? placeholder.substring(specPos + 1) : "";
        return new ParsedPlaceholder(index, conversion, spec);
    }

    private static String formatBraceArgument(Object arg, ParsedPlaceholder placeholder) {
        Object value = arg;
        if (placeholder.conversion != null) {
            if (placeholder.conversion == 'r' || placeholder.conversion == 'a') {
                value = repr(arg);
            } else {
                value = pythonString(arg);
            }
        }
        if (!placeholder.formatSpec.isEmpty()) {
            try {
                return String.format(Locale.ROOT, "%" + placeholder.formatSpec, value);
            } catch (IllegalFormatException exception) {
                return null;
            }
        }
        return String.valueOf(value);
    }

    private static String pythonString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }

    private static String repr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String string) {
            return "'" + string.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        return String.valueOf(value);
    }

    private record ParsedPlaceholder(int index, Character conversion, String formatSpec) {
    }
}

