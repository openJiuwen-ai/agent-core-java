/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.system_tests.memory;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestCodingMemoryRailE2E} in
 * {@code tests/system_tests/harness/test_coding_memory_rail_e2e.py}.
 */
class CodingMemoryRailE2EPythonParityTest {

    @Test
    void testFullInvokeFlow() {
        CodingMemoryRail rail = initializedRail();
        CallbackContext beforeInvoke = ctx(
                "coding_memory_recall", "### Python Preference [python_pref.md]\n\n用户喜欢使用 Python 编程.",
                "coding_memory_count", 1);

        rail.beforeInvoke(beforeInvoke);
        CallbackContext beforeModelCall = ctx("language", "cn");
        rail.beforeModelCall(beforeModelCall);

        assertThat(rail.isManagerInitialized()).isTrue();
        assertThat(rail.getOwnedToolNames())
                .containsExactlyInAnyOrder("coding_memory_read", "coding_memory_write", "coding_memory_edit");
        assertThat(beforeModelCall.get("memory_section")).isInstanceOf(PromptSection.class);
        PromptSection section = (PromptSection) beforeModelCall.get("memory_section");
        assertThat(section.render("cn")).contains("已加载的相关记忆").contains("Python Preference");
    }

    @Test
    void testAutoRecallWithResults() {
        CodingMemoryRail rail = initializedRail();

        rail.beforeInvoke(ctx(
                "coding_memory_recall", "### Python Preference [python_pref.md]\n\n用户喜欢使用 Python 编程.",
                "coding_memory_count", 1));
        CallbackContext beforeModelCall = ctx("language", "cn");
        rail.beforeModelCall(beforeModelCall);

        assertThat(rail.getRecalledContent()).contains("Python Preference");
        assertThat(beforeModelCall.get("coding_memory_recalled_content")).asString().contains("Python Preference");
        assertThat(beforeModelCall.get("coding_memory_total")).isEqualTo(1);
    }

    @Test
    void testAutoRecallNoResults() {
        CodingMemoryRail rail = initializedRail();

        rail.beforeInvoke(ctx());
        CallbackContext beforeModelCall = ctx("language", "cn");
        rail.beforeModelCall(beforeModelCall);

        assertThat(rail.getRecalledContent()).isNull();
        assertThat(beforeModelCall.get("coding_memory_recalled_content")).isNull();
        assertThat(beforeModelCall.get("coding_memory_total")).isEqualTo(0);
    }

    @Test
    void testBeforeModelCallWithRecallResults() {
        CodingMemoryRail rail = initializedRail();
        rail.beforeInvoke(ctx(
                "coding_memory_recall", "### 测试记忆\n\n测试内容",
                "coding_memory_count", 5));

        CallbackContext beforeModelCall = ctx("language", "cn");
        rail.beforeModelCall(beforeModelCall);

        PromptSection section = (PromptSection) beforeModelCall.get("memory_section");
        assertThat(section.render("cn")).contains("已加载的相关记忆").contains("测试内容");
        assertThat(beforeModelCall.get("coding_memory_total")).isEqualTo(5);
    }

    @Test
    void testScenarioSwitching() {
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of("scenario", "personal")))).isEqualTo("personal");
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of("scenario", "coding")))).isEqualTo("coding");
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of()))).isEqualTo("personal");
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of("scenario", "CODING")))).isEqualTo("coding");
    }

    private static CodingMemoryRail initializedRail() {
        CodingMemoryRail rail = new CodingMemoryRail("coding_memory", new Object(), "cn");
        rail.init(new DeepAgent());
        return rail;
    }

    @SuppressWarnings("unchecked")
    private static String resolveMemoryScenario(Map<String, Object> config) {
        Map<String, Object> memory = (Map<String, Object>) config.getOrDefault("memory", Map.of());
        String scenario = String.valueOf(memory.getOrDefault("scenario", "personal")).strip().toLowerCase();
        return "coding".equals(scenario) ? "coding" : "personal";
    }

    private static CallbackContext ctx(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return new CallbackContext(new DeepAgent(), map);
    }
}
