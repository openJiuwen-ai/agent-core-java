/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import java.util.Objects;

/**
 * Model streaming frame record tied to one inference span.
 *
 * <p>The WebUI appends frames per {@code traceId:spanId} key in monotonic
 * {@code sequence} order to render live text, reasoning and tool-call argument
 * deltas while the span is still running.</p>
 *
 * @param traceId lower-case hex trace id, 32 characters
 * @param spanId lower-case hex span id, 16 characters
 * @param subjectId execution subject id owning the frame, may be null
 * @param sequence monotonic frame sequence within the span
 * @param kind frame kind: {@code text-delta|reasoning-delta|tool-call-delta|usage}
 * @param timestampUnixNano frame timestamp in Unix nanoseconds
 * @param text accumulated or delta text payload for text/reasoning frames
 * @param toolCallId tool call id for tool-call-delta frames
 * @param toolName tool name for tool-call-delta frames
 * @param argumentsDelta JSON arguments delta for tool-call-delta frames
 * @since 0.1.18
 */
public record StreamFrameRecord(
        String traceId,
        String spanId,
        String subjectId,
        long sequence,
        String kind,
        long timestampUnixNano,
        String text,
        String toolCallId,
        String toolName,
        String argumentsDelta) {
    /** Frame kind carrying assistant text deltas. */
    public static final String KIND_TEXT_DELTA = "text-delta";

    /** Frame kind carrying reasoning deltas. */
    public static final String KIND_REASONING_DELTA = "reasoning-delta";

    /** Frame kind carrying tool-call argument deltas. */
    public static final String KIND_TOOL_CALL_DELTA = "tool-call-delta";

    /** Frame kind carrying token usage snapshots. */
    public static final String KIND_USAGE = "usage";

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StreamFrameRecord that)) {
            return false;
        }
        return sequence == that.sequence
                && timestampUnixNano == that.timestampUnixNano
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(spanId, that.spanId)
                && Objects.equals(subjectId, that.subjectId)
                && Objects.equals(kind, that.kind)
                && Objects.equals(text, that.text)
                && Objects.equals(toolCallId, that.toolCallId)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(argumentsDelta, that.argumentsDelta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, subjectId, sequence, kind, timestampUnixNano,
                text, toolCallId, toolName, argumentsDelta);
    }
}
