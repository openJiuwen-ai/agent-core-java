/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.config;

import com.openjiuwen.core.session.constants.SessionConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests session config defaults and environment override behavior.
 *
 * <p>Mirrors Python's {@code Config}, {@code MetadataLike}, and env helpers in
 * {@code openjiuwen/core/session/config/base.py}.</p>
 */
class ConfigTest {

    @AfterEach
    void clearWorkflowSessionVars() {
        Config.WORKFLOW_SESSION_VARS.get().clear();
    }

    @Test
    void loadsBuiltinDefaults() {
        Config config = new Config();

        assertEquals(-1.0d, config.getEnv(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY));
        assertEquals(5.0d, config.getEnv(SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY));
        assertEquals(60.0d, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
        assertEquals(SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT,
                config.getEnv(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY));
        assertFalse((Boolean) config.getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY));
    }

    @Test
    void parsesWorkflowSessionVarOverridesByDeclaredType() {
        Config.WORKFLOW_SESSION_VARS.get().put(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "12.5");
        Config.WORKFLOW_SESSION_VARS.get().put(SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "7");
        Config.WORKFLOW_SESSION_VARS.get().put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "true");

        Config config = new Config();

        assertEquals(12.5d, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
        assertEquals(7, config.getEnv(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY));
        assertTrue((Boolean) config.getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY));
    }

    @Test
    void invalidWorkflowSessionVarOverridesKeepDefaults() {
        Config.WORKFLOW_SESSION_VARS.get().put(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "bad-float");
        Config.WORKFLOW_SESSION_VARS.get().put(SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "bad-int");
        Config.WORKFLOW_SESSION_VARS.get().put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "not-bool");

        Config config = new Config();

        assertEquals(60.0d, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
        assertEquals(SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT,
                config.getEnv(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY));
        assertFalse((Boolean) config.getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getEnvsReturnsDeepCopy() {
        Config config = new Config();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", "value");
        config.setEnvs(Map.of("nested", nested));

        Map<String, Object> envs = config.getEnvs();
        ((Map<String, Object>) envs.get("nested")).put("inner", "changed");

        assertEquals("value", ((Map<?, ?>) config.getEnv("nested")).get("inner"));
    }

    @Test
    void workflowAndAgentConfigAccessorsMatchPythonBehavior() {
        Config config = new Config();
        Object workflowConfig = new Object();
        Object agentConfig = new Object();

        config.addWorkflowConfig("workflow-a", workflowConfig);
        config.setAgentConfig(agentConfig);

        assertEquals(workflowConfig, config.getWorkflowConfig("workflow-a"));
        assertEquals(agentConfig, config.getAgentConfig());
        assertThrows(IllegalArgumentException.class, () -> config.getWorkflowConfig(null));
        assertThrows(IllegalArgumentException.class, () -> config.addWorkflowConfig(null, workflowConfig));
        assertThrows(IllegalArgumentException.class, () -> config.addWorkflowConfig("workflow-b", null));
    }

    @Test
    void metadataLikeStoresNameAndEvent() {
        MetadataLike metadata = new MetadataLike("session", "update");

        assertEquals("session", metadata.name());
        assertEquals("update", metadata.event());
    }
}
