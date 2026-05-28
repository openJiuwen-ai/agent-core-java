/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for {@link FullCompactProcessor}.
 * <p>
 * Mirrors Python's {@code FullCompactProcessorConfig} from
 * {@code context_engine/processor/compressor/full_compact_processor.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullCompactProcessorConfig {

    public static final String DEFAULT_MARKER = "<full-compact-boundary>";
    public static final String DEFAULT_STATE_MARKER = "<full-compact-state>";
    public static final String DEFAULT_SYNTHETIC_USER_MARKER = "<full-compact-synthetic-user>";

    /** Trigger full compaction when the estimated context window exceeds this token count. */
    @Builder.Default
    private int triggerTotalTokens = 180000;

    /** Maximum token budget for the internal summary-generation prompt. */
    @Builder.Default
    private int compressionCallMaxTokens = 200000;

    /** Number of most-recent active messages preserved verbatim after full compaction. */
    @Builder.Default
    private int messagesToKeep = 10;

    /** Prefer committed session memory notes before falling back to LLM full compaction. */
    @Builder.Default
    private boolean sessionMemoryEnabled = true;

    /** When preserving recent tool results, also keep their matching assistant tool-call messages. */
    @Builder.Default
    private boolean keepToolMessagePairs = true;

    /** Maximum characters retained for each reinjected state snapshot. */
    @Builder.Default
    private int stateSnapshotMaxChars = 4000;

    /** Maximum number of recent skill-read rounds reinjected after full compaction. */
    @Builder.Default
    private int reinjectRecentSkills = 3;

    /** Tool names eligible for file-related state reinjection. */
    @Builder.Default
    private List<String> reinjectFileToolNames = List.of("read_file", "write_file", "edit_file", "glob", "grep");

    /** Tool names eligible for compact tool-result hints. */
    @Builder.Default
    private List<String> reinjectToolResultHintNames = List.of("read_file", "write_file", "edit_file", "glob", "grep");

    /** Boundary marker inserted between compacted and non-compacted content. */
    @Builder.Default
    private String marker = DEFAULT_MARKER;

    /** Marker for reinjected state blocks. */
    @Builder.Default
    private String stateMarker = DEFAULT_STATE_MARKER;

    /** Marker for synthetic user messages. */
    @Builder.Default
    private String syntheticUserMarker = DEFAULT_SYNTHETIC_USER_MARKER;

    /** Model request configuration for the summarizer. */
    private Object modelRequestConfig;

    /** Client configuration for the summarizer model. */
    private Object modelClientConfig;
}
