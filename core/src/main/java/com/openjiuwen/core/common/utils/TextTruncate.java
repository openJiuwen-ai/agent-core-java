/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

/**
 * UTF-16-safe truncation helpers that never split surrogate pairs.
 *
 * <p>Java {@link String} indexes are UTF-16 code units. BMP-outside characters
 * (most emoji) occupy a high/low surrogate pair; slicing between them leaves a
 * lone surrogate that later UTF-8 encoding quietly turns into {@code '?'}.</p>
 *
 * @since 0.1.18
 */
public final class TextTruncate {
    private TextTruncate() {
    }

    /**
     * Floor an end index so it does not land inside a surrogate pair.
     *
     * @param text source text, must not be null
     * @param index proposed end index (exclusive)
     * @return safe end index in {@code [0, text.length()]}
     * @since 0.1.18
     */
    public static int floorToCodePointBoundary(String text, int index) {
        if (index <= 0) {
            return 0;
        }
        if (index >= text.length()) {
            return text.length();
        }
        if (Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) {
            return index - 1;
        }
        return index;
    }

    /**
     * Raise a start index so it does not begin on a lone low surrogate.
     *
     * @param text source text, must not be null
     * @param index proposed start index (inclusive)
     * @return safe start index in {@code [0, text.length()]}
     * @since 0.1.18
     */
    public static int ceilToCodePointBoundary(String text, int index) {
        if (index <= 0) {
            return 0;
        }
        if (index >= text.length()) {
            return text.length();
        }
        if (Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) {
            return index + 1;
        }
        return index;
    }

    /**
     * Prefix of at most {@code maxUnits} UTF-16 units, never splitting a pair.
     *
     * @param text source text
     * @param maxUnits maximum UTF-16 units
     * @return truncated prefix, or {@code text} when it already fits
     * @since 0.1.18
     */
    public static String safePrefix(String text, int maxUnits) {
        if (text == null) {
            return null;
        }
        if (maxUnits <= 0) {
            return "";
        }
        if (text.length() <= maxUnits) {
            return text;
        }
        return text.substring(0, floorToCodePointBoundary(text, maxUnits));
    }

    /**
     * Suffix of at most {@code maxUnits} UTF-16 units, never splitting a pair.
     *
     * @param text source text
     * @param maxUnits maximum UTF-16 units
     * @return truncated suffix, or {@code text} when it already fits
     * @since 0.1.18
     */
    public static String safeSuffix(String text, int maxUnits) {
        if (text == null) {
            return null;
        }
        if (maxUnits <= 0) {
            return "";
        }
        if (text.length() <= maxUnits) {
            return text;
        }
        int start = ceilToCodePointBoundary(text, text.length() - maxUnits);
        return text.substring(start);
    }

    /**
     * Slice {@code [start, end)} without splitting surrogate pairs at either edge.
     *
     * @param text source text
     * @param start inclusive start
     * @param end exclusive end
     * @return safe substring
     * @since 0.1.18
     */
    public static String safeSubstring(String text, int start, int end) {
        if (text == null) {
            return null;
        }
        int safeStart = ceilToCodePointBoundary(text, Math.max(0, start));
        int safeEnd = floorToCodePointBoundary(text, Math.min(text.length(), end));
        if (safeStart >= safeEnd) {
            return "";
        }
        return text.substring(safeStart, safeEnd);
    }
}
