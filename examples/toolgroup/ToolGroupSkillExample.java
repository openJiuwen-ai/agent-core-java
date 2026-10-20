/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.toolgroup;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.skills.repository.FileSystemSkillRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Demonstrates ToolGroup visibility, execution rejection, and Skill tool binding. */
public final class ToolGroupSkillExample {

    private ToolGroupSkillExample() {}

    public static void main(String[] args) throws Exception {
        AgentCard card =
                new AgentCard(
                        "tool-group-skill-demo",
                        "tool-group-skill-demo",
                        "ToolGroup and Skill demo");
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = new ReActAgentConfig();
        config.setSysOperationId("tool-group-skill-demo-sysop");
        agent.configure(config);
        AgentSession session =
                AgentSession.createAgentSession("tool-group-skill-session", null, card);

        registerLocalTool(agent, "clock", "clock");
        registerLocalTool(agent, "search", "search result");
        registerLocalTool(agent, "chart", "chart result");
        registerLocalTool(agent, "delete_file", "deleted");
        registerLocalTool(agent, "mcp_weather_forecast", "sunny, 24C");
        agent.getAbilityManager()
                .add(
                        McpServerConfig.builder()
                                .serverId("mcp-weather")
                                .serverName("weather")
                                .build());

        agent.registerToolGroup(
                new ToolGroup("research", "Research tools", List.of("skill_search")));
        agent.registerToolGroup(new ToolGroup("analysis", "Analysis tools", List.of("chart")));
        agent.registerToolGroup(new ToolGroup("danger", "Dangerous tools", List.of("delete_file")));
        agent.registerToolGroup(
                new ToolGroup("mcp_tools", "Mock MCP tools", List.of("mcp_weather_forecast")));

        Path skillsRoot = Path.of("examples", "toolgroup", "skills").toAbsolutePath().normalize();
        agent.getSkillUtil()
                .getSkillManager()
                .setRepositories(List.of(new FileSystemSkillRepository(skillsRoot)));
        agent.getSkillUtil().getSkillManager().refreshIncrementally(List.of());
        if (!agent.getSkillUtil().getSkillManager().has("web_research")) {
            throw new IllegalStateException("Failed to register skill from " + skillsRoot);
        }

        agent.registerSkillTools(
                        SkillToolBinding.builder()
                                .skillName("web_research")
                                .tools(
                                        List.of(
                                                localTool(
                                                        "skill_search",
                                                        "skill-bound search result")))
                                .build())
                .toCompletableFuture()
                .join();

        agent.activateToolGroup("research", session);
        agent.activateToolGroup("analysis", session);
        agent.activateSkill("web_research", session);
        printTools("Active research/analysis and web_research", agent, session);

        agent.deactivateToolGroup("research", session);
        printTools("web_research stays active, but explicit research is disabled", agent, session);
        execute(agent, session, "search");

        agent.activateToolGroup("research", session);
        agent.deactivateSkill("web_research", session);
        printTools("research is active without web_research", agent, session);

        agent.activateToolGroup("mcp_tools", session);
        printTools("Mock MCP group is active", agent, session);
        execute(agent, session, "mcp_weather_forecast");

        agent.deactivateToolGroup("mcp_tools", session);
        agent.deactivateToolGroup("research", session);
        agent.deactivateToolGroup("analysis", session);
        execute(agent, session, "delete_file");
    }

    private static void registerLocalTool(ReActAgent agent, String name, Object result) {
        agent.getAbilityManager()
                .addAbility(
                        ToolCard.builder()
                                .id(name)
                                .name(name)
                                .description(name)
                                .inputParams(
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.of(),
                                                "required",
                                                List.of()))
                                .build(),
                        new LocalFunction(
                                ToolCard.builder()
                                        .id(name)
                                        .name(name)
                                        .description(name)
                                        .inputParams(
                                                Map.of(
                                                        "type",
                                                        "object",
                                                        "properties",
                                                        Map.of(),
                                                        "required",
                                                        List.of()))
                                        .build(),
                                inputs -> Map.of("result", result)));
    }

    private static LocalFunction localTool(String name, Object result) {
        ToolCard card =
                ToolCard.builder()
                        .id(name)
                        .name(name)
                        .description(name)
                        .inputParams(
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of(),
                                        "required",
                                        List.of()))
                        .build();
        return new LocalFunction(card, inputs -> Map.of("result", result));
    }

    private static void printTools(String label, ReActAgent agent, AgentSession session) {
        List<String> names =
                agent.listEffectiveToolInfo(session).stream()
                        .map(ToolInfo::getName)
                        .sorted()
                        .toList();
        System.out.println(label + ": " + names);
    }

    private static void execute(ReActAgent agent, AgentSession session, String toolName) {
        ToolCall call =
                ToolCall.builder().id(toolName + "-call").name(toolName).arguments("{}").build();
        List<AbilityManager.ExecutionResult> results =
                agent.getAbilityManager().execute(null, call, session, false);
        if (results.size() != 1 || results.get(0).toolMessage() == null) {
            throw new IllegalStateException("Expected one ToolMessage for " + toolName);
        }
        System.out.println(
                toolName + " execution: " + results.get(0).toolMessage().getContentAsString());
    }
}
