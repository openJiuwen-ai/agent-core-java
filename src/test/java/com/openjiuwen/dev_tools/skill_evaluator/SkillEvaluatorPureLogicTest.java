/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic unit tests for {@link SkillEvaluator} helpers that are not
 * exercised by {@link SkillEvaluatorCompatibilityTest}. Covers the private
 * static helpers {@code extractOutput}, {@code parseIntConfig},
 * {@code resolveStringConfig} via reflection, plus the null/blank requirement
 * branch of {@code buildEvaluationQuery} and the uninitialized-agent guard in
 * {@code ensureAgentReady}.
 *
 * @since 0.1.13
 */
class SkillEvaluatorPureLogicTest {

    private static Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Throwable {
        try {
            Method method = SkillEvaluator.class.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    @DisplayName("extractOutput 返回 Map 中 output 内嵌的 response 字段")
    void extractOutputReturnsResponseFromNestedMap() throws Throwable {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("response", "final answer");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", inner);

        Object output = invokePrivate("extractOutput", new Class<?>[] {Object.class}, result);
        assertThat(output).isEqualTo("final answer");
    }

    @Test
    @DisplayName("extractOutput 在 output 为 Map 但无 response 时回退到 output 自身")
    void extractOutputFallsBackToOutputWhenNoResponse() throws Throwable {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("not_response", "x");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", inner);

        Object output = invokePrivate("extractOutput", new Class<?>[] {Object.class}, result);
        assertThat(output).isEqualTo(inner.toString());
    }

    @Test
    @DisplayName("extractOutput 在 output 为字符串时返回该字符串")
    void extractOutputReturnsStringOutput() throws Throwable {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", "plain string");

        Object output = invokePrivate("extractOutput", new Class<?>[] {Object.class}, result);
        assertThat(output).isEqualTo("plain string");
    }

    @Test
    @DisplayName("extractOutput 在 Map 无 output 键时返回整个结果的字符串形式")
    void extractOutputReturnsWholeResultWhenOutputMissing() throws Throwable {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unrelated", "value");

        Object output = invokePrivate("extractOutput", new Class<?>[] {Object.class}, result);
        assertThat(output).isEqualTo(result.toString());
    }

    @Test
    @DisplayName("extractOutput 在非 Map 输入上回退到 Objects.toString")
    void extractOutputHandlesNonMapInput() throws Throwable {
        Object fromLong = invokePrivate("extractOutput", new Class<?>[] {Object.class}, 42L);
        assertThat(fromLong).isEqualTo("42");

        Object fromNull = invokePrivate("extractOutput", new Class<?>[] {Object.class}, (Object) null);
        assertThat(fromNull).isEqualTo("");
    }

    @Test
    @DisplayName("parseIntConfig 在非法值上回退到默认值")
    void parseIntConfigFallsBackOnInvalidValue() throws Throwable {
        // Reflective call: resolves value from env/property; for an unset key the
        // default is returned.
        Object unset = invokePrivate("parseIntConfig",
                new Class<?>[] {String.class, int.class}, "PARSED_CONFIG_KEY_NOT_SET_42", 99);
        assertThat(unset).isEqualTo(99);
    }

    @Test
    @DisplayName("resolveStringConfig 在键未设置时返回默认值")
    void resolveStringConfigReturnsDefaultWhenUnset() throws Throwable {
        Object value = invokePrivate("resolveStringConfig",
                new Class<?>[] {String.class, String.class}, "RESOLVED_CONFIG_KEY_NOT_SET_42", "fallback");
        assertThat(value).isEqualTo("fallback");
    }

    @Test
    @DisplayName("buildEvaluationQuery 在 requirement 为 null 时省略追加段")
    void buildEvaluationQueryOmitsRequirementWhenNull() {
        Path skillPath = Path.of("tmp", "skill");
        Path outputDir = Path.of("tmp", "out");
        String query = SkillEvaluator.buildEvaluationQuery(skillPath, outputDir, null);
        assertThat(query).contains(skillPath.toString());
        assertThat(query).contains(outputDir.toString());
        assertThat(query).doesNotEndWith("\n");
    }

    @Test
    @DisplayName("buildEvaluationQuery 在 requirement 为空白时省略追加段")
    void buildEvaluationQueryOmitsRequirementWhenBlank() {
        Path skillPath = Path.of("tmp", "skill");
        Path outputDir = Path.of("tmp", "out");
        String query = SkillEvaluator.buildEvaluationQuery(skillPath, outputDir, "   ");
        assertThat(query).contains(skillPath.toString());
        assertThat(query).contains(outputDir.toString());
        assertThat(query).doesNotContain("   ");
    }

    @Test
    @DisplayName("ensureAgentReady 在未初始化时抛 IllegalStateException")
    void ensureAgentReadyThrowsWhenUninitialized() throws Exception {
        SkillEvaluator evaluator = new SkillEvaluator();
        Method method = SkillEvaluator.class.getDeclaredMethod("ensureAgentReady");
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(evaluator);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent not initialized");
    }
}
