/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.harness.tools.CronTool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests cron tool description timezone guidance.
 * <p>
 * Mirrors Python's {@code test_cron_prompt_timezone_guidance.py} in
 * {@code tests/test_cron_prompt_timezone_guidance.py}.
 */
class CronPromptTimezoneGuidanceTest {

    static class DummyCronBackend implements CronTool.CronToolBackend {
        @Override public List<Map<String, Object>> listJobs(boolean includeDisabled) { return List.of(); }
        @Override public Map<String, Object> getJob(String jobId) { return null; }
        @Override public Map<String, Object> createJob(Map<String, Object> params, CronTool.CronToolContext context) {
            return Map.of("params", params, "context", context);
        }
        @Override public Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronTool.CronToolContext context) {
            return Map.of("job_id", jobId, "patch", patch, "context", context);
        }
        @Override public boolean deleteJob(String jobId) { return true; }
        @Override public Map<String, Object> toggleJob(String jobId, boolean enabled) {
            return Map.of("job_id", jobId, "enabled", enabled);
        }
        @Override public List<Map<String, Object>> previewJob(String jobId, int count) { return List.of(); }
        @Override public String runNow(String jobId) { return "run-1"; }
        @Override public Map<String, Object> status() { return Map.of("ok", true); }
        @Override public List<Map<String, Object>> getRuns(String jobId, int limit) { return List.of(); }
        @Override public Map<String, Object> wake(String text, CronTool.CronToolContext context, String mode) {
            return Map.of("text", text, "context", context, "mode", mode);
        }
    }

    @BeforeAll
    static void registerProviders() {
        BuiltinToolProviders.registerAll();
    }

    @Test
    void testCronToolDescriptionContainsExpectedContent() {
        String description = ToolDescriptionRegistry.getToolDescription("cron", "cn");

        assertNotNull(description);
        assertFalse(description.isEmpty());
        assertFalse(description.contains("OpenClaw"));
        assertFalse(description.toLowerCase().contains("openclaw"));
    }

    @Test
    void testCronPromptMetadataHasNoOpenclawWording() {
        String allText = ToolDescriptionRegistry.getToolDescription("cron", "cn") + "\n" +
                ToolDescriptionRegistry.getToolDescription("cron", "en");

        String[] cronNames = {"cron", "cron_list_jobs", "cron_get_job", "cron_create_job",
                "cron_update_job", "cron_delete_job", "cron_toggle_job", "cron_preview_job"};
        for (String name : cronNames) {
            allText += "\n" + ToolDescriptionRegistry.getToolDescription(name, "cn");
            allText += "\n" + ToolDescriptionRegistry.getToolDescription(name, "en");
        }

        assertFalse(allText.contains("OpenClaw"));
        assertFalse(allText.toLowerCase().contains("openclaw"));
    }

    @Test
    void testBuildToolCardReturnsValidCard() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("cron", "cron_test", "cn", null);

        assertEquals("cron", card.get("name"));
        assertNotNull(card.get("description"));
        assertFalse(((String) card.get("description")).isEmpty());
        assertNotNull(card.get("input_params"));
        assertNotNull(card.get("id"));
        assertTrue(((String) card.get("id")).startsWith("cron_test_"));
    }

    @Test
    void testBuildToolCardWithAgentId() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("cron", "cron_test", "cn", "myAgent");

        assertTrue(((String) card.get("id")).contains("cron_test_myAgent"));
    }

    @Test
    void testCronToolContextScope() {
        CronTool.CronToolContext context = new CronTool.CronToolContext(
                "web", "sess-1", null, null);
        assertEquals("web_sess-1", context.getToolScope());
    }

    @Test
    void testCronToolDispatchStatusAction() throws Exception {
        DummyCronBackend backend = new DummyCronBackend();
        CronTool.CronToolContext context = new CronTool.CronToolContext(
                "web", "sess-1", null, null);

        Object result = CronTool.dispatchAction(backend, context, "status", Map.of());
        assertEquals(Map.of("ok", true), result);
    }
}
