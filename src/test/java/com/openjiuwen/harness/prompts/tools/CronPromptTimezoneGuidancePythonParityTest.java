/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.CronTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timezone guidance parity tests for cron prompts and tools.
 *
 * <p>Mirrors Python's {@code tests/test_cron_prompt_timezone_guidance.py}.</p>
 */
class CronPromptTimezoneGuidancePythonParityTest {

    @Test
    void cronToolDescriptionWarnsAgainstRewritingToUtc() {
        String description = new CronPromptToolProviders.CronMetadataProvider().getDescription("cn");

        assertThat(description).contains("schedule.at");
        assertThat(description).contains("不要改写成 Z 或 UTC");
        assertThat(description).contains("sessionTarget=current");
        assertThat(description).doesNotContain("OpenClaw");
        assertThat(description).doesNotContain("openclaw");
    }

    @Test
    void cronPromptMetadataHasNoOpenclawWording() {
        String allText = String.join("\n", allCronMetadataText());

        assertThat(allText).doesNotContain("OpenClaw");
        assertThat(allText).doesNotContain("openclaw");
    }

    @Test
    void buildToolCardExposesTimezoneGuidance() {
        ToolCard card = HarnessPromptToolsPackage.buildToolCard("cron", "cron_test", "cn");

        assertThat(card.getName()).isEqualTo("cron");
        assertThat(card.getDescription()).contains("schedule.at");
        assertThat(card.getDescription()).contains("sessionTarget=current");
    }

    @Test
    void createCronToolsSupportsUnifiedEntryOnly() {
        List<Tool> tools = CronTool.createCronTools(
                new DummyCronBackend(),
                new CronTool.CronToolContext("web", "sess-1", Map.of(), null),
                "cn",
                List.of(),
                null,
                false,
                null
        );

        assertThat(tools).extracting(tool -> tool.getCard().getName()).containsExactly("cron");
        assertThat(tools.get(0).getCard().getId()).contains("web_sess-1");
    }

    @Test
    void createCronToolsCanKeepLegacyCompatEntries() {
        List<Tool> tools = CronTool.createCronTools(
                new DummyCronBackend(),
                new CronTool.CronToolContext("web", "sess-1", Map.of(), null),
                "cn",
                List.of(),
                null,
                true,
                null
        );

        assertThat(tools)
                .extracting(tool -> tool.getCard().getName())
                .contains("cron", "cron_list_jobs", "cron_create_job", "cron_preview_job");
    }

    private static List<String> allCronMetadataText() {
        List<ToolMetadataProvider> providers = List.of(
                new CronPromptToolProviders.CronMetadataProvider(),
                new CronPromptToolProviders.CronListJobsMetadataProvider(),
                new CronPromptToolProviders.CronGetJobMetadataProvider(),
                new CronPromptToolProviders.CronCreateJobMetadataProvider(),
                new CronPromptToolProviders.CronUpdateJobMetadataProvider(),
                new CronPromptToolProviders.CronDeleteJobMetadataProvider(),
                new CronPromptToolProviders.CronToggleJobMetadataProvider(),
                new CronPromptToolProviders.CronPreviewJobMetadataProvider()
        );
        List<String> values = new ArrayList<>();
        for (ToolMetadataProvider provider : providers) {
            values.add(provider.getDescription("cn"));
            values.add(provider.getDescription("en"));
            collectStrings(provider.getInputParams("cn"), values);
            collectStrings(provider.getInputParams("en"), values);
        }
        return values;
    }

    private static void collectStrings(Object value, List<String> values) {
        if (value instanceof String text) {
            values.add(text);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectStrings(item, values));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collectStrings(item, values));
        }
    }

    private static final class DummyCronBackend implements CronTool.CronToolBackend {
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
        public Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronTool.CronToolContext context) {
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
