/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_evolving;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Real TeamSkillRail example with DeepAgent.
 *
 * <p>Mirrors Python's {@code examples.agent_evolving.team_skill_rail_example}.</p>
 */
public final class TeamSkillRailExample {

    public static final String DEFAULT_QUERY = String.join("",
            "Please call skill_tool to use the research-team team skill, then organize a minimal ",
            "collaboration workflow for an AI industry weekly report. You must build_team, call ",
            "spawn_member twice, create at least two tasks, view_task, and then give a short summary.");

    public static final String DEFAULT_USER_INTENT = String.join("",
            "Add a reviewer role and require the leader to check delivery format before summarizing.");

    private TeamSkillRailExample() {
    }

    public static void configureExampleLogging() {
        TeamSkillCreateRailExample.configureExampleLogging();
    }

    public static Map<String, String> loadEnvIfPresent() {
        return TeamSkillCreateRailExample.loadEnvIfPresent();
    }

    public static TeamSkillCreateRailExample.ModelSettings buildModelFromEnv() {
        return TeamSkillCreateRailExample.buildModelFromEnv();
    }

    public static Path prepareWorkspace(String workspace) throws IOException {
        Path root = workspace != null && !workspace.isBlank()
                ? Path.of(workspace).toAbsolutePath().normalize()
                : Files.createTempDirectory("team_skill_example_evolve_").toAbsolutePath().normalize();
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("skills"));
        return root;
    }

    public static String buildSessionId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static Path writeTeamSkill(Path skillDir, String skillName) throws IOException {
        Path target = skillDir.resolve(skillName);
        Files.createDirectories(target);
        Files.writeString(
                target.resolve("SKILL.md"),
                "---\n"
                        + "name: " + skillName + "\n"
                        + "description: Rapid collaboration workflow for lightweight research tasks.\n"
                        + "kind: team-skill\n"
                        + "---\n\n"
                        + "# Workflow\n\n"
                        + "1. Call `build_team` to initialize the team.\n"
                        + "2. Call `spawn_member` for at least two specialized roles.\n"
                        + "3. Call `create_task` to split the work.\n"
                        + "4. Call `view_task` before summarizing the current state.\n",
                StandardCharsets.UTF_8);
        return target;
    }

    public static TeamSkillCreateRailExample.LeaderTeamContext leaderTeamToolsContext(
            Path workspace,
            String sessionId,
            String teamName,
            String memberName,
            String lang
    ) {
        return TeamSkillCreateRailExample.leaderTeamToolsContext(workspace, sessionId, teamName, memberName, lang);
    }

    public static ParsedArgs parseArgs(String[] args) {
        String workspace = null;
        String query = DEFAULT_QUERY;
        String userIntent = DEFAULT_USER_INTENT;
        boolean approvePatch = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--workspace".equals(arg) && i + 1 < args.length) {
                workspace = args[++i];
            } else if ("--query".equals(arg) && i + 1 < args.length) {
                query = args[++i];
            } else if ("--user-intent".equals(arg) && i + 1 < args.length) {
                userIntent = args[++i];
            } else if ("--approve-patch".equals(arg)) {
                approvePatch = true;
            }
        }
        return new ParsedArgs(workspace, query, userIntent, approvePatch);
    }

    public static void main(String[] args) throws Exception {
        ParsedArgs parsedArgs = parseArgs(args);
        configureExampleLogging();

        TeamSkillCreateRailExample.ModelSettings modelSettings = buildModelFromEnv();
        Model model = modelSettings.model();
        String modelName = modelSettings.modelRequestConfig().getModelName();
        Path workspace = prepareWorkspace(parsedArgs.workspace());
        Path skillsDir = workspace.resolve("skills");
        Path skillDir = writeTeamSkill(skillsDir, "research-team");
        String sessionId = buildSessionId("team_skill_evolve");
        String teamName = "team_skill_rail_demo_" + sessionId.substring(sessionId.lastIndexOf('_') + 1);

        Runner.start();
        try (TeamSkillCreateRailExample.LeaderTeamContext context =
                     leaderTeamToolsContext(workspace, sessionId, teamName, "leader", "cn")) {
            TeamSkillRail teamRail = new TeamSkillRail(skillsDir.toString(), model, modelName, false, false);
            DeepAgentConfig config = new DeepAgentConfig();
            config.setCard(AgentCard.builder()
                    .id("team_skill_rail_example")
                    .name("Team Skill Rail Example")
                    .description("DeepAgent example for team skill evolution")
                    .build());
            config.setModelClientConfig(modelSettings.modelClientConfig());
            config.setModelRequestConfig(modelSettings.modelRequestConfig());
            config.setSystemPrompt("You are a strict team leader. Prefer skill_tool when a team skill is provided, "
                    + "then use team tools to execute the collaboration workflow.");
            config.setTools(context.toolCards());
            config.setRails(List.<AgentRail>of(teamRail));
            config.setMaxIterations(8);
            config.setWorkspace(new Workspace(workspace.toString(), context.lang()));

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            AgentSessionApi session = AgentSessionApi.create(sessionId, Map.of("team_name", teamName), agent.getCard());
            Object result = Runner.runAgent(agent, Map.of("query", parsedArgs.query()), session, null);

            System.out.println("workspace: " + workspace);
            System.out.println("team name: " + teamName);
            System.out.println("skill file: " + skillDir.resolve("SKILL.md"));
            System.out.println("final output: " + normalizeOutput(result));
            System.out.println("requesting evolution patch with user intent: " + parsedArgs.userIntent());

            Optional<String> requestId = teamRail.requestUserEvolution(
                    "research-team",
                    parsedArgs.userIntent(),
                    false);
            if (requestId.isEmpty()) {
                System.out.println("No patch was generated from the user intent.");
                return;
            }

            List<com.openjiuwen.core.session.stream.OutputSchema> events =
                    teamRail.drainPendingApprovalEvents(true, 5.0);
            System.out.println("patch request id: " + requestId.get());
            events.stream()
                    .filter(event -> "chat.ask_user_question".equals(event.getType()))
                    .findFirst()
                    .ifPresentOrElse(
                            event -> System.out.println("patch preview: " + event.getPayload()),
                            () -> System.out.println("Patch was generated, but no approval preview event was drained."));

            if (parsedArgs.approvePatch()) {
                teamRail.onApprovePatch(requestId.get());
                System.out.println("patch approved and persisted to: "
                        + skillsDir.resolve("research-team").resolve("evolutions.json"));
            }
        } finally {
            Runner.stop();
        }
    }

    private static Object normalizeOutput(Object result) {
        if (result instanceof Map<?, ?> map && map.containsKey("output")) {
            return map.get("output");
        }
        return result;
    }

    public static final class ParsedArgs {
        private final String workspace;
        private final String query;
        private final String userIntent;
        private final boolean approvePatch;

        private ParsedArgs(String workspace, String query, String userIntent, boolean approvePatch) {
            this.workspace = workspace;
            this.query = query;
            this.userIntent = userIntent;
            this.approvePatch = approvePatch;
        }

        public String workspace() {
            return workspace;
        }

        public String query() {
            return query;
        }

        public String userIntent() {
            return userIntent;
        }

        public boolean approvePatch() {
            return approvePatch;
        }
    }
}
