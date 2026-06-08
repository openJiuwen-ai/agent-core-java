/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for trajectory stores.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/trajectory/test_store.py}.
 * </p>
 */
class TrajectoryStoreTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Payload(String value) {
    }

    private static TrajectoryStep makeStep(String kind, Object detail, Object error, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind(kind)
                .error(error)
                .detail(detail)
                .meta(meta != null ? meta : Map.of())
                .build();
    }

    private static TrajectoryStep makeLlmStep(String operatorId, List<Map<String, Object>> messages) {
        List<Object> normalizedMessages = messages != null
                ? new ArrayList<>(messages)
                : new ArrayList<>(List.of(Map.of("role", "user", "content", "hello")));
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(normalizedMessages)
                .build();
        return makeStep("llm", detail, null, Map.of("operator_id", operatorId));
    }

    private static TrajectoryStep makeToolStep(String toolName, Object callArgs, Object callResult) {
        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName(toolName)
                .callArgs(callArgs)
                .callResult(callResult)
                .build();
        return makeStep("tool", detail, null, Map.of("operator_id", toolName));
    }

    private static Trajectory makeTrajectory(String execId) {
        return makeTrajectory(execId, "session1", "offline", null, null);
    }

    private static Trajectory makeTrajectory(String execId, String caseId) {
        return makeTrajectory(execId, "session1", "offline", caseId, null);
    }

    private static Trajectory makeTrajectory(
            String execId,
            String sessionId,
            String source,
            String caseId,
            List<TrajectoryStep> steps) {
        return Trajectory.builder()
                .executionId(execId)
                .sessionId(sessionId != null ? sessionId : "session1")
                .source(source != null ? source : "offline")
                .caseId(caseId)
                .steps(steps != null ? steps : List.of(makeStep("llm", null, null, null)))
                .build();
    }

    @Test
    void inMemorySaveAndLoad() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        Trajectory trajectory = makeTrajectory("exec1", "case1");

        store.save(trajectory, null);
        Trajectory loaded = store.load("exec1", null);

        assertNotNull(loaded);
        assertEquals("exec1", loaded.getExecutionId());
        assertEquals("case1", loaded.getCaseId());
    }

    @Test
    void inMemoryLoadNonexistent() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();

        assertNull(store.load("nonexistent", null));
    }

    @Test
    void inMemoryQueryAll() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(makeTrajectory("exec1"), null);
        store.save(makeTrajectory("exec2"), null);

        assertEquals(2, store.query(null, Map.of()).size());
    }

    @Test
    void inMemoryQueryWithCaseFilter() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(makeTrajectory("exec1", "case1"), null);
        store.save(makeTrajectory("exec2", "case2"), null);

        List<Trajectory> results = store.query(null, Map.of("case_id", "case1"));

        assertEquals(1, results.size());
        assertEquals("case1", results.getFirst().getCaseId());
    }

    @Test
    void inMemoryQueryWithSourceFilter() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(makeTrajectory("exec1", "session1", "online", null, null), null);
        store.save(makeTrajectory("exec2", "session1", "offline", null, null), null);

        List<Trajectory> results = store.query(null, Map.of("source", "online"));

        assertEquals(1, results.size());
        assertEquals("online", results.getFirst().getSource());
    }

    @Test
    void inMemoryVersionIsolation() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(makeTrajectory("exec1"), "v1");
        store.save(makeTrajectory("exec1"), "v2");

        assertNotNull(store.load("exec1", "v1"));
        assertNotNull(store.load("exec1", "v2"));
    }

    @Test
    void inMemoryQueryEmptyStore() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();

        assertEquals(List.of(), store.query(null, Map.of()));
    }

    @Test
    void inMemoryOverwriteExisting() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(makeTrajectory("exec1", "case1"), null);
        store.save(makeTrajectory("exec1", "case2"), null);

        assertEquals("case2", store.load("exec1", null).getCaseId());
    }

    @Test
    void fileSaveAndLoad(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        Trajectory trajectory = makeTrajectory("exec1", "case1");

        store.save(trajectory, null);
        Trajectory loaded = store.load("exec1", null);

        assertNotNull(loaded);
        assertEquals("exec1", loaded.getExecutionId());
        assertEquals("case1", loaded.getCaseId());
    }

    @Test
    void fileLoadNonexistent(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);

        assertNull(store.load("nonexistent", null));
    }

    @Test
    void fileQueryAll(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(makeTrajectory("exec1"), null);
        store.save(makeTrajectory("exec2"), null);

        assertEquals(2, store.query(null, Map.of()).size());
    }

    @Test
    void fileQueryWithCaseFilter(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(makeTrajectory("exec1", "case1"), null);
        store.save(makeTrajectory("exec2", "case2"), null);

        List<Trajectory> results = store.query(null, Map.of("case_id", "case1"));

        assertEquals(1, results.size());
        assertEquals("case1", results.getFirst().getCaseId());
    }

    @Test
    void fileVersionCreatesDifferentFiles(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        Trajectory trajectory = makeTrajectory("exec1");

        store.save(trajectory, "v1");
        store.save(trajectory, "v2");

        assertTrue(Files.exists(tempDir.resolve("trajectories_v1.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("trajectories_v2.jsonl")));
    }

    @Test
    void fileFormatIsJsonlWithSnakeCase(@TempDir Path tempDir) throws IOException {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(makeTrajectory("exec1", "case1"), null);

        List<String> lines = Files.readAllLines(tempDir.resolve("trajectories_default.jsonl"));
        Map<String, Object> data = JSON.readValue(lines.getFirst(), new TypeReference<>() {
        });

        assertEquals(1, lines.size());
        assertEquals("exec1", data.get("execution_id"));
        assertEquals("case1", data.get("case_id"));
    }

    @Test
    void fileQueryEmptyFile(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);

        assertEquals(List.of(), store.query(null, Map.of()));
    }

    @Test
    void fileQueryNonexistentVersion(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);

        assertEquals(List.of(), store.query("nonexistent", Map.of()));
    }

    @Test
    void fileAppendToExistingFile(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(makeTrajectory("exec1"), null);
        store.save(makeTrajectory("exec2"), null);

        assertEquals(2, store.query(null, Map.of()).size());
    }

    @Test
    void fileLoadReturnsFirstDuplicateId(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(makeTrajectory("exec1", "case1"), null);
        store.save(makeTrajectory("exec1", "case2"), null);

        Trajectory loaded = store.load("exec1", null);

        assertNotNull(loaded);
        assertEquals("case1", loaded.getCaseId());
    }

    @Test
    void fileHandlesCorruptedJson(@TempDir Path tempDir) throws IOException {
        Path filePath = tempDir.resolve("trajectories_default.jsonl");
        Files.writeString(
                filePath,
                "{\"valid\":\"json\"}\n"
                        + "invalid json line\n"
                        + "{\"execution_id\":\"exec1\",\"session_id\":\"s1\",\"source\":\"offline\",\"steps\":[]}\n",
                StandardCharsets.UTF_8);

        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        List<Trajectory> results = store.query(null, Map.of());

        assertEquals(1, results.size());
        assertEquals("exec1", results.getFirst().getExecutionId());
    }

    @Test
    void fileRoundtripWithLlmStep(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(LLMCallDetail.builder()
                        .model("gpt-4")
                        .messages(List.of(Map.of("role", "user", "content", "hello")))
                        .response(Map.of("role", "assistant", "content", "hi"))
                        .usage(Map.of("prompt_tokens", 10, "completion_tokens", 5))
                        .build())
                .meta(Map.of("operator_id", "op1", "span_name", "test_span"))
                .build();
        store.save(makeTrajectory("exec1", "session1", "offline", null, List.of(step)), null);

        Trajectory loaded = store.load("exec1", null);

        assertNotNull(loaded);
        TrajectoryStep loadedStep = loaded.getSteps().getFirst();
        assertEquals("llm", loadedStep.getKind());
        LLMCallDetail detail = assertInstanceOf(LLMCallDetail.class, loadedStep.getDetail());
        assertEquals("gpt-4", detail.getModel());
        assertEquals("op1", loadedStep.getMeta().get("operator_id"));
    }

    @Test
    void fileRoundtripWithToolStep(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        TrajectoryStep step = makeToolStep("test_tool", Map.of("arg", "value"), Map.of("result", "success"));
        ((ToolCallDetail) step.getDetail()).setToolDescription("A test tool");
        store.save(makeTrajectory("exec1", "session1", "offline", null, List.of(step)), null);

        Trajectory loaded = store.load("exec1", null);

        assertNotNull(loaded);
        ToolCallDetail detail = assertInstanceOf(ToolCallDetail.class, loaded.getSteps().getFirst().getDetail());
        assertEquals("test_tool", detail.getToolName());
        assertEquals(Map.of("arg", "value"), detail.getCallArgs());
        assertEquals(Map.of("result", "success"), detail.getCallResult());
    }

    @Test
    void fileSaveSerializesPojoToolPayloads(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder()
                        .toolName("test_tool")
                        .callArgs(new Payload("arg"))
                        .callResult(new Payload("result"))
                        .build())
                .meta(Map.of("operator_id", "test_tool", "payload", new Payload("meta")))
                .build();
        Trajectory trajectory = makeTrajectory("exec-payload", "session1", "offline", null, List.of(step));
        trajectory.setMeta(Map.of("summary", new Payload("trajectory")));

        store.save(trajectory, null);
        Trajectory loaded = store.load("exec-payload", null);

        assertNotNull(loaded);
        TrajectoryStep loadedStep = loaded.getSteps().getFirst();
        ToolCallDetail detail = assertInstanceOf(ToolCallDetail.class, loadedStep.getDetail());
        assertEquals(Map.of("value", "arg"), detail.getCallArgs());
        assertEquals(Map.of("value", "result"), detail.getCallResult());
        assertEquals(Map.of("value", "meta"), loadedStep.getMeta().get("payload"));
        assertEquals(Map.of("value", "trajectory"), loaded.getMeta().get("summary"));
    }
}
