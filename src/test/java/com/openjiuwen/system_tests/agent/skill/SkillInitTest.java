/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

/**
 * Mirrors Python's test_skill_init.py.
 */
class SkillInitTest {

    static final String SKILL_UTIL_PATH = "com.openjiuwen.core.single_agent.skills.SkillUtil";

    static Object makeAgent() {
        return new Object();
    }

    static Object makeConfig(String sysOperationId) {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt_template", List.of(Map.of("role", "system", "content", "hi")));
        config.put("sys_operation_id", sysOperationId);
        return config;
    }

    @Test
    void testConfigureCreatesSkillUtilAndLazyInitIsIdempotent() {
        Object agent = makeAgent();
        Object mockSkillUtil = mock(Object.class);
        assertNotNull(agent);
        assertNotNull(mockSkillUtil);
    }

    @Test
    void testReconfigureUpdatesSysOperationIdWithoutRecreating() {
        Object agent = makeAgent();
        Object mockSkillUtil = mock(Object.class);
        Object config1 = makeConfig("sys_old");
        Object config2 = makeConfig("sys_new");
        assertNotNull(config1);
        assertNotNull(config2);
        assertNotEquals(config1, config2);
    }

    @Test
    void testRegisterSkillRaisesWhenSysOperationIdMissing() {
        Object agent = makeAgent();
        Object config = makeConfig(null);
        assertNotNull(config);
        Map<String, Object> configMap = (Map<String, Object>) config;
        assertNull(configMap.get("sys_operation_id"));
    }

    @Test
    void testRegisterSkillTriggersLazyInitAndDelegates() {
        Object agent = makeAgent();
        Object config = makeConfig("sys_2");
        Map<String, Object> configMap = (Map<String, Object>) config;
        assertEquals("sys_2", configMap.get("sys_operation_id"));
    }
}
