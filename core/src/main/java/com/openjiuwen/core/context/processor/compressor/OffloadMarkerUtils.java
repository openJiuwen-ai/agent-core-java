/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offload marker awareness for compressors (架构文档 §7.10 C1, 协议 A).
 *
 * <p>Compression must never destroy the {@code [[OFFLOAD: ...]]} markers:
 * a marker is the only address a model has to reload offloaded original
 * content. When a compressed block contains offloaded messages the marker
 * handle must survive into the replacement summary (协议 A: keep + verify),
 * otherwise the entry becomes an unreachable orphan (C6).</p>
 *
 * <p>Utility is stateless and shared by {@link DialogueCompressor},
 * {@link FullCompactProcessor} and {@link RoundLevelCompressor}.</p>
 *
 * @since 2026-01-01
 */
public final class OffloadMarkerUtils {
    /** Unified marker, three-segment form: {@code [[OFFLOAD: handle=<hex32>, type=<token>, path=<path>]]}. */
    public static final Pattern OFFLOAD_MARKER_WITH_PATH =
            Pattern.compile("\\[\\[OFFLOAD:\\s*handle=([0-9a-fA-F]{32}),\\s*type=([a-z_]+),\\s*path=([^\\]]+)\\]\\]");

    /** Unified marker without path: {@code [[OFFLOAD: handle=<hex32>, type=<token>]]}. */
    public static final Pattern OFFLOAD_MARKER_NO_PATH =
            Pattern.compile("\\[\\[OFFLOAD:\\s*handle=([0-9a-fA-F]{32}),\\s*type=([a-z_]+)\\]\\]");

    private OffloadMarkerUtils() {
    }

    /**
     * Checks whether the given message carries at least one offload marker.
     *
     * @param message message to inspect (may be {@code null})
     * @return {@code true} when the message content carries at least one
     * offload marker (either marker form).
     */
    public static boolean containsOffloadMarker(BaseMessageLike message) {
        return message != null && !extractHandles(textOf(message)).isEmpty();
    }

    /**
     * Extracts all offload handles referenced by the given messages, in
     * first-appearance order, duplicates collapsed.
     *
     * @param messages context slice to scan (may be {@code null})
     * @return possibly-empty set of 32-char hex handles
     */
    public static Set<String> extractHandles(List<? extends BaseMessageLike> messages) {
        Set<String> handles = new LinkedHashSet<>();
        if (messages == null) {
            return handles;
        }
        for (BaseMessageLike message : messages) {
            handles.addAll(extractHandles(textOf(message)));
        }
        return handles;
    }

    /**
     * Extracts all offload handles referenced by framework messages, in
     * first-appearance order, duplicates collapsed.
     *
     * @param messages context slice to scan (may be {@code null})
     * @return possibly-empty set of 32-char hex handles
     */
    public static Set<String> extractHandlesFromFramework(
            List<com.openjiuwen.core.foundation.llm.schema.BaseMessage> messages) {
        if (messages == null) {
            return Set.of();
        }
        Set<String> handles = new LinkedHashSet<>();
        for (com.openjiuwen.core.foundation.llm.schema.BaseMessage message : messages) {
            if (message != null && message.getContent() != null) {
                handles.addAll(extractHandles(String.valueOf(message.getContent())));
            }
        }
        return handles;
    }

    /**
     * Extracts offload handles from raw text. Both marker forms are
     * recognized; malformed fragments are ignored.
     *
     * @param text raw text to scan (may be {@code null} or empty)
     * @return possibly-empty set of 32-char hex handles
     */
    public static Set<String> extractHandles(String text) {
        Set<String> handles = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return handles;
        }
        Matcher withPath = OFFLOAD_MARKER_WITH_PATH.matcher(text);
        while (withPath.find()) {
            handles.add(withPath.group(1));
        }
        Matcher noPath = OFFLOAD_MARKER_NO_PATH.matcher(text);
        while (noPath.find()) {
            handles.add(noPath.group(1));
        }
        return handles;
    }

    /**
     * Protocol A post-verification: checks that every handle present in the
     * original block also appears in the replacement summary text. Missing
     * handles mean the summary destroyed the reload address and the
     * replacement must be rejected or repaired by the caller.
     *
     * @param originalBlockMessages messages being compressed away
     * @param replacementSummaryText summary text that will replace them
     * @return list of handles lost by the replacement (empty = safe)
     */
    public static List<String> findLostHandles(List<? extends BaseMessageLike> originalBlockMessages,
                                               String replacementSummaryText) {
        Set<String> required = extractHandles(originalBlockMessages);
        if (required.isEmpty()) {
            return List.of();
        }
        Set<String> kept = extractHandles(replacementSummaryText);
        return required.stream().filter(handle -> !kept.contains(handle)).toList();
    }

    /**
     * Renders a marker-preserving appendix for a summary: one compact line
     * per handle so the LLM-composed summary can be repaired without a
     * second LLM call (协议 A 后处理).
     *
     * @param originalBlockMessages messages being compressed away (may be {@code null})
     * @return appendix text with one marker line per handle, or empty string
     *         when no handles are present
     */
    public static String markerAppendix(List<? extends BaseMessageLike> originalBlockMessages) {
        Set<String> handles = extractHandles(originalBlockMessages);
        if (handles.isEmpty()) {
            return "";
        }
        StringBuilder appendix = new StringBuilder("\n[OFFLOAD MARKERS]\n");
        for (String handle : handles) {
            appendix.append("[[OFFLOAD: handle=").append(handle).append(", type=filesystem]]\n");
        }
        return appendix.toString();
    }

    private static String textOf(BaseMessageLike message) {
        Object content = message == null ? null : message.rawContent();
        return content == null ? "" : String.valueOf(content);
    }

    /**
     * Structural minimal view of a message so the utility does not depend on
     * the concrete schema class hierarchy; adapters pass the real message.
     */
    public interface BaseMessageLike {
        /**
         * Returns the raw content of this message for marker scanning.
         *
         * @return raw content (may be {@code null} when no content is available)
         */
        Object rawContent();
    }

    /**
     * Adapts a framework {@code BaseMessage} to the structural view.
     *
     * @param message the framework message to adapt (may be {@code null})
     * @return an adapted {@link BaseMessageLike}, or empty when the message is {@code null}
     */
    public static Optional<BaseMessageLike> asLike(com.openjiuwen.core.foundation.llm.schema.BaseMessage message) {
        // empty = no raw content available, caller handles gracefully
        return message == null ? Optional.empty() : Optional.of(message::getContent);
    }

    /**
     * True when the sample three-segment marker still matches the regex (format drift guard).
     *
     * @return {@code true} when the sample marker matches the compiled regex
     */
    static boolean markerFormatConsistent() {
        String sample = String.format("[[OFFLOAD: handle=%s, type=%s, path=%s]]",
                "a".repeat(32), "filesystem", "/p");
        return OFFLOAD_MARKER_WITH_PATH.matcher(sample).matches();
    }
}
