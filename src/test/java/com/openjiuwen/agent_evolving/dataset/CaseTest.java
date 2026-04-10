/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.dataset;

import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaseTest {

    @Test
    void caseSupportsMinimalCreationAndGeneratedCaseId() {
        Case caseData = new Case(Map.of("query", "test"), Map.of("answer", "expected"));

        assertEquals(Map.of("query", "test"), caseData.getInputs());
        assertEquals(Map.of("answer", "expected"), caseData.getLabel());
        assertNotNull(caseData.getCaseId());
        assertEquals(32, caseData.getCaseId().length());
    }

    @Test
    void caseSupportsCustomCaseId() {
        Case caseData = new Case(Map.of("query", "test"), Map.of("answer", "expected"), "custom_id");

        assertEquals("custom_id", caseData.getCaseId());
    }

    @Test
    void caseRejectsEmptyInputsOrLabels() {
        assertThrows(ValidationError.class, () -> new Case(Map.of(), Map.of("answer", "expected")));
        assertThrows(ValidationError.class, () -> new Case(Map.of("query", "test"), Map.of()));
    }

    @Test
    void evaluatedCaseClampsScoreAndDelegatesProperties() {
        Case caseData = new Case(Map.of("query", "test"), Map.of("answer", "expected"), "case_1");
        EvaluatedCase evaluated = EvaluatedCase.builder()
                .caseData(caseData)
                .score(1.5)
                .reason("good")
                .build();

        assertEquals(1.0, evaluated.getScore());
        assertEquals(Map.of("query", "test"), evaluated.getInputs());
        assertEquals(Map.of("answer", "expected"), evaluated.getLabel());
        assertEquals("case_1", evaluated.getCaseId());
        assertEquals("good", evaluated.getReason());
    }
}
