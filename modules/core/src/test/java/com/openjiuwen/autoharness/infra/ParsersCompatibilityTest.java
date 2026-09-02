
package com.openjiuwen.autoharness.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.autoharness.schema.Gap;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.PullRequestDraft;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ParsersCompatibilityTest {
    @Test
    void parseTasksShouldSupportJsonFenceAndNormalizePipelineAlias() {
        String raw = """
                plan:\n
                ```json
                [
                  {
                    "topic": "tighten verify stage",
                    "description": "focus verify helper",
                    "files": ["openjiuwen/auto_harness/stages/verify.py"],
                    "expected_effect": "clearer failures",
                    "pipeline_name": "pr_pipeline"
                  }
                ]
                ```
                """;

        List<OptimizationTask> tasks = Parsers.parseTasks(raw);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTopic()).isEqualTo("tighten verify stage");
        assertThat(tasks.get(0).getExpectedEffect()).isEqualTo("clearer failures");
        assertThat(tasks.get(0).getPipelineName()).isEqualTo("meta_evolve_pipeline");
    }

    @Test
    void parseTasksShouldIgnoreInvalidJsonAndItemsWithoutTopic() {
        assertThat(Parsers.parseTasks("not json")).isEmpty();
        assertThat(Parsers.parseTasks("[\n  {\"description\":\"missing topic\"}\n]")).isEmpty();
    }

    @Test
    void parseLearningsShouldReturnOnlyObjectsWithTopic() {
        String raw = """
                [
                  {"topic":"verify", "summary":"keep logs short", "type":"insight"},
                  {"summary":"missing topic"}
                ]
                """;

        List<Map<String, Object>> learnings = Parsers.parseLearnings(raw);

        assertThat(learnings).hasSize(1);
        assertThat(learnings.get(0)).containsEntry("topic", "verify");
        assertThat(learnings.get(0)).containsEntry("summary", "keep logs short");
    }

    @Test
    void parsePrDraftShouldValidateKindAndReadKindFromBodyDirective() {
        String raw = """
                ```json
                {
                  "title": "Refine verify parser slice",
                  "body": "/kind refactor\\n## Summary\\n- port parser helpers"
                }
                ```
                """;

        Parsers.PullRequestDraftParseResult parsed = Parsers.parsePrDraftWithError(raw);

        assertThat(parsed.error()).isEmpty();
        PullRequestDraft draft = parsed.draft();
        assertThat(draft).isNotNull();
        assertThat(draft.getKind()).isEqualTo("refactor");
        assertThat(Parsers.parsePrDraft("{\"title\":\"x\",\"body\":\"/kind unknown\"}")).isNull();
    }

    @Test
    void parsePrDraftShouldReturnPythonLikeDetailedErrors() {
        assertThat(Parsers.parsePrDraftWithError("no json").error()).isEqualTo("未找到 JSON 对象");
        assertThat(Parsers.parsePrDraftWithError("{bad json}").error()).isEqualTo("JSON 解析失败");
        assertThat(Parsers.parsePrDraftWithError("{\"title\":\"x\",\"body\":\"\"}").error())
                .isEqualTo("缺少 title 或 body");
        assertThat(Parsers.parsePrDraftWithError("{\"title\":\"x\",\"body\":\"/kind unknown\"}").error())
                .isEqualTo("kind 必须是 bug/task/feature/refactor/clean_code 之一");
    }

    @Test
    void parsePipelineSelectionShouldNormalizeAliasesAndCoerceFields() {
        String raw = """
                {
                  "pipeline_name": "extended_harness_pipeline",
                  "reason": "needs more checks",
                  "alternatives": ["pr_pipeline", "custom_pipeline"],
                  "confidence": "0.75",
                  "risk_level": "medium",
                  "required_inputs": ["repo", "tests"],
                  "fallback_pipeline": "pr_pipeline"
                }
                """;

        PipelineSelectionArtifact artifact = Parsers.parsePipelineSelection(raw);

        assertThat(artifact).isNotNull();
        assertThat(artifact.getPipelineName()).isEqualTo("extended_evolve_pipeline");
        assertThat(artifact.getAlternatives()).containsExactly("meta_evolve_pipeline", "custom_pipeline");
        assertThat(artifact.getConfidence()).isEqualTo(0.75);
        assertThat(artifact.getFallbackPipeline()).isEqualTo("meta_evolve_pipeline");
    }

    @Test
    void extractTextShouldReadOutputSchemaPayloadContentOnly() {
        OutputSchema chunk = new OutputSchema("message", 0, Map.of("content", "hello"));

        assertThat(Parsers.extractText(chunk)).isEqualTo("hello");
        assertThat(Parsers.extractText(new OutputSchema("message", 0, Map.of("other", "x")))).isEmpty();
        assertThat(Parsers.extractText("plain text")).isEmpty();
    }

    @Test
    void parseGapsShouldSkipHeaderAndSortByPriorityDescending() {
        String raw =
            """
                    | competitor | feature | current_state | gap_description | impact | feasibility | suggested_approach | target_files |
                    |------------|---------|---------------|-----------------|--------|-------------|--------------------|--------------|
                    | Anthropic | parser quality | partial | missing PR parsing | 0.9 | 0.5 | port helper | src/a.py, src/b.py |
                    | OpenAI | gap ranking | partial | missing gap ranking | 0.5 | 0.5 | port table parser | src/c.py |
                    | Broken | bad row | x | y | nope | 0.2 | ignore me | src/d.py |
                    """;

        List<Gap> gaps = Parsers.parseGaps(raw);

        assertThat(gaps).hasSize(2);
        assertThat(gaps.get(0).getCompetitor()).isEqualTo("Anthropic");
        assertThat(gaps.get(0).priority()).isEqualTo(0.45);
        assertThat(gaps.get(0).getTargetFiles()).containsExactly("src/a.py", "src/b.py");
        assertThat(gaps.get(1).getCompetitor()).isEqualTo("OpenAI");
    }
}
