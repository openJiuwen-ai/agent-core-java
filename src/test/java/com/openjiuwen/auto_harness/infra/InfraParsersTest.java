package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.PipelineSelectionArtifact;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's {@code openjiuwen.auto_harness.infra.parsers}.
 */
class InfraParsersTest {

    @Test
    void parsePipelineSelectionReturnsNullWhenJsonIsMissing() {
        assertNull(InfraParsers.parsePipelineSelection("agent answered without structured json"));
    }

    @Test
    void parsePipelineSelectionReturnsNullWhenPipelineNameIsMissing() {
        assertNull(InfraParsers.parsePipelineSelection("{\"reason\":\"no pipeline selected\"}"));
        assertNull(InfraParsers.parsePipelineSelection("{\"pipeline_name\":\"   \"}"));
    }

    @Test
    void parsePipelineSelectionExtractsAndNormalizesJsonBlock() {
        String raw = """
                prefix
                ```json
                {
                  "pipeline_name": "meta-evolve-pipeline",
                  "reason": "best fit",
                  "alternatives": ["clean-code-pipeline"],
                  "confidence": "0.75",
                  "risk_level": "low",
                  "required_inputs": ["tests"],
                  "fallback_pipeline": "safe-fix"
                }
                ```
                suffix
                """;

        PipelineSelectionArtifact artifact = InfraParsers.parsePipelineSelection(raw);

        assertEquals("meta_evolve_pipeline", artifact.getPipelineName());
        assertEquals("best fit", artifact.getReason());
        assertEquals(List.of("clean_code_pipeline"), artifact.getAlternatives());
        assertEquals(0.75, artifact.getConfidence());
        assertEquals("low", artifact.getRiskLevel());
        assertEquals(List.of("tests"), artifact.getRequiredInputs());
        assertEquals("safe_fix", artifact.getFallbackPipeline());
    }

    @Test
    void extractTextMirrorsPayloadContentLookup() {
        OutputSchema chunk = new OutputSchema("output", 0, Map.of("content", "hello"));

        assertEquals("hello", InfraParsers.extractText(chunk));
        assertEquals("", InfraParsers.extractText(new OutputSchema("output", 0, Map.of())));
        assertEquals("", InfraParsers.extractText(new Object()));
    }
}
