/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.prompts.tools.BuiltinToolProviders;
import com.openjiuwen.harness.prompts.tools.ToolDescriptionRegistry;
import com.openjiuwen.harness.tools.CronTool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cron prompt timezone guidance tests.
 * <p>
 * Mirrors Python's {@code test_cron_prompt_timezone_guidance.py} in
 * {@code tests/test_cron_prompt_timezone_guidance.py}.
 */
class TestCronPromptTimezoneGuidance {

    @BeforeAll
    static void registerProviders() {
        BuiltinToolProviders.registerAll();
    }

    @Test
    void testCronToolDescriptionWarnsAgainstRewritingToUtc() {
        String description = ToolDescriptionRegistry.getToolDescription("cron", "cn");

        assertTrue(description.contains("schedule.at"));
        assertTrue(description.contains("不要改写成 Z 或 UTC"));
        assertTrue(description.contains("sessionTarget=current"));
        assertFalse(description.contains("OpenClaw"));
        assertFalse(description.toLowerCase().contains("openclaw"));
    }

    @Test
    void testCronPromptMetadataHasNoOpenclawWording() {
        StringBuilder allText = new StringBuilder();
        String[] cronNames = {"cron", "cron_list_jobs", "cron_get_job", "cron_create_job",
                "cron_update_job", "cron_delete_job", "cron_toggle_job", "cron_preview_job"};
        for (String name : cronNames) {
            allText.append('\n').append(ToolDescriptionRegistry.getToolDescription(name, "cn"));
            allText.append('\n').append(ToolDescriptionRegistry.getToolDescription(name, "en"));
            allText.append('\n').append(ToolDescriptionRegistry.getToolInputParams(name, "cn"));
            allText.append('\n').append(ToolDescriptionRegistry.getToolInputParams(name, "en"));
        }

        assertFalse(allText.toString().contains("OpenClaw"));
        assertFalse(allText.toString().toLowerCase().contains("openclaw"));
    }

    @Test
    void testBuildToolCardExposesTimezoneGuidance() {
        Map<String, Object> card = ToolDescriptionRegistry.buildToolCard("cron", "cron_test", "cn", null);

        assertEquals("cron", card.get("name"));
        assertTrue(((String) card.get("description")).contains("schedule.at"));
        assertTrue(((String) card.get("description")).contains("sessionTarget=current"));
        assertNotNull(card.get("input_params"));
    }

    @Test
    void testCreateCronToolsSupportsUnifiedEntryOnly() {
        List<Tool> tools = CronTool.createCronTools(
                new DummyCronBackend(),
                new CronTool.CronToolContext("web", "sess-1"),
                "cn",
                null,
                null,
                false,
                null);

        assertEquals(List.of("cron"), tools.stream().map(tool -> tool.getCard().getName()).toList());
        assertTrue(tools.get(0).getCard().getId().contains("web_sess-1"));
    }

    @Test
    void testCreateCronToolsCanKeepLegacyCompatEntries() {
        List<Tool> tools = CronTool.createCronTools(
                new DummyCronBackend(),
                new CronTool.CronToolContext("web", "sess-1"),
                "cn",
                null,
                null,
                true,
                null);

        List<String> names = tools.stream().map(tool -> tool.getCard().getName()).toList();
        assertTrue(names.contains("cron"));
        assertTrue(names.contains("cron_list_jobs"));
        assertTrue(names.contains("cron_create_job"));
        assertTrue(names.contains("cron_preview_job"));
    }

    static class DummyCronBackend implements CronTool.CronToolBackend {
        @Override
        public List<Map<String, Object>> listJobs(boolean includeDisabled) {
            return List.of();
        }

        @Override
        public Map<String, Object> getJob(String jobId) {
            return null;
        }

        @Override
        public Map<String, Object> createJob(Map<String, Object> params, CronTool.CronToolContext context) {
            return Map.of("params", params, "context", context);
        }

        @Override
        public Map<String, Object> updateJob(String jobId, Map<String, Object> patch,
                                             CronTool.CronToolContext context) {
            return Map.of("job_id", jobId, "patch", patch, "context", context);
        }

        @Override
        public boolean deleteJob(String jobId) {
            return true;
        }

        @Override
        public Map<String, Object> toggleJob(String jobId, boolean enabled) {
            return Map.of("job_id", jobId, "enabled", enabled);
        }

        @Override
        public List<Map<String, Object>> previewJob(String jobId, int count) {
            return List.of();
        }

        @Override
        public String runNow(String jobId) {
            return "run-1";
        }

        @Override
        public Map<String, Object> status() {
            return Map.of("ok", true);
        }

        @Override
        public List<Map<String, Object>> getRuns(String jobId, int limit) {
            return List.of();
        }

        @Override
        public Map<String, Object> wake(String text, CronTool.CronToolContext context, String mode) {
            return Map.of("text", text, "context", context, "mode", mode);
        }
    }
}
