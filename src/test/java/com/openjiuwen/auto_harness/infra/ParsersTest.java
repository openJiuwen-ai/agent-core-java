/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PullRequestDraft;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParsersTest {

    @Test
    void parseTasksReadsJsonFenceAndNormalizesPipelineName() {
        List<OptimizationTask> tasks = Parsers.parseTasks("""
                ```json
                [
                  {
                    "topic": "fix timeout",
                    "description": "increase limit",
                    "files": ["a.py"],
                    "expected_effect": "stable",
                    "pipeline_name": "pr_pipeline"
                  }
                ]
                ```
                """);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTopic()).isEqualTo("fix timeout");
        assertThat(tasks.get(0).getFiles()).containsExactly("a.py");
        assertThat(tasks.get(0).getPipelineName()).isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
    }

    @Test
    void parseTasksReturnsEmptyWhenNoArrayExists() {
        assertThat(Parsers.parseTasks("no json here")).isEmpty();
    }

    @Test
    void parseLearningsKeepsTopicDictionariesOnly() {
        List<Map<String, Object>> learnings = Parsers.parseLearnings("""
                [{"topic": "timeout", "summary": "raise limit"}, {"summary": "skip"}]
                """);

        assertThat(learnings).hasSize(1);
        assertThat(learnings.get(0)).containsEntry("topic", "timeout");
    }

    @Test
    void parsePrDraftReadsKindFromBodyWhenKindFieldIsBlank() {
        Parsers.PullRequestDraftParseResult result = Parsers.parsePrDraftWithError("""
                {"title":"Fix timeout","body":"body text\\n/kind bug"}
                """);

        assertThat(result.error()).isEmpty();
        assertThat(result.draft().getKind()).isEqualTo("bug");
    }

    @Test
    void parsePrDraftReportsMissingTitleOrBody() {
        Parsers.PullRequestDraftParseResult result = Parsers.parsePrDraftWithError("""
                {"title":"","body":"x","kind":"bug"}
                """);

        assertThat(result.draft()).isNull();
        assertThat(result.error()).contains("title");
    }

    @Test
    void parsePipelineSelectionNormalizesNamesAndDefaultsBadLists() {
        PipelineSelectionArtifact artifact = Parsers.parsePipelineSelection("""
                ```json
                {
                  "pipeline_name": "extended_harness_pipeline",
                  "reason": "signal",
                  "alternatives": ["pr_pipeline"],
                  "confidence": "0.91",
                  "required_inputs": "bad",
                  "fallback_pipeline": "pr_pipeline"
                }
                ```
                """);

        assertThat(artifact).isNotNull();
        assertThat(artifact.getPipelineName()).isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(artifact.getAlternatives()).containsExactly(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(artifact.getRequiredInputs()).isEmpty();
        assertThat(artifact.getFallbackPipeline()).isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(artifact.getConfidence()).isEqualTo(0.91);
    }

    @Test
    void extractTextReadsPayloadContentOutputOrString() {
        assertThat(Parsers.extractText(new OutputSchema("chunk", 0, Map.of("content", "hello"))))
                .isEqualTo("hello");
        assertThat(Parsers.extractText(Map.of("payload", Map.of("output", "fallback"))))
                .isEqualTo("fallback");
        assertThat(Parsers.extractText(new OutputSchema("chunk", 0, "plain")))
                .isEqualTo("plain");
    }

    @Test
    void parseGapsParsesMarkdownRowsAndSortsByPriority() {
        List<Gap> gaps = Parsers.parseGaps("""
                | competitor | feature | current_state | gap_description | impact | feasibility | suggested_approach | target_files |
                | hermes | guard | missing | no guard | 0.9 | 0.8 | add rail | a.py,b.py |
                | devin | ui | partial | weak ui | 0.5 | 0.5 | add tool | c.py |
                """);

        assertThat(gaps).hasSize(2);
        assertThat(gaps.get(0).getCompetitor()).isEqualTo("hermes");
        assertThat(gaps.get(0).getTargetFiles()).containsExactly("a.py", "b.py");
    }

    @Test
    void parseExtensionDesignsOldArrayFormatReturnsNullPackage() {
        Parsers.ExtensionDesignParseResult result = Parsers.parseExtensionDesigns("""
                ```json
                [
                  {"gap_id":"gap_guard","extension_name":"filename_guard","kind":"constraint","applies_to":["ppt"]},
                  {"gap_id":"gap_ppt","extension_name":"ppt","depends_on":["filename_guard"]}
                ]
                ```
                """);

        assertThat(result.packageName()).isNull();
        assertThat(result.designs()).extracting(ExtensionDesign::getExtensionName)
                .containsExactly("filename_guard", "ppt");
        assertThat(result.designs().get(0).getKind()).isEqualTo("constraint");
        assertThat(result.designs().get(1).getKind()).isEqualTo("capability");
        assertThat(result.designs().get(1).getDependsOn()).containsExactly("filename_guard");
    }

    @Test
    void parseExtensionDesignsNewFormatPreservesPackageAndPatch() {
        Parsers.ExtensionDesignParseResult result = Parsers.parseExtensionDesigns("""
                ```json
                {
                  "package_name": "office_generator",
                  "designs": [
                    {
                      "extension_name": "ppt",
                      "components": ["tool", "skill"],
                      "file_plan": {"a.py": "create"},
                      "harness_config_patch": {"resources": {"tools": []}}
                    }
                  ]
                }
                ```
                """);

        assertThat(result.packageName()).isEqualTo("office_generator");
        assertThat(result.designs()).hasSize(1);
        assertThat(result.designs().get(0).getComponents()).containsExactly("tool", "skill");
        assertThat(result.designs().get(0).getFilePlan()).containsEntry("a.py", "create");
        assertThat(result.designs().get(0).getHarnessConfigPatch()).containsKey("resources");
    }
}
