package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillEvaluatorCompatibilityTest {

    @Test
    void buildEvaluationQueryCarriesSkillPathOutputDirAndRequirement() {
        Path skillPath = Path.of("/tmp/demo-skill");
        Path outputDir = Path.of("/tmp/evals");

        SkillEvaluator evaluator = new SkillEvaluator();
        String query = evaluator.buildEvaluationQuery(skillPath, "Focus on trigger clarity and safety.", outputDir);

        assertThat(query).contains(skillPath.toString());
        assertThat(query).contains(outputDir.toString());
        assertThat(query).contains("Focus on trigger clarity and safety.");
    }

    @Test
    void configCopyRetainsPromptTemplateHeadersAndLimits() throws Exception {
        ReActAgentConfig source = ReActAgentConfig.builder()
                .modelName("demo-model")
                .modelProvider("openai")
                .apiKey("demo-key")
                .apiBase("https://example.com/v1")
                .promptTemplate(List.of(Map.of("role", "system", "content", "hello")))
                .customHeaders(Map.of("x-test", "1"))
                .maxIterations(17)
                .build();

        Method copyConfigMethod = SkillEvaluator.class.getDeclaredMethod("copyConfig", ReActAgentConfig.class);
        copyConfigMethod.setAccessible(true);
        ReActAgentConfig copy = (ReActAgentConfig) copyConfigMethod.invoke(null, source);

        assertThat(copy.getModelName()).isEqualTo("demo-model");
        assertThat(copy.getPromptTemplate()).containsExactly(Map.of("role", "system", "content", "hello"));
        assertThat(copy.getCustomHeaders()).containsEntry("x-test", "1");
        assertThat(copy.getMaxIterations()).isEqualTo(17);
        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getPromptTemplate()).isNotSameAs(source.getPromptTemplate());
    }
}
