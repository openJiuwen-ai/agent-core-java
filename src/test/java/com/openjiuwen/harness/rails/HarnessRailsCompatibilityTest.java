
package com.openjiuwen.harness.rails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.evolution.EvolutionPatch;
import com.openjiuwen.harness.rails.evolution.EvolutionRecord;
import com.openjiuwen.harness.rails.evolution.EvolutionTarget;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.task_loop.TaskIterationContext;
import com.openjiuwen.harness.task_loop.TaskPlan;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class HarnessRailsCompatibilityTest {
    @TempDir
    Path tempDir;

    private static class TestSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private TestSession(String sessionId) {
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
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static final class FakeMemoryProvider implements MemoryProvider {
        private boolean initialized;
        private final List<Map<String, Object>> syncCalls = new ArrayList<>();
        private final List<String> prefetchQueries = new ArrayList<>();

        @Override
        public String getName() {
            return "fake";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void initialize(Map<String, Object> kwargs) {
            initialized = true;
        }

        @Override
        public List<Map<String, Object>> getToolSchemas() {
            return List.of(Map.of("name", "ltm_search", "description", "Search long-term memory", "parameters",
                    Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")))));
        }

        @Override
        public String handleToolCall(String toolName, Map<String, Object> args) {
            return "{\"tool\":\"" + toolName + "\",\"query\":\"" + args.getOrDefault("query", "") + "\"}";
        }

        @Override
        public String prefetch(String query, Map<String, Object> kwargs) {
            prefetchQueries.add(query);
            return "remembered context for " + query;
        }

        @Override
        public void syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
            syncCalls.add(Map.of("user", userMsg, "assistant", assistantMsg));
        }

        @Override
        public String systemPromptBlock() {
            return "Use ltm_search when long-term memory may help.";
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }
    }

    @Test
    void railsShouldExposeExpectedPrioritiesAndBehavior() {
        AgentModeRail modeRail = new AgentModeRail();
        TaskPlanningRail planningRail = new TaskPlanningRail(true, 10);
        SkillUseRail skillUseRail = new SkillUseRail();
        SecurityRail securityRail = new SecurityRail();
        SessionRail sessionRail = new SessionRail();
        SubagentRail subagentRail = new SubagentRail();
        SysOperationRail sysOperationRail = new SysOperationRail();

        assertThat(modeRail.priority()).isEqualTo(85);
        assertThat(modeRail.enforceMode(null)).isEqualTo(AgentMode.NORMAL);
        assertThat(modeRail.visibleToolNames(AgentMode.NORMAL,
                List.of("switch_mode", "enter_plan_mode", "exit_plan_mode"))).containsExactly("switch_mode");
        assertThat(modeRail.visibleToolNames(AgentMode.PLAN,
                List.of("switch_mode", "todo_create", "sessions_spawn", "read_file")))
                .containsExactly("switch_mode", "read_file");
        assertThat(modeRail.allowsToolInPlanMode("read_file")).isTrue();
        assertThat(modeRail.allowsToolInPlanMode("todo_create")).isFalse();
        assertThat(modeRail.allowsToolInPlanMode("unknown_tool")).isFalse();
        assertThat(planningRail.priority()).isEqualTo(90);
        assertThat(planningRail.isEnableProgressRepeat()).isTrue();
        assertThat(planningRail.getListToolCallInterval()).isEqualTo(10);
        assertThat(skillUseRail.priority()).isEqualTo(100);
        assertThat(skillUseRail.describe()).contains("skill");
        assertThat(skillUseRail.skillMode()).isEqualTo("all");
        assertThat(securityRail.allowsDestructiveAction(false)).isFalse();
        assertThat(securityRail.getPriority()).isEqualTo(securityRail.priority());
        assertThat(sessionRail.sessionScope(null)).isEqualTo("default");
        assertThat(subagentRail.describe()).contains("subagent");
        assertThat(sysOperationRail.describe()).contains("sys_operation");
    }

    @Test
    void skillUseRailShouldRegisterSkillToolsAndInjectSkillPrompt() throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot.resolve("migration"));
        Files.writeString(skillsRoot.resolve("migration").resolve("SKILL.md"), """
                ---
                description: Helps migrate rails against Python behavior
                ---

                Read migration reports before changing rails.
                """);
        SkillUseRail rail = new SkillUseRail();
        DeepAgent agent =
            HarnessFactory.createDeepAgent(AgentCard.builder().name("skill-agent").description("Skill agent").build(),
                    DeepAgentConfig.builder().rails(List.of(rail)).skillDirectories(List.of("skills"))
                            .skillMode("auto_list").build(),
                    Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.registeredToolNames()).containsExactlyInAnyOrder("list_skill", "skill_tool");
        assertThat(rail.registeredSkillNames()).containsExactly("migration");
        assertThat(findTool(agent, "list_skill").invoke(Map.of("query", "rails")).toString()).contains("migration")
                .contains("Python behavior");
        assertThat(findTool(agent, "skill_tool").invoke(Map.of("skill_name", "migration")).toString())
                .contains("Read migration reports");

        ModelCallInputs modelInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        rail.beforeModelCall(
                AgentCallbackContext.builder().inputs(modelInputs).extra(new java.util.LinkedHashMap<>()).build());

        assertThat(rail.hasSkillPromptSection()).isTrue();
        assertThat(agent.getAgent().getPromptBuilder().build()).contains("list_skill")
                .contains("Skill name: migration");
        assertThat(modelInputs.getMessages().stream()
                .map(message -> String
                        .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) message).getContent())))
                .anyMatch(content -> content.contains("Skill name: migration"));

        rail.uninit(agent);
        assertThat(rail.registeredToolNames()).isEmpty();
        assertThat(agent.getAgent().getPromptBuilder().hasSection("skills")).isFalse();
    }

    @Test
    void skillUseRailShouldHonorConstructorDirectoriesAndSkillFilters() throws Exception {
        Path primaryRoot = tempDir.resolve("primary-skills");
        Path configRoot = tempDir.resolve("config-skills");
        Files.createDirectories(primaryRoot.resolve("alpha"));
        Files.createDirectories(primaryRoot.resolve("beta"));
        Files.createDirectories(configRoot.resolve("ignored"));
        Files.writeString(primaryRoot.resolve("alpha").resolve("SKILL.md"),
                "---\ndescription: Alpha skill\n---\n# Alpha");
        Files.writeString(primaryRoot.resolve("beta").resolve("SKILL.md"), "---\ndescription: Beta skill\n---\n# Beta");
        Files.writeString(configRoot.resolve("ignored").resolve("SKILL.md"),
                "---\ndescription: Ignored\n---\n# Ignored");

        SkillUseRail rail = new SkillUseRail(List.of("primary-skills"), "all", List.of("alpha,beta"), List.of("beta"));
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("filtered-skill-agent").description("Skill agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).skillDirectories(List.of("config-skills"))
                        .skillMode("auto_list").build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.skillMode()).isEqualTo("all");
        assertThat(rail.configuredSkillDirectories()).containsExactly("primary-skills");
        assertThat(rail.enabledSkills()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(rail.disabledSkills()).containsExactly("beta");
        assertThat(rail.registeredSkillNames()).containsExactly("alpha");
        assertThat(findTool(agent, "list_skill").invoke(Map.of()).toString()).contains("alpha").doesNotContain("beta")
                .doesNotContain("ignored");
        assertThat(findTool(agent, "list_skill").invoke(Map.of("query", "skill")).toString()).contains("alpha")
                .doesNotContain("beta").doesNotContain("ignored");
        assertThat(findTool(agent, "skill_tool").invoke(Map.of("skill_name", "alpha")).toString()).contains("Alpha");
        assertThat(findTool(agent, "skill_tool").invoke(Map.of("skill_name", "beta")).toString())
                .contains("skill is not available");
    }

    @Test
    void skillUseRailShouldSyncRemoteSkillsBeforeRegistration() throws Exception {
        SkillUseRail.RemoteSkillSource source =
            new SkillUseRail.RemoteSkillSource("owner", "repo", "main", "skills", "token");
        RemoteSyncSkillUseRail rail =
            new RemoteSyncSkillUseRail(List.of("synced-skills"), "all", List.of(), List.of(), List.of(source));
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("remote-skill-agent").description("Remote skill agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.syncedSources).containsExactly(source);
        assertThat(rail.registeredSkillNames()).containsExactly("remote-migration");
        assertThat(findTool(agent, "list_skill").invoke(Map.of()).toString()).contains("remote-migration");
        assertThat(findTool(agent, "skill_tool").invoke(Map.of("skill_name", "remote-migration")).toString())
                .contains("Remote workflow");
    }

    @Test
    void skillUseRailShouldRejectInvalidRemoteSkillSources() {
        assertThatThrownBy(() -> new SkillUseRail.RemoteSkillSource("", "repo", "main", "skills", "token"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owner");
        assertThatThrownBy(() -> new SkillUseRail.RemoteSkillSource("owner", "nested/repo", "main", "skills", "token"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repo");
        assertThatThrownBy(() -> new SkillUseRail.RemoteSkillSource("owner", "repo", "main", "../skills", "token"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("directory");
    }

    @Test
    void securityRailShouldEnforceReadOnlyToolCallsThroughCoreCallback() {
        SecurityRail readOnlyRail = new SecurityRail(true);

        assertThat(readOnlyRail.validateReadOnlyToolCall("read_file", Map.of("path", "a.txt")).isSuccess()).isTrue();
        assertThat(readOnlyRail.validateReadOnlyToolCall("write_file", Map.of("path", "a.txt")).isSuccess()).isFalse();
        assertThat(readOnlyRail.validateReadOnlyToolCall("bash", Map.of("command", "cat a.txt")).isSuccess()).isTrue();
        assertThat(readOnlyRail.validateReadOnlyToolCall("bash", Map.of("command", "touch a.txt")).isSuccess())
                .isFalse();

        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-readonly").name("write_file").arguments("{}").build())
                .toolName("write_file").toolArgs(Map.of("path", "a.txt")).build();
        AgentCallbackContext ctx =
            AgentCallbackContext.builder().inputs(inputs).extra(new java.util.LinkedHashMap<>()).build();

        readOnlyRail.beforeToolCall(ctx);

        assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
        assertThat(inputs.getToolResult()).isInstanceOf(ToolOutput.class);
        assertThat(String.valueOf(inputs.getToolMsg().getContent())).contains("read-only agent cannot call write tool");
    }

    @Test
    void migratedRailSkeletonsShouldExposePythonAlignedPublicSurface() {
        HeartbeatRail heartbeatRail = new HeartbeatRail();
        LspRail lspRail = new LspRail();
        McpRail mcpRail = new McpRail();
        ProgressiveToolRail progressiveToolRail =
            new ProgressiveToolRail(List.of("read_file"), List.of("search_tools", "load_tools"), 12);
        TaskCompletionRail completionRail =
            new TaskCompletionRail("Solve: {query}", "DONE", 2, true, 5, Duration.ofSeconds(30));
        ContextAssembleRail assembleRail = new ContextAssembleRail();
        ContextProcessorRail processorRail = new ContextProcessorRail(true, List.of("MessageSummaryOffloader"), true);
        MemoryRail memoryRail = new MemoryRail();
        CodingMemoryRail codingMemoryRail = new CodingMemoryRail();
        ExternalMemoryRail externalMemoryRail = new ExternalMemoryRail();
        VerificationContractRail contractRail = new VerificationContractRail();
        VerificationRail verificationRail = new VerificationRail(Set.of("read_file", "bash"));
        SkillCreateRail skillCreateRail = new SkillCreateRail("skills");
        TeamSkillCreateRail teamSkillCreateRail = new TeamSkillCreateRail("team-skills");
        TeamSkillRail teamSkillRail = new TeamSkillRail("team-skills");

        assertThat(heartbeatRail.priority()).isEqualTo(80);
        assertThat(lspRail.priority()).isEqualTo(60);
        assertThat(lspRail.toolName()).isEqualTo("lsp");
        assertThat(mcpRail.priority()).isEqualTo(95);
        assertThat(mcpRail.toolNames()).containsExactly("list_mcp_resources", "read_mcp_resource");
        assertThat(progressiveToolRail.priority()).isEqualTo(90);
        assertThat(progressiveToolRail.metaToolNames()).containsExactly("search_tools", "load_tools");
        assertThat(progressiveToolRail.getDefaultVisibleTools()).containsExactly("read_file");
        assertThat(progressiveToolRail.getAlwaysVisibleTools()).containsExactlyInAnyOrder("search_tools", "load_tools");
        assertThat(progressiveToolRail.getMaxLoadedTools()).isEqualTo(12);

        assertThat(completionRail.priority()).isEqualTo(10);
        assertThat(completionRail.applyTaskInstruction("ship it", false)).isEqualTo("Solve: ship it");
        assertThat(completionRail.applyTaskInstruction("follow up", true)).isEqualTo("follow up");
        assertThat(completionRail.extractPromiseBlock("ok <promise>DONE with details</promise>"))
                .contains("DONE with details");
        assertThat(completionRail.promiseMatches("ok <promise>DONE with details</promise>")).isTrue();
        assertThat(completionRail.promiseMatches("ok <promise>NOT DONE</promise>")).isFalse();
        assertThat(completionRail.promiseMatches("ok <promise>done\nwith details</promise>")).isTrue();
        assertThat(completionRail.hasCompletionPromise()).isTrue();
        assertThat(completionRail.getRequiredConfirmations()).isEqualTo(2);
        assertThat(completionRail.getMaxRounds()).isEqualTo(5);
        assertThat(completionRail.getTimeout()).isEqualTo(Duration.ofSeconds(30));

        assertThat(assembleRail.priority()).isEqualTo(85);
        assertThat(assembleRail.sectionNames()).containsExactly("workspace", "tools", "context");
        assertThat(processorRail.priority()).isEqualTo(85);
        assertThat(processorRail.isPreset()).isTrue();
        assertThat(processorRail.getProcessorKeys()).containsExactly("MessageSummaryOffloader");
        assertThat(processorRail.isSessionMemoryEnabled()).isTrue();

        assertThat(memoryRail.priority()).isEqualTo(80);
        assertThat(memoryRail.toolNames()).contains("memory_search", "read_memory");
        assertThat(codingMemoryRail.sectionName()).isEqualTo("coding_memory");
        assertThat(externalMemoryRail.priority()).isEqualTo(75);
        assertThat(externalMemoryRail.sectionName()).isEqualTo("external_memory");

        assertThat(contractRail.priority()).isEqualTo(88);
        assertThat(contractRail.requiresVerification(3, false, false)).isTrue();
        assertThat(contractRail.requiresVerification(1, true, false)).isTrue();
        assertThat(contractRail.requiresVerification(1, false, false)).isFalse();
        assertThat(verificationRail.priority()).isEqualTo(90);
        assertThat(verificationRail.allowsTool("read_file")).isTrue();
        assertThat(verificationRail.allowsTool("mcp__server__tool")).isTrue();
        assertThat(verificationRail.allowsTool("edit_file")).isFalse();
        assertThat(skillCreateRail.priority()).isEqualTo(85);
        assertThat(teamSkillCreateRail.priority()).isEqualTo(85);
        assertThat(teamSkillRail.priority()).isEqualTo(80);
    }

    @Test
    void mcpAndLspRailsShouldRegisterHarnessTools() throws Exception {
        McpRail mcpRail = new McpRail();
        LspRail lspRail = new LspRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("mcp-lsp-agent").description("MCP LSP agent").build(),
                DeepAgentConfig.builder().rails(List.of(mcpRail, lspRail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(mcpRail.registeredToolNames()).containsExactly("list_mcp_resources", "read_mcp_resource");
        assertThat(lspRail.isRegistered()).isTrue();
        assertThat(lspRail.hasActiveManager()).isTrue();
        assertThat(lspRail.getManager().getWorkspaceRoot()).isEqualTo(tempDir.toString());
        assertThat(agent.getRegisteredTools().stream().filter(Tool.class::isInstance)
                .map(item -> ((Tool) item).getCard().getName()))
                .contains("list_mcp_resources", "read_mcp_resource", "lsp");

        Tool lspTool = agent.getRegisteredTools().stream().filter(Tool.class::isInstance).map(Tool.class::cast)
                .filter(tool -> "lsp".equals(tool.getCard().getName())).findFirst().orElseThrow();
        ToolOutput output = (ToolOutput) lspTool
                .invoke(Map.of("operation", "goToDefinition", "file_path", "src/Main.java", "line", 1, "character", 1));
        assertThat(output.isSuccess()).isTrue();
        assertThat(String.valueOf(((Map<?, ?>) output.getData()).get("file_path"))).contains(tempDir.toString());

        Path source = tempDir.resolve("src/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Main {}");
        RecordingLspServer server = new RecordingLspServer(
                Map.of("uri", source.toUri().toString(), "range", Map.of("start", Map.of("line", 0, "character", 6))));
        lspRail.getManager().registerServer(source.toString(), server);
        ToolOutput requested = (ToolOutput) lspTool
                .invoke(Map.of("operation", "goToDefinition", "file_path", "src/Main.java", "line", 1, "character", 7));
        assertThat(requested.isSuccess()).isTrue();
        assertThat(server.requests).hasSize(1);
        assertThat(server.requests.get(0).method()).isEqualTo("textDocument/definition");
        assertThat(String.valueOf(((Map<?, ?>) requested.getData()).get("formatted"))).contains("Main.java:1:7");

        lspRail.uninit(agent);
        mcpRail.uninit(agent);
        assertThat(lspRail.isRegistered()).isFalse();
        assertThat(lspRail.hasActiveManager()).isFalse();
        assertThat(mcpRail.registeredToolNames()).isEmpty();
    }

    @Test
    void mcpClientShouldExposeResourceDefaultsForUnsupportedClients() throws Exception {
        McpClient client = new McpClient() {
            @Override
            public boolean connect(int retryTimes, float timeout) {
                return true;
            }

            @Override
            public boolean disconnect(float timeout) {
                return true;
            }

            @Override
            public List<Object> listTools(float timeout) {
                return List.of();
            }

            @Override
            public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
                return Map.of();
            }

            @Override
            public java.util.Optional<Object> getToolInfo(String toolName, float timeout) {
                return java.util.Optional.empty();
            }

            @Override
            public String getServerPath() {
                return "memory";
            }
        };

        assertThat(client.listResources()).isEmpty();
        assertThatThrownBy(() -> client.readResource("res://a")).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("resource read");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void mcpRailShouldListAndReadResourcesFromRealStdioServer() throws Exception {
        String serverId = "stdio-fixture-" + java.util.UUID.randomUUID().toString().replace("-", "");
        McpServerConfig serverConfig = McpServerConfig.builder().serverId(serverId).serverName("stdio-fixture")
                .clientType("stdio").serverPath(javaBin())
                .params(Map.of("command", javaBin(), "args", List.of("-cp", stdioFixtureClasspath(),
                        com.openjiuwen.harness.rails.fixtures.StdioMcpResourceServer.class.getName())))
                .build();
        Runner.resourceMgr().addMcpServer(serverConfig, "mcp-stdio-fixture-test", null);
        try {
            McpRail rail = new McpRail();
            DeepAgent agent = HarnessFactory.createDeepAgent(
                    AgentCard.builder().name("mcp-stdio-agent").description("MCP stdio agent").build(),
                    DeepAgentConfig.builder().rails(List.of(rail)).build(),
                    Workspace.builder().rootPath(tempDir.toString()).language("en").build());
            agent.ensureInitialized();

            ToolOutput listed =
                (ToolOutput) findTool(agent, "list_mcp_resources").invoke(Map.of("server_id", serverId));
            assertThat(listed.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<com.openjiuwen.harness.tools.McpResourceDescriptor> resources =
                (List<com.openjiuwen.harness.tools.McpResourceDescriptor>) listed.getData();
            assertThat(resources).hasSize(1);
            assertThat(resources.get(0).uri()).isEqualTo("memory://fixture/readme");
            assertThat(resources.get(0).name()).isEqualTo("Fixture README");
            assertThat(resources.get(0).mimeType()).isEqualTo("text/plain");
            assertThat(resources.get(0).description()).contains("stdio MCP fixture");

            ToolOutput read = (ToolOutput) findTool(agent, "read_mcp_resource")
                    .invoke(Map.of("server_id", serverId, "uri", "memory://fixture/readme"));
            assertThat(read.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<com.openjiuwen.harness.tools.McpResourceContent> contents =
                (List<com.openjiuwen.harness.tools.McpResourceContent>) read.getData();
            assertThat(contents).hasSize(1);
            assertThat(contents.get(0).uri()).isEqualTo("memory://fixture/readme");
            assertThat(contents.get(0).mimeType()).isEqualTo("text/plain");
            assertThat(contents.get(0).text()).isEqualTo("hello from stdio fixture");
        } finally {
            Runner.resourceMgr().removeMcpServer(serverId, null, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void mcpRailShouldListAndReadResourcesFromHttpTransports() throws Exception {
        for (String clientType : List.of("sse", "streamable-http")) {
            HttpServer server = startHttpMcpResourceServer();
            String serverId =
                clientType.replace("-", "") + "-fixture-" + java.util.UUID.randomUUID().toString().replace("-", "");
            McpServerConfig serverConfig =
                McpServerConfig.builder().serverId(serverId).serverName(clientType + "-fixture").clientType(clientType)
                        .serverPath("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp")
                        .authHeaders(Map.of("X-MCP-Test", clientType)).authQueryParams(Map.of("client", clientType))
                        .build();
            Runner.resourceMgr().addMcpServer(serverConfig, "mcp-http-fixture-test", null);
            try {
                McpRail rail = new McpRail();
                DeepAgent agent = HarnessFactory.createDeepAgent(
                        AgentCard.builder().name(clientType + "-mcp-agent").description("MCP HTTP agent").build(),
                        DeepAgentConfig.builder().rails(List.of(rail)).build(),
                        Workspace.builder().rootPath(tempDir.toString()).language("en").build());
                agent.ensureInitialized();

                ToolOutput listed =
                    (ToolOutput) findTool(agent, "list_mcp_resources").invoke(Map.of("server_id", serverId));
                assertThat(listed.isSuccess()).isTrue();
                @SuppressWarnings("unchecked")
                List<com.openjiuwen.harness.tools.McpResourceDescriptor> resources =
                    (List<com.openjiuwen.harness.tools.McpResourceDescriptor>) listed.getData();
                assertThat(resources).hasSize(1);
                assertThat(resources.get(0).uri()).isEqualTo("memory://" + clientType + "/readme");
                assertThat(resources.get(0).name()).isEqualTo(clientType + " README");
                assertThat(resources.get(0).mimeType()).isEqualTo("text/plain");
                assertThat(resources.get(0).description()).contains(clientType, "MCP fixture");

                ToolOutput read = (ToolOutput) findTool(agent, "read_mcp_resource")
                        .invoke(Map.of("server_id", serverId, "uri", "memory://" + clientType + "/readme"));
                assertThat(read.isSuccess()).isTrue();
                @SuppressWarnings("unchecked")
                List<com.openjiuwen.harness.tools.McpResourceContent> contents =
                    (List<com.openjiuwen.harness.tools.McpResourceContent>) read.getData();
                assertThat(contents).hasSize(1);
                assertThat(contents.get(0).uri()).isEqualTo("memory://" + clientType + "/readme");
                assertThat(contents.get(0).mimeType()).isEqualTo("text/plain");
                assertThat(contents.get(0).text()).isEqualTo("hello from " + clientType + " fixture");
            } finally {
                Runner.resourceMgr().removeMcpServer(serverId, null, null, TagMatchStrategy.ALL, true);
                server.stop(0);
            }
        }
    }

    @Test
    void memoryRailShouldRegisterToolsInitializeManagerAndInjectPrompt() throws Exception {
        MemoryRail rail = new MemoryRail();
        DeepAgent agent =
            HarnessFactory.createDeepAgent(AgentCard.builder().name("memory-agent").description("Memory agent").build(),
                    DeepAgentConfig.builder().rails(List.of(rail)).build(),
                    Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.registeredToolNames()).contains("memory_search", "memory_get", "write_memory", "edit_memory",
                "read_memory");

        Object writeResult = findTool(agent, "write_memory").invoke(Map.of("path", "MEMORY.md", "content",
                "The project uses a migration-first workflow.", "append", false));
        assertThat(String.valueOf(writeResult)).contains("success=true");

        Object readResult = findTool(agent, "read_memory").invoke(Map.of("path", "MEMORY.md"));
        assertThat(String.valueOf(readResult)).contains("migration-first workflow");

        rail.beforeInvoke(
                AgentCallbackContext.builder().agent(agent.getAgent()).extra(new java.util.LinkedHashMap<>()).build());
        assertThat(rail.isManagerInitialized()).isTrue();

        ModelCallInputs modelInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        AgentCallbackContext modelCtx = AgentCallbackContext.builder().inputs(modelInputs)
                .extra(new java.util.LinkedHashMap<>(Map.of("run_kind", "heartbeat"))).build();
        rail.beforeModelCall(modelCtx);

        assertThat(rail.hasMemoryPromptSection()).isTrue();
        assertThat(modelInputs.getMessages().stream()
                .map(message -> String
                        .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) message).getContent())))
                .anyMatch(content -> content.contains("Memory is read-only"));
    }

    @Test
    void codingMemoryRailShouldRegisterToolsMaintainIndexAndInjectPrompt() throws Exception {
        CodingMemoryRail rail = new CodingMemoryRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("coding-memory-agent").description("Coding memory agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("cn").build());
        agent.ensureInitialized();

        assertThat(rail.registeredToolNames()).containsExactlyInAnyOrder("coding_memory_read", "coding_memory_write",
                "coding_memory_edit");

        Object writeResult = findTool(agent, "coding_memory_write").invoke(Map.of("path", "migration.md", "content", """
                ---
                name: Harness migration
                description: Rails migrate against Python behavior
                type: project
                ---

                Harness rails should fill bottom dependencies before upper behavior.
                """));
        assertThat(String.valueOf(writeResult)).contains("success=true");
        assertThat(Files.readString(tempDir.resolve("coding_memory").resolve("MEMORY.md")))
                .contains("Harness migration").contains("migration.md");

        ModelCallInputs modelInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        AgentCallbackContext modelCtx =
            AgentCallbackContext.builder().inputs(modelInputs).extra(new java.util.LinkedHashMap<>()).build();
        rail.beforeModelCall(modelCtx);

        assertThat(rail.hasMemoryPromptSection()).isTrue();
        assertThat(modelInputs.getMessages().stream()
                .map(message -> String
                        .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) message).getContent())))
                .anyMatch(content -> content.contains("编码记忆"))
                .anyMatch(content -> content.contains("Harness migration"));
    }

    @Test
    void contextAssembleRailShouldInjectWorkspaceToolsAndContextSections() throws Exception {
        Files.createDirectories(tempDir.resolve("context"));
        Files.writeString(tempDir.resolve("context").resolve("brief.md"),
                "Use the migration report as source of truth.");
        ContextAssembleRail rail = new ContextAssembleRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("context-assemble-agent").description("Context assemble agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ModelCallInputs inputs =
            ModelCallInputs.builder().messages(new java.util.ArrayList<>()).tools(new java.util.ArrayList<>(
                    List.of(ToolInfo.builder().name("read_file").description("Read a file").build()))).build();
        rail.beforeModelCall(
                AgentCallbackContext.builder().inputs(inputs).extra(new java.util.LinkedHashMap<>()).build());

        assertThat(rail.hasContextSections()).isTrue();
        assertThat(inputs.getMessages()).hasSize(3);
        String rendered = inputs.getMessages().stream()
                .map(message -> String
                        .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) message).getContent()))
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(rendered).contains("Workspace").contains("Available Tools").contains("read_file")
                .contains("Context Files").contains("migration report");
    }

    @Test
    void contextProcessorRailShouldInstallProcessorsInjectOffloadAndRepairToolContext() throws Exception {
        ContextProcessorRail rail = new ContextProcessorRail(true, List.of("ToolResultBudgetProcessor"), false);
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().modelName("ctx-test-model").build();
        ModelClientConfig modelClientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("test-key")
                .apiBase("http://test.local").verifySsl(false).build();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("context-processor-agent").description("Context processor agent").build(),
                DeepAgentConfig.builder().model(modelConfig).backend(modelClientConfig).rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.installedProcessors()).extracting(ContextEngine.ProcessorSpec::processorType).contains(
                "MessageSummaryOffloader", "DialogueCompressor", "CurrentRoundCompressor", "RoundLevelCompressor",
                "ToolResultBudgetProcessor");
        CurrentRoundCompressorConfig currentRoundConfig =
            rail.installedProcessors().stream().filter(spec -> "CurrentRoundCompressor".equals(spec.processorType()))
                    .map(spec -> (CurrentRoundCompressorConfig) spec.config()).findFirst().orElseThrow();
        RoundLevelCompressorConfig roundLevelConfig =
            rail.installedProcessors().stream().filter(spec -> "RoundLevelCompressor".equals(spec.processorType()))
                    .map(spec -> (RoundLevelCompressorConfig) spec.config()).findFirst().orElseThrow();
        assertThat(currentRoundConfig.getModel()).isSameAs(modelConfig);
        assertThat(currentRoundConfig.getModelClient()).isSameAs(modelClientConfig);
        assertThat(roundLevelConfig.getModel()).isSameAs(modelConfig);
        assertThat(roundLevelConfig.getModelClient()).isSameAs(modelClientConfig);
        assertThat(roundLevelConfig.getTargetTotalTokens()).isEqualTo(160000);
        assertThat(roundLevelConfig.getKeepRecentMessages()).isEqualTo(6);

        rail.beforeModelCall(AgentCallbackContext.builder().extra(new java.util.LinkedHashMap<>()).build());
        assertThat(rail.hasOffloadPromptSection()).isTrue();

        TestSession session = new TestSession("context-repair-session");
        ModelContext context = new ContextEngine().createContext("repair", session);
        context.addMessages(new com.openjiuwen.core.foundation.llm.schema.UserMessage("run a tool"));
        context.addMessages(new com.openjiuwen.core.foundation.llm.schema.AssistantMessage("assistant", "", null, null,
                List.of(ToolCall.builder().id("tc-missing").name("grep").arguments("not-json").build()), null, null,
                null, null));

        rail.beforeInvoke(
                AgentCallbackContext.builder().context(context).extra(new java.util.LinkedHashMap<>()).build());

        List<com.openjiuwen.core.foundation.llm.schema.BaseMessage> repaired = context.getMessages();
        assertThat(repaired).hasSize(3);
        assertThat(((com.openjiuwen.core.foundation.llm.schema.AssistantMessage) repaired.get(1)).getToolCalls().get(0)
                .getArguments()).isEqualTo("{}");
        assertThat(repaired.get(2)).isInstanceOf(com.openjiuwen.core.foundation.llm.schema.ToolMessage.class);
        assertThat(String.valueOf(repaired.get(2).getContent())).contains("Tool execution interrupted");
    }

    @Test
    void externalMemoryRailShouldRegisterProviderToolsPrefetchAndSync() throws Exception {
        FakeMemoryProvider provider = new FakeMemoryProvider();
        ExternalMemoryRail rail = new ExternalMemoryRail(provider, "user-1", "scope-1", "session-1");
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("external-memory-agent").description("External memory agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.registeredToolNames()).containsExactly("ltm_search");
        assertThat(agent.getAgent().getPromptBuilder().hasSection("external_memory")).isTrue();

        Object toolResult = findTool(agent, "ltm_search").invoke(Map.of("query", "migration"));
        assertThat(String.valueOf(toolResult)).contains("migration");

        InvokeInputs beforeInvokeInputs =
            InvokeInputs.builder().query("what did we decide").conversationId("session-1").build();
        rail.beforeInvoke(AgentCallbackContext.builder().inputs(beforeInvokeInputs)
                .extra(new java.util.LinkedHashMap<>()).build());
        assertThat(rail.isInitialized()).isTrue();
        assertThat(provider.isInitialized()).isTrue();

        ModelCallInputs modelInputs = ModelCallInputs.builder()
                .messages(new java.util.ArrayList<>(
                        List.of(new com.openjiuwen.core.foundation.llm.schema.UserMessage("what did we decide"))))
                .build();
        rail.beforeModelCall(
                AgentCallbackContext.builder().inputs(modelInputs).extra(new java.util.LinkedHashMap<>()).build());

        assertThat(rail.hasPrefetchPromptSection()).isTrue();
        assertThat(provider.prefetchQueries).containsExactly("what did we decide");
        assertThat(modelInputs.getMessages().stream()
                .map(message -> String
                        .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) message).getContent())))
                .anyMatch(content -> content.contains("remembered context"));

        rail.afterInvoke(AgentCallbackContext.builder()
                .inputs(InvokeInputs.builder().query("what did we decide")
                        .result(Map.of("output", "we decided to fill lower layers first")).build())
                .extra(new java.util.LinkedHashMap<>()).build());
        assertThat(provider.syncCalls).singleElement().satisfies(
                call -> assertThat(call).containsEntry("assistant", "we decided to fill lower layers first"));
    }

    @Test
    void heartbeatRailShouldInjectPromptOnlyForHeartbeatRuns() throws Exception {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("heartbeat-agent").description("Heartbeat agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        Files.writeString(tempDir.resolve("HEARTBEAT.md"), """
                <!-- internal note -->
                Check stale task A

                Check stale task B
                """);

        ModelCallInputs normalInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        AgentCallbackContext normalCtx = AgentCallbackContext.builder().inputs(normalInputs)
                .extra(new java.util.LinkedHashMap<>(Map.of("run_kind", "outer_loop"))).build();
        rail.beforeModelCall(normalCtx);
        assertThat(rail.hasHeartbeatPromptSection()).isFalse();
        assertThat(normalInputs.getMessages()).isEmpty();

        ModelCallInputs heartbeatInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        AgentCallbackContext heartbeatCtx = AgentCallbackContext.builder().inputs(heartbeatInputs)
                .extra(new java.util.LinkedHashMap<>(Map.of("run_kind", "heartbeat"))).build();
        rail.beforeModelCall(heartbeatCtx);
        rail.beforeModelCall(heartbeatCtx);

        assertThat(rail.hasHeartbeatPromptSection()).isTrue();
        assertThat(heartbeatInputs.getMessages()).hasSize(1);
        String prompt = String
                .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) heartbeatInputs.getMessages().get(0))
                        .getContent());
        assertThat(prompt).contains("Heartbeat").contains("Check stale task A").contains("Check stale task B")
                .contains("HEARTBEAT_OK").doesNotContain("internal note");
    }

    @Test
    void agentModeRailShouldRegisterModeToolsAndDriveStateSwitch() throws Exception {
        AgentModeRail rail = new AgentModeRail();
        DeepAgent agent =
            HarnessFactory.createDeepAgent(AgentCard.builder().name("mode-agent").description("Mode agent").build(),
                    DeepAgentConfig.builder().rails(List.of(rail)).build(),
                    Workspace.builder().rootPath(tempDir.toString()).language("en").build());

        agent.ensureInitialized();

        assertThat(rail.registeredToolNames()).containsExactly("switch_mode", "enter_plan_mode", "exit_plan_mode");
        assertThat(agent.getRegisteredTools()).hasSize(3);
        assertThat(rail.validateToolCall(AgentMode.NORMAL, "exit_plan_mode", Map.of(), null).isSuccess()).isFalse();
        assertThat(rail.validateToolCall(AgentMode.PLAN, "todo_modify", Map.of(), null).isSuccess()).isFalse();

        ToolOutput enter = (ToolOutput) agent.getRegisteredTools().stream()
                .map(item -> (com.openjiuwen.core.foundation.tool.Tool) item)
                .filter(tool -> "enter_plan_mode".equals(tool.getCard().getName())).findFirst().orElseThrow()
                .invoke(Map.of("conversation_id", "session-1"));
        assertThat(enter.isSuccess()).isTrue();
        assertThat(agent.getCurrentMode()).isEqualTo(AgentMode.PLAN);
        assertThat(((Map<?, ?>) enter.getData()).get("plan_file_path")).isNotNull();
        Path planPath = Path.of(String.valueOf(((Map<?, ?>) enter.getData()).get("plan_file_path")));
        assertThat(Files.exists(planPath)).isTrue();
        assertThat(rail.allowsWriteTarget("write_file", planPath.toString(), planPath)).isTrue();
        assertThat(rail.allowsWriteTarget("write_file", tempDir.resolve("other.md").toString(), planPath)).isFalse();
        assertThat(rail.validateToolCall(AgentMode.PLAN, "write_file",
                Map.of("file_path", tempDir.resolve("other.md").toString()), planPath).isSuccess()).isFalse();

        ToolOutput exit = (ToolOutput) agent.getRegisteredTools().stream()
                .map(item -> (com.openjiuwen.core.foundation.tool.Tool) item)
                .filter(tool -> "exit_plan_mode".equals(tool.getCard().getName())).findFirst().orElseThrow()
                .invoke(Map.of());
        assertThat(exit.isSuccess()).isTrue();
        assertThat(agent.getCurrentMode()).isEqualTo(AgentMode.NORMAL);
        assertThat(((Map<?, ?>) exit.getData()).get("plan_file_path")).isEqualTo(planPath.toString());
        assertThat(((Map<?, ?>) exit.getData()).get("plan_content")).isEqualTo("# Plan\n");

        rail.uninit(agent);
        assertThat(agent.getRegisteredTools()).isEmpty();
    }

    @Test
    void agentModeRailShouldOwnTaskToolOnlyDuringPlanModeWhenSubagentsExist() throws Exception {
        AgentModeRail rail = new AgentModeRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("mode-task-agent").description("Mode task agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail))
                        .subagents(
                                List.of(com.openjiuwen.harness.subagents.PlanAgentFactory.buildPlanAgentConfig("en")))
                        .build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.ownsTaskTool()).isFalse();

        ToolOutput enter = (ToolOutput) agent.getRegisteredTools().stream()
                .map(item -> (com.openjiuwen.core.foundation.tool.Tool) item)
                .filter(tool -> "enter_plan_mode".equals(tool.getCard().getName())).findFirst().orElseThrow()
                .invoke(Map.of("conversation_id", "session-task-tool"));

        assertThat(enter.isSuccess()).isTrue();
        assertThat(rail.ownsTaskTool()).isTrue();
        assertThat(agent.getRegisteredTools().stream()
                .map(item -> ((com.openjiuwen.core.foundation.tool.Tool) item).getCard().getName()))
                .contains("task_tool");

        ToolOutput exit = (ToolOutput) agent.getRegisteredTools().stream()
                .map(item -> (com.openjiuwen.core.foundation.tool.Tool) item)
                .filter(tool -> "exit_plan_mode".equals(tool.getCard().getName())).findFirst().orElseThrow()
                .invoke(Map.of());

        assertThat(exit.isSuccess()).isTrue();
        assertThat(rail.ownsTaskTool()).isFalse();
        assertThat(agent.getRegisteredTools().stream()
                .map(item -> ((com.openjiuwen.core.foundation.tool.Tool) item).getCard().getId()))
                .noneMatch(id -> id.contains(".plan_mode.task_tool"));
        assertThat(agent.getRegisteredTools().stream()
                .map(item -> ((com.openjiuwen.core.foundation.tool.Tool) item).getCard().getName()))
                .contains("task_tool");
    }

    @Test
    void agentModeRailAfterToolCallShouldManageOwnedTaskToolAndRespectSkip() throws Exception {
        AgentModeRail rail = new AgentModeRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("mode-after-tool-agent").description("Mode after tool agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail))
                        .subagents(
                                List.of(com.openjiuwen.harness.subagents.PlanAgentFactory.buildPlanAgentConfig("en")))
                        .build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ToolCallInputs enterInputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-enter-after").name("enter_plan_mode").arguments("{}").build())
                .toolName("enter_plan_mode").toolArgs(Map.of()).build();
        AgentCallbackContext skippedEnter = AgentCallbackContext.builder().inputs(enterInputs)
                .extra(new java.util.LinkedHashMap<>(Map.of("_skip_tool", Boolean.TRUE))).build();

        rail.afterToolCall(skippedEnter);
        assertThat(rail.ownsTaskTool()).isFalse();

        AgentCallbackContext enterCtx =
            AgentCallbackContext.builder().inputs(enterInputs).extra(new java.util.LinkedHashMap<>()).build();
        rail.afterToolCall(enterCtx);

        assertThat(rail.ownsTaskTool()).isTrue();
        assertThat(agent.getRegisteredTools().stream()
                .map(item -> ((com.openjiuwen.core.foundation.tool.Tool) item).getCard().getName()))
                .contains("task_tool");

        ToolCallInputs exitInputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-exit-after").name("exit_plan_mode").arguments("{}").build())
                .toolName("exit_plan_mode").toolArgs(Map.of()).build();
        AgentCallbackContext exitCtx =
            AgentCallbackContext.builder().inputs(exitInputs).extra(new java.util.LinkedHashMap<>()).build();
        rail.afterToolCall(exitCtx);

        assertThat(rail.ownsTaskTool()).isFalse();
        assertThat(agent.getRegisteredTools().stream()
                .map(item -> ((com.openjiuwen.core.foundation.tool.Tool) item).getCard().getId()))
                .noneMatch(id -> id.contains(".plan_mode.task_tool"));
    }

    @Test
    void agentModeRailShouldApplyPlanToolValidationThroughCoreCallback() throws Exception {
        AgentModeRail rail = new AgentModeRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("mode-callback-agent").description("Mode callback agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ToolOutput enter = (ToolOutput) agent.getRegisteredTools().stream()
                .map(item -> (com.openjiuwen.core.foundation.tool.Tool) item)
                .filter(tool -> "enter_plan_mode".equals(tool.getCard().getName())).findFirst().orElseThrow()
                .invoke(Map.of("conversation_id", "session-callback"));
        assertThat(enter.isSuccess()).isTrue();

        Path otherPath = tempDir.resolve("other.md");
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-plan-mode").name("write_file").arguments("{}").build())
                .toolName("write_file").toolArgs(Map.of("file_path", otherPath.toString())).build();
        AgentCallbackContext ctx =
            AgentCallbackContext.builder().inputs(inputs).extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeToolCall(ctx);

        assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
        assertThat(inputs.getToolResult()).isInstanceOf(ToolOutput.class);
        assertThat(String.valueOf(inputs.getToolMsg().getContent()))
                .contains("write/edit can only target the plan file");
    }

    @Test
    void agentModeRailShouldInjectAndRemovePlanPromptSectionAroundModelCall() throws Exception {
        AgentModeRail rail = new AgentModeRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("mode-prompt-agent").description("Mode prompt agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ToolOutput enter = (ToolOutput) agent.getRegisteredTools().stream()
                .map(item -> (com.openjiuwen.core.foundation.tool.Tool) item)
                .filter(tool -> "enter_plan_mode".equals(tool.getCard().getName())).findFirst().orElseThrow()
                .invoke(Map.of("conversation_id", "session-prompt"));
        assertThat(enter.isSuccess()).isTrue();

        ModelCallInputs inputs = ModelCallInputs.builder()
                .messages(new java.util.ArrayList<>(
                        List.of(new com.openjiuwen.core.foundation.llm.schema.SystemMessage("base"))))
                .tools(new java.util.ArrayList<>(List.of(ToolInfo.builder().name("todo_create").build(),
                        ToolInfo.builder().name("sessions_spawn").build(),
                        ToolInfo.builder().name("read_file").build())))
                .build();
        AgentCallbackContext ctx =
            AgentCallbackContext.builder().inputs(inputs).extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeModelCall(ctx);

        assertThat(rail.hasPlanModePromptSection()).isTrue();
        assertThat(inputs.getMessages()).hasSize(2);
        assertThat(String.valueOf(
                ((com.openjiuwen.core.foundation.llm.schema.BaseMessage) inputs.getMessages().get(0)).getContent()))
                .contains("Plan mode is active.").contains("Active plan file:");
        assertThat(inputs.getTools()).extracting(ToolInfo::getName).containsExactly("read_file");

        rail.afterModelCall(ctx);

        assertThat(rail.hasPlanModePromptSection()).isFalse();
    }

    @Test
    void taskPlanningRailShouldInjectProgressReminderAfterConfiguredToolInterval() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail(true, 2);
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-progress-agent").description("Planning progress agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        TestSession session = new TestSession("progress-session");
        ModelContext context = new ContextEngine().createContext("progress-context", session);

        com.openjiuwen.harness.tools.TodoTool todoTool =
            new com.openjiuwen.harness.tools.TodoTool(tempDir.resolve(".todo").toString());
        todoTool.save(session.getSessionId(),
                new ArrayList<>(List.of(
                        TodoItem.builder().id("task-a").content("Read code").status(TodoStatus.TODO).build(),
                        TodoItem.builder().id("task-b").content("Patch rail").status(TodoStatus.IN_PROGRESS).build())));

        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-progress").name("read_file").arguments("{}").build())
                .toolName("read_file").toolArgs(Map.of()).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().inputs(inputs).session(session).context(context)
                .extra(new java.util.LinkedHashMap<>()).build();

        rail.afterToolCall(ctx);
        assertThat(rail.toolCallCount(session.getSessionId())).isEqualTo(1);
        assertThat(context.getMessages()).isEmpty();

        rail.afterToolCall(ctx);

        assertThat(rail.toolCallCount(session.getSessionId())).isEqualTo(2);
        assertThat(context.getMessages()).hasSize(1);
        assertThat(String.valueOf(context.getMessages().get(0).getContent())).contains("current task plan")
                .contains("Patch rail");

        rail.afterInvoke(ctx);
        assertThat(rail.toolCallCount(session.getSessionId())).isZero();
    }

    @Test
    void taskPlanningRailShouldExposeTodoGetAndModifyTools() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-todo-agent").description("Planning todo agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        assertThat(rail.registeredToolNames()).containsExactlyInAnyOrder("todo_create", "todo_list", "todo_get",
                "todo_modify");

        ToolOutput create = (ToolOutput) findTool(agent, "todo_create")
                .invoke(Map.of("session_id", "todo-session", "tasks", List.of("Read code", "Patch rail")));
        assertThat(create.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<TodoItem> created = (List<TodoItem>) create.getData();
        String firstId = created.get(0).getId();

        ToolOutput modify = (ToolOutput) findTool(agent, "todo_modify")
                .invoke(Map.of("session_id", "todo-session", "updates", List.of(Map.of("task_id", firstId, "content",
                        "Read code deeply", "status", "in_progress", "priority", "high"))));
        assertThat(modify.isSuccess()).isTrue();

        ToolOutput get = (ToolOutput) findTool(agent, "todo_list").invoke(Map.of("session_id", "todo-session"));
        assertThat(get.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<TodoItem> todos = (List<TodoItem>) get.getData();
        assertThat(todos).extracting(TodoItem::getContent).contains("Read code deeply");
        assertThat(todos.stream().filter(item -> item.getId().equals(firstId)).findFirst().orElseThrow().getStatus())
                .isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(todos.stream().filter(item -> item.getId().equals(firstId)).findFirst().orElseThrow().getPriority())
                .isEqualTo("high");

        ToolOutput getOne =
            (ToolOutput) findTool(agent, "todo_get").invoke(Map.of("session_id", "todo-session", "id", firstId));
        assertThat(getOne.isSuccess()).isTrue();
        assertThat((TodoItem) getOne.getData()).extracting(TodoItem::getId).isEqualTo(firstId);
    }

    @Test
    void taskPlanningRailTodoToolsShouldUseExecutionSessionWhenArgumentsOmitSessionId() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-session-agent").description("Planning session agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        TestSession firstSession = new TestSession("todo-session-a");
        TestSession secondSession = new TestSession("todo-session-b");
        Map<String, Object> firstContext = Map.of("session", firstSession);
        Map<String, Object> secondContext = Map.of("session", secondSession);

        ToolOutput firstCreate = (ToolOutput) findTool(agent, "todo_create")
                .invoke(Map.of("tasks", List.of("First session task")), firstContext);
        ToolOutput secondCreate = (ToolOutput) findTool(agent, "todo_create")
                .invoke(Map.of("tasks", List.of("Second session task")), secondContext);
        assertThat(firstCreate.isSuccess()).isTrue();
        assertThat(secondCreate.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        List<TodoItem> firstTodos = (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_list")
                .invoke(Map.of(), firstContext)).getData();
        @SuppressWarnings("unchecked")
        List<TodoItem> secondTodos = (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_list")
                .invoke(Map.of(), secondContext)).getData();
        assertThat(firstTodos).extracting(TodoItem::getContent).containsExactly("First session task");
        assertThat(secondTodos).extracting(TodoItem::getContent).containsExactly("Second session task");

        String firstTaskId = firstTodos.get(0).getId();
        ToolOutput modify = (ToolOutput) findTool(agent, "todo_modify")
                .invoke(Map.of("updates", List.of(Map.of("task_id", firstTaskId, "status", "completed"))),
                        firstContext);
        ToolOutput get = (ToolOutput) findTool(agent, "todo_get").invoke(Map.of("id", firstTaskId), firstContext);
        assertThat(modify.isSuccess()).isTrue();
        assertThat((TodoItem) get.getData()).extracting(TodoItem::getStatus).isEqualTo(TodoStatus.COMPLETED);
        assertThat(tempDir.resolve(".todo/todo-session-a/todo.json")).exists();
        assertThat(tempDir.resolve(".todo/todo-session-b/todo.json")).exists();
        assertThat(tempDir.resolve(".todo/default/todo.json")).doesNotExist();
    }

    @Test
    void taskPlanningRailShouldCreateStructuredTodoItems() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-structured-todo-agent").description("Planning structured todo agent")
                        .build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ToolOutput create =
            (ToolOutput) findTool(agent, "todo_create").invoke(Map.of("session_id", "structured-session", "tasks",
                    List.of(Map.of("id", "task-1", "content", "Read code", "activeForm", "Reading code", "description",
                            "Inspect harness rails", "selected_model_id", "fast", "depends_on", List.of("setup"),
                            "result_summary", "not started", "meta_data", Map.of("area", "rails")),
                            Map.of("id", "task-2", "content", "Patch rail", "activeForm", "Patching rail",
                                    "description", "Apply compatibility change"))));

        assertThat(create.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<TodoItem> created = (List<TodoItem>) create.getData();
        assertThat(created).hasSize(2);
        assertThat(created.get(0).getId()).isEqualTo("task-1");
        assertThat(created.get(0).getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(created.get(0).getActiveForm()).isEqualTo("Reading code");
        assertThat(created.get(0).getDescription()).isEqualTo("Inspect harness rails");
        assertThat(created.get(0).getSelectedModelId()).isEqualTo("fast");
        assertThat(created.get(0).getDependsOn()).containsExactly("setup");
        assertThat(created.get(0).getResultSummary()).isEqualTo("not started");
        assertThat(created.get(0).getMetaData()).containsEntry("area", "rails");
        assertThat(created.get(1).getStatus()).isEqualTo(TodoStatus.PENDING);

        @SuppressWarnings("unchecked")
        List<TodoItem> loaded = (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_list")
                .invoke(Map.of("session_id", "structured-session"))).getData();
        assertThat(loaded).extracting(TodoItem::getId).containsExactly("task-1", "task-2");
    }

    @Test
    void taskPlanningRailShouldSupportActionBasedTodoModify() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-action-todo-agent").description("Planning action todo agent")
                        .build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        findTool(agent, "todo_create").invoke(Map.of("session_id", "action-session", "tasks",
                List.of(Map.of("id", "task-1", "content", "Read code", "activeForm", "Reading code", "description",
                        "Inspect code"),
                        Map.of("id", "task-2", "content", "Patch rail", "activeForm", "Patching rail", "description",
                                "Apply patch"))));

        ToolOutput update = (ToolOutput) findTool(agent, "todo_modify")
                .invoke(Map.of("session_id", "action-session", "action", "update", "todos", List.of(Map.of("id",
                        "task-2", "status", "completed", "result_summary", "patched", "selected_model_id", "smart"))));
        assertThat(update.isSuccess()).isTrue();

        ToolOutput append = (ToolOutput) findTool(agent, "todo_modify").invoke(Map.of("session_id", "action-session",
                "action", "append", "todos", List.of(Map.of("id", "task-3", "content", "Verify", "activeForm",
                        "Verifying", "description", "Run tests", "status", "pending"))));
        assertThat(append.isSuccess()).isTrue();

        ToolOutput insert = (ToolOutput) findTool(agent, "todo_modify")
                .invoke(Map.of("session_id", "action-session", "action", "insert_before", "todo_data",
                        Map.of("target_id", "task-3", "items",
                                List.of(Map.of("id", "task-2b", "content", "Review", "activeForm", "Reviewing",
                                        "description", "Review the patch", "status", "pending", "selected_model_id",
                                        "fast")))));
        assertThat(insert.isSuccess()).isTrue();

        ToolOutput cancel = (ToolOutput) findTool(agent, "todo_modify")
                .invoke(Map.of("session_id", "action-session", "action", "cancel", "ids", List.of("task-3")));
        assertThat(cancel.isSuccess()).isTrue();

        ToolOutput delete = (ToolOutput) findTool(agent, "todo_modify")
                .invoke(Map.of("session_id", "action-session", "action", "delete", "ids", List.of("task-1")));
        assertThat(delete.isSuccess()).isTrue();

        TodoItem task2 = (TodoItem) ((ToolOutput) findTool(agent, "todo_get")
                .invoke(Map.of("session_id", "action-session", "id", "task-2"))).getData();
        TodoItem task2b = (TodoItem) ((ToolOutput) findTool(agent, "todo_get")
                .invoke(Map.of("session_id", "action-session", "id", "task-2b"))).getData();
        TodoItem task3 = (TodoItem) ((ToolOutput) findTool(agent, "todo_get")
                .invoke(Map.of("session_id", "action-session", "id", "task-3"))).getData();
        assertThat(task2.getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(task2.getResultSummary()).isEqualTo("patched");
        assertThat(task2.getSelectedModelId()).isEqualTo("smart");
        assertThat(task2b.getSelectedModelId()).isEqualTo("fast");
        assertThat(task3.getStatus()).isEqualTo(TodoStatus.CANCELLED);

        @SuppressWarnings("unchecked")
        List<TodoItem> activeTodos =
            (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_list").invoke(Map.of("session_id", "action-session")))
                    .getData();
        assertThat(activeTodos).extracting(TodoItem::getId).containsExactly("task-2b");
    }

    @Test
    void taskPlanningRailShouldRejectMultipleInProgressTodos() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-invalid-todo-agent").description("Planning invalid todo agent")
                        .build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        findTool(agent, "todo_create").invoke(Map.of("session_id", "invalid-action-session", "tasks",
                List.of(Map.of("id", "task-1", "content", "Read code", "activeForm", "Reading code", "description",
                        "Inspect code"),
                        Map.of("id", "task-2", "content", "Patch rail", "activeForm", "Patching rail", "description",
                                "Apply patch"))));

        ToolOutput output =
            (ToolOutput) findTool(agent, "todo_modify").invoke(Map.of("session_id", "invalid-action-session", "action",
                    "update", "todos", List.of(Map.of("id", "task-2", "status", "in_progress"))));

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("More than one task is marked as 'in_progress'");
    }

    @Test
    void taskPlanningRailShouldInjectTodoPromptBeforeModelCall() {
        TaskPlanningRail rail = new TaskPlanningRail(false, 20, Map.of("fast", "cheap model"));
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-prompt-agent").description("Planning prompt agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ModelCallInputs inputs = ModelCallInputs.builder().messages(new ArrayList<>()).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().inputs(inputs).build();

        rail.beforeModelCall(ctx);
        rail.beforeModelCall(ctx);

        assertThat(rail.hasTodoPromptSection()).isTrue();
        assertThat(agent.getAgent().getPromptBuilder().build()).contains("todo_create")
                .contains("Model Selection Strategy").contains("selected_model_id: fast: cheap model");
        assertThat(inputs.getMessages()).hasSize(1);
        assertThat(String.valueOf(
                ((com.openjiuwen.core.foundation.llm.schema.SystemMessage) inputs.getMessages().get(0)).getContent()))
                .contains("todo_create").contains("selected_model_id: fast: cheap model");
    }

    @Test
    void taskPlanningRailShouldWarnWhenNoModelSelectionConfigured() {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-no-model-prompt-agent").description("Planning no model prompt agent")
                        .build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        ModelCallInputs inputs = ModelCallInputs.builder().messages(new ArrayList<>()).build();

        rail.beforeModelCall(AgentCallbackContext.builder().inputs(inputs).build());

        assertThat(rail.hasTodoPromptSection()).isTrue();
        assertThat(agent.getAgent().getPromptBuilder().build()).contains("Model Selection Note")
                .contains("do NOT use the selected_model_id field");
        assertThat(String.valueOf(
                ((com.openjiuwen.core.foundation.llm.schema.SystemMessage) inputs.getMessages().get(0)).getContent()))
                .contains("Model Selection Note");
    }

    @Test
    void taskPlanningRailShouldWriteTaskPlanSnapshotAfterTaskIteration() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-snapshot-agent").description("Planning snapshot agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        findTool(agent, "todo_create")
                .invoke(Map.of("session_id", "snapshot-session", "tasks", List.of("Read code", "Patch rail")));

        rail.afterTaskIteration(
                TaskIterationContext.builder().agent(agent).round(3).followUp(true)
                        .inputs(Map.of("task_id", "task-3", "conversation_id", "snapshot-session"))
                        .result(Map.of("output", "done", "usage_metadata",
                                UsageMetadata.builder().inputTokens(5).outputTokens(7).totalTokens(12).build()))
                        .build());

        Path snapshot = rail.taskPlanPath("snapshot-session");
        assertThat(snapshot).isNotNull();
        assertThat(Files.exists(snapshot)).isTrue();
        String content = Files.readString(snapshot);
        assertThat(content).contains("\"session_id\":\"snapshot-session\"").contains("\"round\":3")
                .contains("\"follow_up\":true").contains("Read code").contains("Patch rail")
                .contains("\"output\":\"done\"").contains("\"token_usage\":12").contains("\"total_tokens\":12");

        com.openjiuwen.harness.task_loop.TaskPlanSnapshot loadedSnapshot =
            rail.loadTaskPlanSnapshot("snapshot-session");
        TaskPlan loadedPlan = rail.loadPersistedTaskPlan("snapshot-session");
        assertThat(loadedSnapshot).isNotNull();
        assertThat(loadedSnapshot.getSessionId()).isEqualTo("snapshot-session");
        assertThat(loadedSnapshot.getRound()).isEqualTo(3);
        assertThat(loadedSnapshot.isFollowUp()).isTrue();
        assertThat(loadedSnapshot.getTokenUsage()).isEqualTo(12);
        assertThat(loadedSnapshot.getUsageMetadata().getTotalTokens()).isEqualTo(12);
        assertThat(loadedPlan.getTasks()).extracting(TodoItem::getContent).containsExactly("Read code", "Patch rail");
    }

    @Test
    void taskPlanningRailShouldSyncTodoStatusFromTaskPlanAfterTaskIteration() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-sync-agent").description("Planning sync agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        @SuppressWarnings("unchecked")
        List<TodoItem> created = (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_create")
                .invoke(Map.of("session_id", "sync-session", "tasks", List.of("Read code", "Patch rail")))).getData();
        String firstId = created.get(0).getId();
        String secondId = created.get(1).getId();
        TaskPlan plan = TaskPlan.builder().goal("sync")
                .tasks(List.of(
                        TodoItem.builder().id(firstId).content("Read code").status(TodoStatus.COMPLETED)
                                .resultSummary("read").build(),
                        TodoItem.builder().id(secondId).content("Patch rail").status(TodoStatus.IN_PROGRESS).build()))
                .currentTaskId(secondId).build();

        rail.afterTaskIteration(TaskIterationContext.builder().agent(agent)
                .inputs(Map.of("conversation_id", "sync-session")).result(Map.of("task_plan", plan)).build());

        TodoItem syncedFirst = (TodoItem) ((ToolOutput) findTool(agent, "todo_get")
                .invoke(Map.of("session_id", "sync-session", "id", firstId))).getData();
        TodoItem syncedSecond = (TodoItem) ((ToolOutput) findTool(agent, "todo_get")
                .invoke(Map.of("session_id", "sync-session", "id", secondId))).getData();
        assertThat(syncedFirst.getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(syncedFirst.getResultSummary()).isEqualTo("read");
        assertThat(syncedSecond.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(Files.readString(rail.taskPlanPath("sync-session"))).contains("\"task_plan\"");
    }

    @Test
    void taskPlanningRailShouldSwitchModelForSelectedInProgressTodoAndAccumulateUsage() throws Exception {
        Model fastModel = org.mockito.Mockito.mock(Model.class);
        Runner.resourceMgr().addModel("fast", () -> fastModel, "planning-model-test");
        try {
            TaskPlanningRail rail = new TaskPlanningRail(false, 20, Map.of("fast", "cheap model for simple tasks"));
            DeepAgent agent = HarnessFactory.createDeepAgent(
                    AgentCard.builder().name("planning-model-agent").description("Planning model agent").build(),
                    DeepAgentConfig.builder().rails(List.of(rail)).build(),
                    Workspace.builder().rootPath(tempDir.toString()).language("en").build());
            agent.ensureInitialized();
            @SuppressWarnings("unchecked")
            List<TodoItem> created = (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_create")
                    .invoke(Map.of("session_id", "model-session", "tasks", List.of("Use a cheap model")))).getData();
            findTool(agent, "todo_modify").invoke(Map.of("session_id", "model-session", "updates", List.of(
                    Map.of("task_id", created.get(0).getId(), "status", "in_progress", "selected_model_id", "fast"))));

            ModelCallInputs inputs = ModelCallInputs.builder().messages(new ArrayList<>())
                    .response(com.openjiuwen.core.foundation.llm.schema.AssistantMessage.builder().content("done")
                            .usageMetadata(
                                    UsageMetadata.builder().inputTokens(10).outputTokens(5).totalTokens(15).build())
                            .build())
                    .build();
            AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent.getAgent())
                    .session(new TestSession("model-session")).inputs(inputs).extra(new LinkedHashMap<>()).build();

            rail.beforeModelCall(ctx);

            assertThat(agent.getAgent().peekLlm()).isSameAs(fastModel);
            assertThat(ctx.getExtra()).containsEntry("task_planning.model_id", "fast");

            rail.afterModelCall(ctx);

            assertThat(rail.getUsageRecords()).containsKey("fast");
            assertThat(rail.getUsageRecords().get("fast").getInputTokens()).isEqualTo(10);
            assertThat(rail.getUsageRecords().get("fast").getOutputTokens()).isEqualTo(5);
            assertThat(rail.getUsageRecords().get("fast").getTotalTokens()).isEqualTo(15);

            findTool(agent, "todo_modify").invoke(Map.of("session_id", "model-session", "updates",
                    List.of(Map.of("task_id", created.get(0).getId(), "selected_model_id", ""))));
            AgentCallbackContext refreshCtx = AgentCallbackContext.builder().session(new TestSession("model-session"))
                    .inputs(ToolCallInputs.builder().toolName("todo_modify").build()).build();
            rail.afterToolCall(refreshCtx);
            rail.beforeModelCall(ctx);
            assertThat(agent.getAgent().peekLlm()).isNull();

            rail.afterInvoke(ctx);
            assertThat(rail.getUsageRecords()).isEmpty();
        } finally {
            Runner.resourceMgr().removeModel("fast", "planning-model-test",
                    com.openjiuwen.core.runner.base.TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void taskPlanningRailShouldRefreshAndClearTodoCacheAroundInvoke() throws Exception {
        TaskPlanningRail rail = new TaskPlanningRail(false, 20);
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("planning-cache-agent").description("Planning cache agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        @SuppressWarnings("unchecked")
        List<TodoItem> created = (List<TodoItem>) ((ToolOutput) findTool(agent, "todo_create")
                .invoke(Map.of("session_id", "cache-session", "tasks", List.of("Cached task")))).getData();

        AgentCallbackContext toolCtx = AgentCallbackContext.builder().session(new TestSession("cache-session"))
                .inputs(ToolCallInputs.builder().toolName("todo_create").build()).build();
        rail.afterToolCall(toolCtx);

        assertThat(rail.cachedTodos("cache-session")).extracting(TodoItem::getId)
                .containsExactly(created.get(0).getId());

        rail.afterInvoke(toolCtx);

        assertThat(rail.cachedTodos("cache-session")).isEmpty();
    }

    @Test
    void taskCompletionRailShouldInjectCompletionSignalBeforeModelCall() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "DONE", 1, false, null, null);
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("completion-signal-agent").description("Completion signal agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ModelCallInputs inputs = ModelCallInputs.builder().messages(new ArrayList<>()).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().inputs(inputs).build();

        rail.beforeModelCall(ctx);

        assertThat(rail.hasCompletionSignalSection()).isTrue();
        assertThat(agent.getAgent().getPromptBuilder().build()).contains("Completion Signal")
                .contains("<promise>DONE</promise>");
        assertThat(inputs.getMessages()).hasSize(1);
        assertThat(String.valueOf(
                ((com.openjiuwen.core.foundation.llm.schema.BaseMessage) inputs.getMessages().get(0)).getContent()))
                .contains("Completion Signal").contains("<promise>DONE</promise>");

        rail.beforeModelCall(ctx);
        assertThat(inputs.getMessages()).hasSize(1);
    }

    @Test
    void taskCompletionRailShouldExtractPromiseFromStreamingPayloads() {
        TaskCompletionRail rail = new TaskCompletionRail(null, "DONE", 1, true, null, null);
        ControllerOutputChunk chunk = new ControllerOutputChunk(0,
                new ControllerOutputPayload(ControllerOutputPayload.TASK_PROCESSING,
                        List.of(new DataFrame.JsonDataFrame(Map.of("delta",
                                List.of("working", Map.of("content", "<promise>DONE with details</promise>"))))),
                        Map.of("stream_kind", "inner_agent")),
                false);

        assertThat(rail.extractMatchingPromise(Map.of("output", "not complete", "stream_chunks", List.of(chunk))))
                .contains("DONE with details");
        assertThat(rail.extractMatchingPromise(Map.of("stream_chunks", List.of("<promise>NOT_DONE</promise>"))))
                .isEmpty();
    }

    @Test
    void evolutionRailsShouldExposeRecordsThresholdsAndTeamCompletionSignals() {
        EvolutionPatch patch = EvolutionPatch.builder().section("Workflow").action("append")
                .content("step one then step two").target(EvolutionTarget.BODY).build();
        EvolutionRecord record = EvolutionRecord.make("user_request", "test", patch);

        assertThat(record.getId()).startsWith("ev_");
        assertThat(record.isPending()).isTrue();
        assertThat(record.toMap()).containsKeys("id", "source", "timestamp", "context", "change", "usage_stats");
        assertThat(TeamSkillRail.formatEvolutionRecords(List.of(record), "en")).contains("Workflow")
                .contains("step one then step two");
        assertThat(TeamSkillRail.formatEvolutionRecords(List.of(), "cn")).isEqualTo("（无演进经验）");

        SkillCreateRail skillCreateRail = new SkillCreateRail("skills", "en", true, 3, 2);
        skillCreateRail.recordToolCall("read_file");
        skillCreateRail.recordToolCall("bash");
        assertThat(skillCreateRail.shouldProposeNewSkill()).isFalse();
        skillCreateRail.recordToolCall("grep");
        assertThat(skillCreateRail.shouldProposeNewSkill()).isTrue();
        assertThat(skillCreateRail.buildFollowUpPrompt()).contains("skill-creator").contains("skills");

        TeamSkillCreateRail teamCreateRail = new TeamSkillCreateRail("team-skills", "en", true, 2);
        teamCreateRail.recordToolCall("spawn_member");
        assertThat(teamCreateRail.shouldProposeNewTeamSkill()).isFalse();
        teamCreateRail.recordToolCall("team.spawn_member");
        assertThat(teamCreateRail.shouldProposeNewTeamSkill()).isTrue();
        assertThat(teamCreateRail.buildFollowUpPrompt()).contains("team-skill-creator").contains("team-skills");

        TeamSkillRail teamSkillRail = new TeamSkillRail("team-skills");
        assertThat(TeamSkillRail.allTasksCompleted("completed: task-a, completed: task-b")).isTrue();
        assertThat(TeamSkillRail.allTasksCompleted("completed: task-a, pending: task-b")).isFalse();
        assertThat(teamSkillRail.notifyTeamCompleted(null)).isTrue();
        assertThat(teamSkillRail.notifyTeamCompleted(null)).isFalse();
        assertThat(teamSkillRail.drainPendingApprovalEvents()).hasSize(1);
    }

    @Test
    void skillCreateRailsShouldEnqueueFollowUpsWhenOwnerHasTaskLoopController() {
        SkillCreateRail skillCreateRail = new SkillCreateRail("skills", "en", true, 2, 2);
        TeamSkillCreateRail teamSkillCreateRail = new TeamSkillCreateRail("team-skills", "en", true, 2);
        DeepAgent agent = HarnessFactory
                .createDeepAgent(
                        AgentCard.builder().name("skill-create-agent").description("Skill create agent").build(),
                        DeepAgentConfig.builder().enableTaskLoop(true)
                                .rails(List.of(skillCreateRail, teamSkillCreateRail)).build(),
                        Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        skillCreateRail.recordToolCall("read_file");
        skillCreateRail.recordToolCall("bash");
        teamSkillCreateRail.recordToolCall("spawn_member");
        teamSkillCreateRail.recordToolCall("team.spawn_member");

        assertThat(skillCreateRail.proposeIfNeeded(AgentCallbackContext.builder().build())).isTrue();
        assertThat(teamSkillCreateRail.proposeIfNeeded(AgentCallbackContext.builder().build())).isTrue();

        assertThat(agent.getLoopController().drainFollowUp()).anyMatch(prompt -> prompt.contains("skill-creator"))
                .anyMatch(prompt -> prompt.contains("team-skill-creator"));
    }

    @Test
    void verificationContractRailShouldInjectGateBeforeModelCall() throws Exception {
        VerificationContractRail rail = new VerificationContractRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("verification-contract-agent").description("Verification contract agent")
                        .build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ModelCallInputs inputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        AgentCallbackContext ctx =
            AgentCallbackContext.builder().inputs(inputs).extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeModelCall(ctx);
        rail.beforeModelCall(ctx);

        assertThat(rail.hasContractPromptSection()).isTrue();
        assertThat(agent.getAgent().getPromptBuilder().build()).contains("Verification Gate");
        assertThat(inputs.getMessages()).hasSize(1);
        assertThat(String.valueOf(
                ((com.openjiuwen.core.foundation.llm.schema.BaseMessage) inputs.getMessages().get(0)).getContent()))
                .contains("Verification Gate").contains("subagent_type=\"verification_agent\"");
    }

    @Test
    void verificationRailShouldInjectReminderAndBlockDisallowedTools() throws Exception {
        VerificationRail rail = new VerificationRail(Set.of("read_file", "bash"));
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("verification-agent").description("Verification agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).enableTaskLoop(true).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ModelCallInputs modelInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>()).build();
        AgentCallbackContext modelCtx =
            AgentCallbackContext.builder().inputs(modelInputs).extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeModelCall(modelCtx);
        rail.beforeModelCall(modelCtx);

        assertThat(rail.hasReminderPromptSection()).isTrue();
        assertThat(agent.getAgent().getPromptBuilder().build()).contains("VERIFICATION AGENT - ACTIVE CONSTRAINTS");
        assertThat(modelInputs.getMessages()).hasSize(1);

        ToolCallInputs blockedInputs = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-verification-block").name("write_file").arguments("{}").build())
                .toolName("write_file").toolArgs(Map.of("file_path", tempDir.resolve("out.txt").toString())).build();
        AgentCallbackContext blockedCtx =
            AgentCallbackContext.builder().inputs(blockedInputs).extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeToolCall(blockedCtx);

        assertThat(blockedCtx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
        assertThat(blockedInputs.getToolResult()).isInstanceOf(ToolOutput.class);
        assertThat(String.valueOf(blockedInputs.getToolMsg().getContent()))
                .contains("not available to the verification agent").contains("read_file");

        ToolCallInputs mcpInputs = ToolCallInputs.builder()
                .toolCall(
                        ToolCall.builder().id("tc-verification-mcp").name("mcp__server__tool").arguments("{}").build())
                .toolName("mcp__server__tool").toolArgs(Map.of()).build();
        AgentCallbackContext mcpCtx =
            AgentCallbackContext.builder().inputs(mcpInputs).extra(new java.util.LinkedHashMap<>()).build();
        rail.beforeToolCall(mcpCtx);
        assertThat(mcpCtx.getExtra()).doesNotContainKey("_skip_tool");
    }

    @Test
    void verificationRailShouldRejectOutOfScopeReadPaths() throws Exception {
        VerificationRail rail = new VerificationRail(Set.of("read_file"));
        DeepAgent agent =
            HarnessFactory.createDeepAgent(
                    AgentCard.builder().name("verification-scope-agent").description("Verification scope agent")
                            .build(),
                    DeepAgentConfig.builder().rails(List.of(rail)).build(),
                    Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ToolCallInputs inScope = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-in-scope").name("read_file").arguments("{}").build())
                .toolName("read_file").toolArgs(Map.of("file_path", tempDir.resolve("README.md").toString())).build();
        AgentCallbackContext inScopeCtx =
            AgentCallbackContext.builder().inputs(inScope).extra(new java.util.LinkedHashMap<>()).build();
        rail.beforeToolCall(inScopeCtx);
        assertThat(inScopeCtx.getExtra()).doesNotContainKey("_skip_tool");

        ToolCallInputs outOfScope = ToolCallInputs.builder()
                .toolCall(ToolCall.builder().id("tc-out-scope").name("read_file").arguments("{}").build())
                .toolName("read_file").toolArgs("{\"file_path\":\"/tmp/outside-verification.txt\"}").build();
        AgentCallbackContext outOfScopeCtx =
            AgentCallbackContext.builder().inputs(outOfScope).extra(new java.util.LinkedHashMap<>()).build();
        rail.beforeToolCall(outOfScopeCtx);

        assertThat(outOfScopeCtx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
        assertThat(String.valueOf(outOfScope.getToolMsg().getContent())).contains("outside the workspace scope");
    }

    @Test
    void progressiveToolRailShouldInitializeSessionVisibilityAndFilterModelTools() throws Exception {
        ProgressiveToolRail rail =
            new ProgressiveToolRail(List.of("read_file"), List.of("search_tools", "load_tools"), 2);
        ToolCard readFile =
            ToolCard.builder().id("read-file-card").name("read_file").description("Read a file from the workspace.")
                    .inputParams(Map.of("properties", Map.of("file_path", Map.of("type", "string")))).build();
        ToolCard writeFile = ToolCard.builder().id("write-file-card").name("write_file")
                .description("Write a file in the workspace.").build();
        ToolCard bash = ToolCard.builder().id("bash-card").name("bash").description("Run a shell command.").build();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("progressive-agent").description("Progressive agent").build(),
                DeepAgentConfig.builder().tools(List.of(readFile, writeFile, bash)).rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();
        TestSession session = new TestSession("progressive-session");

        AgentCallbackContext invokeCtx =
            AgentCallbackContext.builder().session(session).extra(new java.util.LinkedHashMap<>()).build();
        rail.beforeInvoke(invokeCtx);

        assertThat(rail.getVisibleTools(session)).containsExactly("search_tools", "load_tools", "read_file");
        assertThat(
                agent.getRegisteredTools().stream().filter(com.openjiuwen.core.foundation.tool.Tool.class::isInstance)
                        .map(item -> ((com.openjiuwen.core.foundation.tool.Tool) item).getCard().getName()))
                .contains("search_tools", "load_tools");

        ModelCallInputs modelInputs = ModelCallInputs.builder().messages(new java.util.ArrayList<>())
                .tools(new java.util.ArrayList<>(List.of(ToolInfo.builder().name("search_tools").build(),
                        ToolInfo.builder().name("load_tools").build(),
                        ToolInfo.builder().name("read_file").description("Read a file").build(),
                        ToolInfo.builder().name("write_file").description("Write a file").build(),
                        ToolInfo.builder().name("bash").description("Run shell").build())))
                .build();
        AgentCallbackContext modelCtx = AgentCallbackContext.builder().inputs(modelInputs).session(session)
                .extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeModelCall(modelCtx);

        assertThat(rail.hasProgressivePromptSections()).isTrue();
        assertThat(modelInputs.getTools()).extracting(ToolInfo::getName).containsExactly("search_tools", "load_tools",
                "read_file");
        assertThat(modelInputs.getMessages()).hasSize(2);
        assertThat(modelInputs.getMessages().stream()
                .map(message -> String
                        .valueOf(((com.openjiuwen.core.foundation.llm.schema.BaseMessage) message).getContent())))
                .anyMatch(content -> content.contains("Tool Navigation"))
                .anyMatch(content -> content.contains("Progressive Tool Usage Rules"));

        assertThat(rail.searchTools("write", 10, 2)).extracting(item -> String.valueOf(item.get("name")))
                .contains("write_file");

        Map<String, Object> loaded = rail.loadTools(session, List.of("bash", "write_file", "missing_tool"), false);
        assertThat(((List<?>) loaded.get("visible_tools")).stream().map(String::valueOf).toList())
                .containsExactly("search_tools", "load_tools");
        assertThat(((List<?>) loaded.get("skipped_tools")).stream().map(String::valueOf).toList())
                .contains("write_file", "missing_tool");

        Map<String, Object> replaced = rail.loadTools(session, List.of("bash"), true);
        assertThat(((List<?>) replaced.get("visible_tools")).stream().map(String::valueOf).toList())
                .containsExactly("bash");
    }

    @Test
    void agentModeRailShouldHideEnterAndExitToolsFromNormalModelCalls() throws Exception {
        AgentModeRail rail = new AgentModeRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("mode-normal-tools-agent").description("Mode normal tools agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).language("en").build());
        agent.ensureInitialized();

        ModelCallInputs inputs = ModelCallInputs.builder()
                .tools(new java.util.ArrayList<>(List.of(ToolInfo.builder().name("switch_mode").build(),
                        ToolInfo.builder().name("enter_plan_mode").build(),
                        ToolInfo.builder().name("exit_plan_mode").build())))
                .build();
        AgentCallbackContext ctx =
            AgentCallbackContext.builder().inputs(inputs).extra(new java.util.LinkedHashMap<>()).build();

        rail.beforeModelCall(ctx);

        assertThat(inputs.getTools()).extracting(ToolInfo::getName).containsExactly("switch_mode");
        assertThat(rail.hasPlanModePromptSection()).isFalse();
    }

    private static Tool findTool(DeepAgent agent, String name) {
        return agent.getRegisteredTools().stream().filter(Tool.class::isInstance).map(Tool.class::cast)
                .filter(tool -> name.equals(tool.getCard().getName())).findFirst().orElseThrow();
    }

    private static String javaBin() {
        return ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("Current Java executable is unavailable."));
    }

    private static String stdioFixtureClasspath() throws Exception {
        List<String> entries = new ArrayList<>();
        for (Class<?> type : List.of(com.openjiuwen.harness.rails.fixtures.StdioMcpResourceServer.class,
                com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.core.JsonFactory.class,
                com.fasterxml.jackson.annotation.JsonInclude.class)) {
            String entry = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            if (!entries.contains(entry)) {
                entries.add(entry);
            }
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static final class RecordingLspServer {
        private final Object result;
        private final List<LspRequest> requests = new ArrayList<>();
        private boolean shutdownCalled;
        private boolean exitCalled;

        private RecordingLspServer(Object result) {
            this.result = result;
        }

        public void addNotificationHandler(String method, java.util.function.Consumer<Map<String, Object>> handler) {
            // Compatibility hook used by LSPServerManager diagnostics registration.
        }

        public Object sendRequest(String method, Map<String, Object> params) {
            requests.add(new LspRequest(method, params));
            return result;
        }

        public void shutdown() {
            shutdownCalled = true;
        }

        public void exit() {
            exitCalled = true;
        }
    }

    private record LspRequest(String method, Map<String, Object> params) {
    }

    private static final class RemoteSyncSkillUseRail extends SkillUseRail {
        private final List<RemoteSkillSource> syncedSources = new ArrayList<>();

        private RemoteSyncSkillUseRail(List<String> skillDirectories, String skillMode, List<String> enabledSkills,
                List<String> disabledSkills, List<RemoteSkillSource> remoteSkillSources) {
            super(skillDirectories, skillMode, enabledSkills, disabledSkills, remoteSkillSources);
        }

        @Override
        protected void uploadRemoteSkill(DeepAgent deepAgent, RemoteSkillSource source, Path targetRoot) {
            syncedSources.add(source);
            try {
                Path skillDir = targetRoot.resolve("remote-migration");
                Files.createDirectories(skillDir);
                Files.writeString(skillDir.resolve("SKILL.md"), """
                        ---
                        description: Remote migration skill
                        ---

                        Remote workflow
                        """);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static HttpServer startHttpMcpResourceServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", HarnessRailsCompatibilityTest::handleHttpMcpRequest);
        server.start();
        return server;
    }

    private static void handleHttpMcpRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        Map<String, Object> request = JsonUtils.getMapper().readValue(exchange.getRequestBody(), new TypeReference<>() {
        });
        Object id = request.get("id");
        String method = String.valueOf(request.get("method"));
        String clientType = exchange.getRequestURI().getQuery().replace("client=", "");
        if (!clientType.equals(exchange.getRequestHeaders().getFirst("X-MCP-Test"))) {
            Map<String, Object> errorBody = new java.util.LinkedHashMap<>();
            errorBody.put("jsonrpc", "2.0");
            if (id != null) {
                errorBody.put("id", id);
            }
            errorBody.put("error", Map.of("code", -32602, "message", "missing test auth"));
            writeHttpMcpResponse(exchange, errorBody);
            return;
        }

        // JSON-RPC notifications (e.g. notifications/initialized) have no id; ack with empty body.
        // Map.of(..., "id", null, ...) would NPE and break HTTP MCP connect after #40 handshake.
        if (id == null || method.startsWith("notifications/")) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        Map<String, Object> result = switch (method) {
            case "initialize" -> Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of(), "serverInfo",
                    Map.of("name", clientType + "-fixture", "version", "1.0.0"));
            case "tools/list" -> Map.of("tools", List.of());
            case "resources/list" -> Map.of("resources",
                    List.of(Map.of("uri", "memory://" + clientType + "/readme", "name", clientType + " README",
                            "mimeType", "text/plain", "description", clientType + " MCP fixture resource")));
            case "resources/read" -> Map.of("contents", List.of(Map.of("uri", "memory://" + clientType + "/readme",
                    "mimeType", "text/plain", "text", "hello from " + clientType + " fixture")));
            case "tools/call" -> Map.of("content", List.of());
            default -> Map.of();
        };
        writeHttpMcpResponse(exchange, Map.of("jsonrpc", "2.0", "id", id, "result", result));
    }

    private static void writeHttpMcpResponse(HttpExchange exchange, Map<String, Object> response) throws IOException {
        byte[] body = JsonUtils.safeJsonDumps(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
