/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentSkillToolActivationTest {
    @TempDir
    private Path tempDir;

    @Test
    void skillToolBecomesVisibleOnIterationAfterRegisteredSkillDocumentIsRead() throws Exception {
        Path skillDir = tempDir.resolve("echo-skill");
        Files.createDirectories(skillDir);
        Path skillDocument = skillDir.resolve("SKILL.md");
        Files.writeString(skillDocument, """
                ---
                name: EchoSkill
                description: Echo helper
                ---

                Read this before echoing.
                """);

        String sysOperationId = "skill-test-" + UUID.randomUUID();
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        String readFileToolId = "read-file-" + UUID.randomUUID();
        Runner.resourceMgr().addTool(readFileTool(readFileToolId));
        try {
            RecordingReActAgent agent = new RecordingReActAgent(skillDocument, readFileToolId);
            ReActAgentConfig config = new ReActAgentConfig();
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            config.setMaxIterations(3);
            config.setSysOperationId(sysOperationId);
            agent.configure(config);
            agent.registerSkill(skillDir.toString(), true).toCompletableFuture().join();
            agent.registerSkillTools(SkillToolBinding.builder()
                    .skillName("EchoSkill")
                    .tools(List.of(tool("echoTool")))
                    .build()).toCompletableFuture().join();

            Object result = agent.invoke(Map.of("query", "use skill"), new MemorySession("session-1"))
                    .toCompletableFuture()
                    .join();

            assertThat(agent.toolNamesPerCall).containsExactly(
                    List.of("read_file"),
                    List.of("read_file", "echoTool"),
                    List.of("read_file", "echoTool")
            );
            assertThat(result).isInstanceOf(Map.class);
            Map<?, ?> resultMap = (Map<?, ?>) result;
            assertThat(resultMap.get("output")).isEqualTo("done");
            assertThat(resultMap.get("result_type")).isEqualTo("answer");
        } finally {
            Runner.resourceMgr().removeTool(readFileToolId);
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    @Test
    void skillToolStaysHiddenWhenSkillDocumentReadDoesNotSucceed() throws Exception {
        Path skillDir = tempDir.resolve("hidden-skill");
        Files.createDirectories(skillDir);
        Path skillDocument = skillDir.resolve("SKILL.md");
        Files.writeString(skillDocument, """
                ---
                name: HiddenSkill
                description: Hidden helper
                ---

                This should not activate unless read_file succeeds.
                """);

        String sysOperationId = "skill-test-" + UUID.randomUUID();
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        try {
            RecordingReActAgent agent = new RecordingReActAgent(skillDocument);
            ReActAgentConfig config = new ReActAgentConfig();
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            config.setMaxIterations(2);
            config.setSysOperationId(sysOperationId);
            agent.configure(config);
            agent.registerSkill(skillDir.toString(), true).toCompletableFuture().join();
            agent.registerSkillTools(SkillToolBinding.builder()
                    .skillName("HiddenSkill")
                    .tools(List.of(tool("hiddenTool")))
                    .build()).toCompletableFuture().join();

            Object result = agent.invoke(Map.of("query", "try hidden skill"), new MemorySession("session-failed-read"))
                    .toCompletableFuture()
                    .join();

            assertThat(agent.toolNamesPerCall).containsExactly(
                    List.of("read_file"),
                    List.of("read_file")
            );
            assertThat(result).isInstanceOf(Map.class);
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    @Test
    void skillToolStaysHiddenWhenReadResultDoesNotIncludePath() throws Exception {
        Path skillDir = tempDir.resolve("pathless-skill");
        Files.createDirectories(skillDir);
        Path skillDocument = skillDir.resolve("SKILL.md");
        Files.writeString(skillDocument, """
                ---
                name: PathlessSkill
                description: Pathless helper
                ---

                This should not activate unless read_file proves the path.
                """);

        String sysOperationId = "skill-test-" + UUID.randomUUID();
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()
        ));
        String readFileToolId = "read-file-pathless-" + UUID.randomUUID();
        Runner.resourceMgr().addTool(pathlessReadFileTool(readFileToolId));
        try {
            RecordingReActAgent agent = new RecordingReActAgent(skillDocument, readFileToolId);
            ReActAgentConfig config = new ReActAgentConfig();
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            config.setMaxIterations(2);
            config.setSysOperationId(sysOperationId);
            agent.configure(config);
            agent.registerSkill(skillDir.toString(), true).toCompletableFuture().join();
            agent.registerSkillTools(SkillToolBinding.builder()
                    .skillName("PathlessSkill")
                    .tools(List.of(tool("pathlessTool")))
                    .build()).toCompletableFuture().join();

            Object result = agent.invoke(Map.of("query", "try pathless skill"), new MemorySession("session-pathless"))
                    .toCompletableFuture()
                    .join();

            assertThat(agent.toolNamesPerCall).containsExactly(
                    List.of("read_file"),
                    List.of("read_file")
            );
            assertThat(result).isInstanceOf(Map.class);
        } finally {
            Runner.resourceMgr().removeTool(readFileToolId);
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    private static Tool tool(String name) {
        return new Tool(ToolCard.builder()
                .id(name)
                .name(name)
                .description("tool " + name)
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return Map.of("ok", true);
            }
        };
    }

    private static Tool readFileTool(String id) {
        return new Tool(ToolCard.builder()
                .id(id)
                .name("read_file")
                .description("Read file")
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
                String path = String.valueOf(inputs.get("path"));
                return Map.of("path", path, "content", Files.readString(Path.of(path)));
            }
        };
    }

    private static Tool pathlessReadFileTool(String id) {
        return new Tool(ToolCard.builder()
                .id(id)
                .name("read_file")
                .description("Read file without path")
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
                String path = String.valueOf(inputs.get("path"));
                return Map.of("content", Files.readString(Path.of(path)));
            }
        };
    }

    private static final class RecordingReActAgent extends ReActAgent {
        private final List<List<String>> toolNamesPerCall = new ArrayList<>();
        private final String skillDocumentPath;
        private int callCount;

        private RecordingReActAgent(Path skillDocument) {
            this(skillDocument, "read_file");
        }

        private RecordingReActAgent(Path skillDocument, String readToolId) {
            super(new AgentCard("agent", "agent", "test"));
            this.skillDocumentPath = skillDocument.toAbsolutePath().toString().replace("\\", "\\\\");
            getAbilityManager().add(ToolCard.builder()
                    .id(readToolId)
                    .name("read_file")
                    .description("Read file")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            toolNamesPerCall.add(tools.stream().map(ToolInfo::getName).toList());
            callCount++;
            if (callCount == 1) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-read")
                                .name("read_file")
                                .arguments("{\"path\":\"" + skillDocumentPath + "\"}")
                                .build()))
                        .build();
            }
            if (callCount == 2) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-echo")
                                .name("echoTool")
                                .arguments("{\"text\":\"hello\"}")
                                .build()))
                        .build();
            }
            return new AssistantMessage("done");
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
