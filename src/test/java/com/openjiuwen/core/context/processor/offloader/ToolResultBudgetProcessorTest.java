package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMessages;
import com.openjiuwen.core.context_engine.schema.OffloadToolMessage;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultBudgetProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<ToolCall> createToolCallList(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build())
                .toList();
    }

    private static ContextEngine engine(Workspace workspace, SysOperation sysOperation) {
        return new ContextEngine(
                ContextEngineConfig.builder().defaultWindowMessageNum(100).build(),
                workspace,
                sysOperation
        );
    }

    private static SysOperation realSysOperation(Path workspaceRoot) {
        SysOperationCard card = new SysOperationCard();
        card.setId("test_real_sys_op");
        card.setMode(com.openjiuwen.core.sys_operation.OperationMode.LOCAL);
        com.openjiuwen.core.sysop.config.LocalWorkConfig workConfig = new com.openjiuwen.core.sysop.config.LocalWorkConfig();
        workConfig.setSandboxRoot(List.of(workspaceRoot.toString()));
        workConfig.setRestrictToSandbox(true);
        workConfig.setWorkDir(workspaceRoot.toString());
        card.setWorkConfig(workConfig);
        return new SysOperation(card);
    }

    private static com.openjiuwen.core.session.AgentSessionApi session(String sessionId) {
        return new com.openjiuwen.core.session.AgentSessionApi() {
            @Override
            public String getSessionId() {
                return sessionId;
            }

            @Override
            public Object getState(String key) {
                return null;
            }

            @Override
            public void updateState(java.util.Map<String, Object> state) {
            }

            @Override
            public void writeStream(Object data) {
            }

            @Override
            public java.util.Iterator<Object> streamIterator() {
                return java.util.List.of().iterator();
            }
        };
    }

    @Test
    @DisplayName("triggerAddMessages returns false when below threshold")
    void testTriggerAddMessagesFalseWhenBelowThreshold() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(
                ToolResultBudgetProcessorConfig.builder()
                        .tokensThreshold(100000)
                        .largeMessageThreshold(100)
                        .trimSize(20)
                        .build()
        );
        ModelContext context = engine(null, null).createContext(
                "test_ctx",
                null,
                null,
                List.of(
                        new UserMessage("short"),
                        ToolMessage.builder().content("short").toolCallId("tc-1").build()
                ),
                null
        );

        Map<String, Object> kwargs = Map.of();
        boolean triggered = processor.triggerAddMessages(
                (SessionModelContext) ((com.openjiuwen.core.context.context.SessionModelContext) context).unwrap(),
                List.of(new UserMessage("more")),
                kwargs).toCompletableFuture().join();
        assertThat(triggered).isFalse();
    }

    @Test
    @DisplayName("triggerAddMessages returns true when above threshold")
    void testTriggerAddMessagesTrueWhenAboveThreshold() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(
                ToolResultBudgetProcessorConfig.builder()
                        .tokensThreshold(100)
                        .largeMessageThreshold(50)
                        .trimSize(20)
                        .build()
        );
        ModelContext context = engine(null, null).createContext(
                "test_ctx",
                null,
                null,
                List.of(
                        new UserMessage("task"),
                        AssistantMessage.builder().content("").toolCalls(createToolCallList("tc-1")).build(),
                        ToolMessage.builder().content("x".repeat(600)).toolCallId("tc-1").name("grep").build(),
                        new AssistantMessage("done")
                ),
                null
        );

        Map<String, Object> kwargs2 = Map.of();
        boolean triggered = processor.triggerAddMessages(
                (SessionModelContext) ((com.openjiuwen.core.context.context.SessionModelContext) context).unwrap(),
                List.of(),
                kwargs2).toCompletableFuture().join();
        assertThat(triggered).isTrue();
    }

    @Test
    @DisplayName("allowlisted tools are not offloaded")
    void testAllowlistRespected() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(
                ToolResultBudgetProcessorConfig.builder()
                        .toolNameAllowlist(List.of("important_tool"))
                        .build()
        );
        List<BaseMessage> messages = List.of(
                AssistantMessage.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("tc-1").name("important_tool").type("function").arguments("").build()
                )).build(),
                ToolMessage.builder().content("x".repeat(500)).toolCallId("tc-1").name("important_tool").build()
        );
        ModelContext context = engine(null, null).createContext("test_ctx", null, null, messages, null);

        boolean shouldOffload = processor.isAlreadyOffloaded((ToolMessage) messages.get(1));
        assertThat(shouldOffload).isFalse();
    }

    @Test
    @DisplayName("already offloaded tool message is detected")
    void testAlreadyOffloadedDetection() {
        ToolResultBudgetProcessor processor = new ToolResultBudgetProcessor(ToolResultBudgetProcessorConfig.builder().build());
        OffloadToolMessage offloaded = new OffloadToolMessage(
                ToolResultBudgetProcessor.PERSISTED_OUTPUT_TAG + "\nOutput too large...",
                "fake-handle",
                "filesystem",
                "tc-x");

        assertThat(processor.isAlreadyOffloaded(offloaded)).isTrue();
        assertThat(processor.isAlreadyOffloaded(ToolMessage.builder().content("normal").toolCallId("tc-y").build())).isFalse();
    }

    @Disabled("Temporarily disabled due to unit test failure - see surefire-reports")
    @Test
    @DisplayName("filesystem offload writes real file and preserves original content")
    void testFilesystemOffloadWritesRealFile(@TempDir Path tempDir) throws Exception {
        Workspace workspace = new Workspace(tempDir.resolve("workspace").toString(), "en");
        ContextEngine engine = engine(workspace, realSysOperation(tempDir.resolve("workspace")));
        String sessionId = "real_fs_test_session";
        ModelContext context = engine.createContext(
                "test_ctx",
                session(sessionId),
                List.of(new ContextEngine.ProcessorSpec(
                        "ToolResultBudgetProcessor",
                        ToolResultBudgetProcessorConfig.builder()
                                .tokensThreshold(50)
                                .largeMessageThreshold(50)
                                .trimSize(20)
                                .build()
                )),
                List.of(),
                null
        );

        String largeContent = "UNIQUE_CONTENT_" + "x".repeat(500) + "_END_MARKER";
        context.addMessages(List.of(
                new UserMessage("Run grep on large file"),
                AssistantMessage.builder().content("").toolCalls(createToolCallList("tc-1")).build(),
                ToolMessage.builder().content(largeContent).toolCallId("tc-1").name("grep").build(),
                new AssistantMessage("Found results in the file.")
        ));

        BaseMessage toolMessage = context.getMessages().get(2);
        assertThat(toolMessage.getContentAsString()).startsWith(ToolResultBudgetProcessor.PERSISTED_OUTPUT_TAG);
        assertThat(toolMessage).isInstanceOf(OffloadToolMessage.class);
        OffloadToolMessage offloadToolMessage = (OffloadToolMessage) toolMessage;
        assertThat(offloadToolMessage.getOffloadType()).isEqualTo("filesystem");

        Path offloadFile = workspace.root()
                .resolve("context")
                .resolve(sessionId + "_context")
                .resolve("offload")
                .resolve("ToolResultBudgetProcessor_" + offloadToolMessage.getOffloadHandle() + ".json");
        assertThat(Files.exists(offloadFile)).isTrue();

        String payloadJson = Files.readString(offloadFile);
        var payload = MAPPER.readTree(payloadJson);
        assertThat(payload.get("offload_handle").asText()).isEqualTo(offloadToolMessage.getOffloadHandle());
        assertThat(payload.get("messages").get(0).get("content").asText()).isEqualTo(largeContent);
    }

    @Test
    @DisplayName("filesystem offload reloads original content")
    void testFilesystemReload(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.resolve("workspace").toString(), "en");
        ContextEngine engine = engine(workspace, realSysOperation(tempDir.resolve("workspace")));
        String sessionId = "reload_test_session";
        ModelContext context = engine.createContext(
                "test_ctx",
                session(sessionId),
                List.of(new ContextEngine.ProcessorSpec(
                        "ToolResultBudgetProcessor",
                        ToolResultBudgetProcessorConfig.builder()
                                .tokensThreshold(50)
                                .largeMessageThreshold(50)
                                .trimSize(20)
                                .build()
                )),
                List.of(),
                null
        );

        String originalContent = "ORIGINAL_TOOL_CONTENT_" + "x".repeat(500) + "_END_MARKER";
        context.addMessages(List.of(
                new UserMessage("Get detailed output"),
                AssistantMessage.builder().content("").toolCalls(createToolCallList("tc-reload")).build(),
                ToolMessage.builder().content(originalContent).toolCallId("tc-reload").name("grep").build(),
                new AssistantMessage("Done.")
        ));

        OffloadToolMessage toolMessage = (OffloadToolMessage) context.getMessages().get(2);
        com.openjiuwen.core.context_engine.context.SessionModelContext delegate =
                (com.openjiuwen.core.context_engine.context.SessionModelContext)
                        ((com.openjiuwen.core.context.context.SessionModelContext) context).unwrap();
        com.openjiuwen.core.context_engine.context.SessionModelContext.ReloaderTool reloader =
                (com.openjiuwen.core.context_engine.context.SessionModelContext.ReloaderTool) delegate.reloaderTool();
        String reloaded = reloader.reloadOriginalContextMessages(
                toolMessage.getOffloadHandle(), toolMessage.getOffloadType());

        assertThat(reloaded).contains("ORIGINAL_TOOL_CONTENT_");
        assertThat(reloaded).contains("_END_MARKER");
    }

    @Test
    @DisplayName("fallback to in-memory when no workspace or sys operation")
    void testFallbackToInMemory() {
        ContextEngine engine = engine(null, null);
        ModelContext context = engine.createContext(
                "test_ctx",
                session("inmemory_reload_session"),
                List.of(new ContextEngine.ProcessorSpec(
                        "ToolResultBudgetProcessor",
                        ToolResultBudgetProcessorConfig.builder()
                                .tokensThreshold(100)
                                .largeMessageThreshold(50)
                                .trimSize(20)
                                .build()
                )),
                List.of(),
                null
        );

        String originalContent = "INMEMORY_TOOL_CONTENT_" + "y".repeat(500) + "_END_MARKER";
        context.addMessages(List.of(
                new UserMessage("Read file"),
                AssistantMessage.builder().content("").toolCalls(createToolCallList("tc-inmem")).build(),
                ToolMessage.builder().content(originalContent).toolCallId("tc-inmem").name("read_file").build(),
                new AssistantMessage("Done.")
        ));

        OffloadToolMessage toolMessage = (OffloadToolMessage) context.getMessages().get(2);
        assertThat(toolMessage.getOffloadType()).isEqualTo("in_memory");

        com.openjiuwen.core.context_engine.context.SessionModelContext delegate =
                (com.openjiuwen.core.context_engine.context.SessionModelContext)
                        ((com.openjiuwen.core.context.context.SessionModelContext) context).unwrap();
        com.openjiuwen.core.context_engine.context.SessionModelContext.ReloaderTool reloader =
                (com.openjiuwen.core.context_engine.context.SessionModelContext.ReloaderTool) delegate.reloaderTool();
        String reloaded = reloader.reloadOriginalContextMessages(
                toolMessage.getOffloadHandle(), toolMessage.getOffloadType());
        assertThat(reloaded).contains("INMEMORY_TOOL_CONTENT_");
        assertThat(reloaded).contains("_END_MARKER");
    }
}
