/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.memory_call.MemoryCallOperator;
import com.openjiuwen.core.operator.llm_call.LLMCall;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.skill_call.SkillCallOperator;
import com.openjiuwen.core.operator.skill_call.SkillExperienceOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the operator package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.operator} package facade in
 * {@code openjiuwen/core/operator/__init__.py}.</p>
 */
class OperatorPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        List<String> expected = List.of(
                "Operator",
                "PreviewableOperator",
                "TunableSpec",
                "LLMCallOperator",
                "LLMCall",
                "ToolCallOperator",
                "MemoryCallOperator",
                "SkillExperienceOperator",
                "SkillCallOperator"
        );

        assertEquals("openjiuwen/core/operator/__init__.py", OperatorPackage.PYTHON_MODULE);
        assertEquals("Operator abstraction for atomic execution and optimization.", OperatorPackage.DESCRIPTION);
        assertEquals(expected, OperatorPackage.EXPORTED_SYMBOLS);
        assertSame(OperatorPackage.EXPORTED_SYMBOLS, OperatorPackage.all());
        assertEquals(expected, new ArrayList<>(OperatorPackage.EXPORT_SOURCES.keySet()));
        assertEquals(expected, new ArrayList<>(OperatorPackage.JAVA_TYPE_NAMES.keySet()));
    }

    @Test
    void exportMetadataMatchesPythonImports() {
        assertEquals("openjiuwen.core.operator.base.Operator", OperatorPackage.sourceFor("Operator"));
        assertEquals(
                "openjiuwen.core.operator.base.PreviewableOperator",
                OperatorPackage.sourceFor("PreviewableOperator")
        );
        assertEquals("openjiuwen.core.operator.base.TunableSpec", OperatorPackage.sourceFor("TunableSpec"));
        assertEquals(
                "openjiuwen.core.operator.llm_call.LLMCallOperator",
                OperatorPackage.sourceFor("LLMCallOperator")
        );
        assertEquals("openjiuwen.core.operator.llm_call.LLMCall", OperatorPackage.sourceFor("LLMCall"));
        assertEquals(
                "openjiuwen.core.operator.tool_call.ToolCallOperator",
                OperatorPackage.sourceFor("ToolCallOperator")
        );
        assertEquals(
                "openjiuwen.core.operator.memory_call.MemoryCallOperator",
                OperatorPackage.sourceFor("MemoryCallOperator")
        );
        assertEquals(
                "openjiuwen.core.operator.skill_call.SkillExperienceOperator",
                OperatorPackage.sourceFor("SkillExperienceOperator")
        );
        assertEquals(
                "openjiuwen.core.operator.skill_call.SkillCallOperator",
                OperatorPackage.sourceFor("SkillCallOperator")
        );

        assertEquals("com.openjiuwen.core.operator.Operator", OperatorPackage.javaTypeNameFor("Operator"));
        assertEquals(
                "com.openjiuwen.core.operator.PreviewableOperator",
                OperatorPackage.javaTypeNameFor("PreviewableOperator")
        );
        assertEquals("com.openjiuwen.core.operator.TunableSpec", OperatorPackage.javaTypeNameFor("TunableSpec"));
        assertEquals(
                "com.openjiuwen.core.operator.llm_call.LLMCallOperator",
                OperatorPackage.javaTypeNameFor("LLMCallOperator")
        );
        assertEquals(
                "com.openjiuwen.core.operator.llm_call.LLMCall",
                OperatorPackage.javaTypeNameFor("LLMCall")
        );
    }

    @Test
    void resolvedTypesTrackMergedDependencies() {
        assertEquals(Operator.class, OperatorPackage.resolveType("Operator").orElseThrow());
        assertEquals(PreviewableOperator.class, OperatorPackage.resolveType("PreviewableOperator").orElseThrow());
        assertEquals(TunableSpec.class, OperatorPackage.resolveType("TunableSpec").orElseThrow());
        assertEquals(LLMCallOperator.class, OperatorPackage.resolveType("LLMCallOperator").orElseThrow());
        assertEquals(LLMCall.class, OperatorPackage.resolveType("LLMCall").orElseThrow());
        assertEquals(ToolCallOperator.class, OperatorPackage.resolveType("ToolCallOperator").orElseThrow());
        assertEquals(MemoryCallOperator.class, OperatorPackage.resolveType("MemoryCallOperator").orElseThrow());
        assertEquals(
                SkillExperienceOperator.class,
                OperatorPackage.resolveType("SkillExperienceOperator").orElseThrow()
        );
        assertEquals(SkillCallOperator.class, OperatorPackage.resolveType("SkillCallOperator").orElseThrow());
    }

    @Test
    void unknownSymbolIsNotExported() {
        assertTrue(OperatorPackage.exports("Operator"));
        assertFalse(OperatorPackage.exports("MissingOperator"));
        assertNull(OperatorPackage.sourceFor("MissingOperator"));
        assertNull(OperatorPackage.javaTypeNameFor("MissingOperator"));
        assertTrue(OperatorPackage.resolveType("MissingOperator").isEmpty());
    }

    @Test
    void exportedCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> OperatorPackage.EXPORTED_SYMBOLS.add("Unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> OperatorPackage.EXPORT_SOURCES.put("Unexpected", "unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> OperatorPackage.JAVA_TYPE_NAMES.put("Unexpected", "Unexpected")
        );
    }
}
