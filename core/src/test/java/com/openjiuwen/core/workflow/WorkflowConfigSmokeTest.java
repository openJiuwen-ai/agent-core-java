/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Focused smoke tests for the isolated workflow config candidate.
 * <p>
 * Mirrors Python's config model behavior in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
public final class WorkflowConfigSmokeTest {

    private WorkflowConfigSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        testNodeSpecDefaultsArePerInstance();
        testWorkflowSpecDefaultsArePerInstanceAndSerializeSnakeCaseNames();
        testValidationMatchesPydanticBounds();
        testExceptionConfigAllowsExtraFields();
        testSchemaOrTransformerBoundary();
    }

    private static void testNodeSpecDefaultsArePerInstance() {
        NodeSpec first = new NodeSpec();
        NodeSpec second = new NodeSpec();

        first.getAbilities().add(ComponentAbility.INVOKE);

        assertEquals(1, first.getAbilities().size(), "first abilities should be mutable");
        assertEquals(0, second.getAbilities().size(), "default abilities list should not be shared");
        assertEquals(0, first.getMaxRetries(), "max_retries default");
        assertEquals(-1.0d, first.getTimeout(), "timeout default");
        assertNull(first.getIoConfigs(), "io_configs default");
        assertNull(first.getStreamIoConfigs(), "stream_io_configs default");
        assertNull(first.getExceptionConfig(), "exception_config default");
    }

    private static void testWorkflowSpecDefaultsArePerInstanceAndSerializeSnakeCaseNames() throws Exception {
        WorkflowSpec first = new WorkflowSpec();
        WorkflowSpec second = new WorkflowSpec();

        first.getEdges().put("start", new ArrayList<>(List.of("end")));
        first.getStreamEdges().put("producer", new ArrayList<>(List.of("consumer")));
        first.getCompConfigs().put("start", new NodeSpec());
        first.getStreamSourceGroups().put("consumer-collect", new ArrayList<>(List.of(List.of("producer-stream"))));
        first.getStartNodes().add("start");

        assertTrue(second.getEdges().isEmpty(), "default edges map should not be shared");
        assertTrue(second.getStreamEdges().isEmpty(), "default stream_edges map should not be shared");
        assertTrue(second.getCompConfigs().isEmpty(), "default comp_configs map should not be shared");
        assertTrue(second.getStreamSourceGroups().isEmpty(), "default stream_source_groups map should not be shared");
        assertTrue(second.getStartNodes().isEmpty(), "default start_nodes list should not be shared");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(first);
        assertContains(json, "\"stream_edges\"", "stream_edges JSON field");
        assertContains(json, "\"comp_configs\"", "comp_configs JSON field");
        assertContains(json, "\"stream_source_groups\"", "stream_source_groups JSON field");
        assertContains(json, "\"start_nodes\"", "start_nodes JSON field");
    }

    private static void testValidationMatchesPydanticBounds() {
        NodeSpec nodeSpec = new NodeSpec();
        assertThrows(() -> nodeSpec.setMaxRetries(-1), "negative max_retries must fail");
        nodeSpec.setMaxRetries(2);
        assertEquals(2, nodeSpec.getMaxRetries(), "valid max_retries");

        WorkflowConfig config = new WorkflowConfig(new WorkflowCard("wf-1", "workflow"));
        assertEquals(5, config.getWorkflowMaxNestingDepth(), "workflow_max_nesting_depth default");
        assertTrue(config.getSpec() != null, "spec default_factory should create WorkflowSpec");
        config.setSpec(null);
        assertNull(config.getSpec(), "Optional spec should accept null");
        assertThrows(() -> config.setWorkflowMaxNestingDepth(-1), "negative nesting depth must fail");
        assertThrows(() -> config.setWorkflowMaxNestingDepth(11), "nesting depth above 10 must fail");
        config.setWorkflowMaxNestingDepth(10);
        assertEquals(10, config.getWorkflowMaxNestingDepth(), "valid nesting depth");
    }

    private static void testExceptionConfigAllowsExtraFields() throws Exception {
        ExceptionConfig exceptionConfig = new ExceptionConfig();
        exceptionConfig.putExtraField("component_error_recovery", Map.of("handler", "recover"));

        assertEquals("interrupt", exceptionConfig.getHandleType(), "handle_type default");
        assertTrue(exceptionConfig.getExtraFields().containsKey("component_error_recovery"),
                "extra fields should be retained");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(exceptionConfig);
        assertContains(json, "\"handle_type\":\"interrupt\"", "handle_type JSON field");
        assertContains(json, "\"component_error_recovery\"", "extra field JSON output");
    }

    private static void testSchemaOrTransformerBoundary() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        SchemaOrTransformer schemaValue = SchemaOrTransformer.ofSchema(schema);
        assertTrue(schemaValue.isSchema(), "schema value should report schema");
        assertEquals("object", schemaValue.getSchema().get("type"), "schema payload");

        SchemaOrTransformer transformerValue = SchemaOrTransformer.ofTransformer(state -> state.get("payload"));
        assertTrue(transformerValue.isTransformer(), "transformer value should report transformer");
        assertEquals("ok", transformerValue.getTransformer().apply(Map.of("payload", "ok")), "transformer result");

        CompIOConfig ioConfig = new CompIOConfig(schemaValue, transformerValue);
        assertTrue(ioConfig.getInputsSchema().isSchema(), "inputs_schema schema branch");
        assertTrue(ioConfig.getOutputsSchema().isTransformer(), "outputs_schema transformer branch");
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void assertContains(String value, String expected, String message) {
        if (!value.contains(expected)) {
            throw new AssertionError(message + " expected to contain " + expected + " but was " + value);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " expected null but was " + value);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + " expected " + expected + " but was " + actual);
        }
    }
}
