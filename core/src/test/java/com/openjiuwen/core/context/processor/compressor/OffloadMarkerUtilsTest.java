/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link OffloadMarkerUtils} unit tests: marker extraction in both forms,
 * protocol A lost-handle detection and deterministic appendix repair
 * (v2 §7.10 C1 / 协议 A, AC-C1-01) plus the marker format drift guard.
 */
class OffloadMarkerUtilsTest {
    private static final String HANDLE_A = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final String HANDLE_B = "0f9e8d7c6b5a49382716050413211234";
    private static final String MARKER_WITH_PATH =
            "[[OFFLOAD: handle=" + HANDLE_A + ", type=filesystem, path=/ws/context/offload/" + HANDLE_A + ".json]]";
    private static final String MARKER_NO_PATH = "[[OFFLOAD: handle=" + HANDLE_B + ", type=in_memory]]";

    private static BaseMessage userMessage(String content) {
        return new UserMessage(content);
    }

    @Test
    void extractHandlesReadsBothMarkerForms() {
        assertThat(OffloadMarkerUtils.extractHandles("before " + MARKER_WITH_PATH + " after"))
                .containsExactly(HANDLE_A);
        assertThat(OffloadMarkerUtils.extractHandles("before " + MARKER_NO_PATH + " after"))
                .containsExactly(HANDLE_B);
        assertThat(OffloadMarkerUtils.extractHandles(MARKER_WITH_PATH + "\n" + MARKER_NO_PATH))
                .containsExactly(HANDLE_A, HANDLE_B);
    }

    @Test
    void extractHandlesCollapsesDuplicatesDeterministically() {
        String text = MARKER_NO_PATH + " repeat " + MARKER_NO_PATH + " then " + MARKER_WITH_PATH;
        // The extractor scans the with-path pattern first and the no-path
        // pattern second; within each pass duplicates collapse preserving
        // first-appearance order, so the result is deterministic.
        assertThat(OffloadMarkerUtils.extractHandles(text))
                .containsExactly(HANDLE_A, HANDLE_B);
        assertThat(OffloadMarkerUtils.extractHandles(text))
                .containsOnlyOnce(HANDLE_A, HANDLE_B);
    }

    @Test
    void malformedFragmentsAreIgnored() {
        assertThat(OffloadMarkerUtils.extractHandles("[[OFFLOAD: handle=short, type=filesystem]]")).isEmpty();
        assertThat(OffloadMarkerUtils.extractHandles("[[OFFLOAD: handle=]]")).isEmpty();
        assertThat(OffloadMarkerUtils.extractHandles("[OFFLOAD: no double brackets]")).isEmpty();
        assertThat(OffloadMarkerUtils.extractHandles("")).isEmpty();
        assertThat(OffloadMarkerUtils.extractHandles((String) null)).isEmpty();
    }

    @Test
    void frameworkAndLikeAdaptersSeeTheSameHandles() {
        List<BaseMessage> framework = List.of(
                userMessage("block summary"),
                new ToolMessage("tool result " + MARKER_WITH_PATH, "tc-1"));
        assertThat(OffloadMarkerUtils.extractHandlesFromFramework(framework)).containsExactly(HANDLE_A);

        List<OffloadMarkerUtils.BaseMessageLike> adapted = framework.stream()
                .map(OffloadMarkerUtils::asLike)
                .flatMap(Optional::stream)
                .toList();
        assertThat(OffloadMarkerUtils.extractHandles(adapted)).containsExactly(HANDLE_A);
    }

    @Test
    void containsOffloadMarkerDetectsAndToleratesNull() {
        assertThat(OffloadMarkerUtils.containsOffloadMarker(OffloadMarkerUtils.asLike(
                userMessage("carry " + MARKER_NO_PATH)).orElse(null))).isTrue();
        assertThat(OffloadMarkerUtils.containsOffloadMarker(OffloadMarkerUtils.asLike(
                userMessage("no marker here")).orElse(null))).isFalse();
        assertThat(OffloadMarkerUtils.containsOffloadMarker(null)).isFalse();
    }

    @Test
    void findLostHandlesReportsOnlyMissingOnes() {
        // AC-C1-01: every handle of the compressed block must survive into
        // the summary; partial loss is reported, full survival is safe.
        List<BaseMessage> block = List.of(
                userMessage("first " + MARKER_WITH_PATH),
                userMessage("second " + MARKER_NO_PATH));

        assertThat(OffloadMarkerUtils.findLostHandles(
                block.stream().map(OffloadMarkerUtils::asLike).flatMap(Optional::stream).toList(),
                "summary kept " + MARKER_WITH_PATH)).containsExactly(HANDLE_B);
        assertThat(OffloadMarkerUtils.findLostHandles(
                block.stream().map(OffloadMarkerUtils::asLike).flatMap(Optional::stream).toList(),
                "summary kept " + MARKER_WITH_PATH + " and " + MARKER_NO_PATH)).isEmpty();
        assertThat(OffloadMarkerUtils.findLostHandles(
                block.stream().map(OffloadMarkerUtils::asLike).flatMap(Optional::stream).toList(),
                "summary lost everything")).containsExactly(HANDLE_A, HANDLE_B);
    }

    @Test
    void findLostHandlesIsSafeWhenBlockHasNoMarkers() {
        assertThat(OffloadMarkerUtils.findLostHandles(
                List.of(OffloadMarkerUtils.asLike(userMessage("plain block")).orElse(null)), "any summary")).isEmpty();
        assertThat(OffloadMarkerUtils.findLostHandles(List.of(), "any summary")).isEmpty();
    }

    @Test
    void markerAppendixRepairsEveryLostHandle() {
        List<OffloadMarkerUtils.BaseMessageLike> block = List.of(
                OffloadMarkerUtils.asLike(userMessage("first " + MARKER_WITH_PATH)).orElse(null),
                OffloadMarkerUtils.asLike(userMessage("second " + MARKER_NO_PATH)).orElse(null));

        String appendix = OffloadMarkerUtils.markerAppendix(block);

        assertThat(appendix).startsWith("\n[OFFLOAD MARKERS]");
        // The appendix must be re-parseable so the repaired summary survives
        // a later compression round's verification.
        assertThat(OffloadMarkerUtils.extractHandles(appendix)).containsExactlyInAnyOrder(HANDLE_A, HANDLE_B);
    }

    @Test
    void markerAppendixIsEmptyWhenNoMarkers() {
        assertThat(OffloadMarkerUtils.markerAppendix(
                List.of(OffloadMarkerUtils.asLike(userMessage("plain")).orElse(null)))).isEmpty();
        assertThat(OffloadMarkerUtils.markerAppendix(List.of())).isEmpty();
    }

    @Test
    void markerFormatGuardHolds() {
        // Format drift guard: the canonical three-segment marker must keep
        // matching the published regex (v2 §5.2 marker 格式).
        assertThat(OffloadMarkerUtils.markerFormatConsistent()).isTrue();
    }

    @Test
    void extractHandlesOnNullMessageListAndNullEntries() {
        assertThat(OffloadMarkerUtils.extractHandles((List<OffloadMarkerUtils.BaseMessageLike>) null)).isEmpty();
        assertThat(OffloadMarkerUtils.extractHandlesFromFramework(null)).isEqualTo(Set.of());
        assertThat(OffloadMarkerUtils.extractHandlesFromFramework(List.of(new BaseMessage("user", null))))
                .isEmpty();
    }
}
