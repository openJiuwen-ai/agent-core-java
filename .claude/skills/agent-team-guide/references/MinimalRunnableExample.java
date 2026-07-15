/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package references;

import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;
import com.openjiuwen.core.runner.Runner;

import java.util.Map;

/**
 * Minimal runnable agent team assembly example.
 *
 * Prerequisites (all required, otherwise it won't run):
 *   1. Move this file or the entire examples/ directory under src/main/java/, so it enters the Maven compile path.
 *   2. Fill real values in src/main/resources/apiconfig.json (API_BASE / API_KEY / MODEL_PROVIDER / MODEL_NAME).
 *      Override with -Dopenjiuwen.example.config=<path> or OPENJIUWEN_API_CONFIG environment variable.
 *
 * Startup command:
 *   mvn exec:java -Dexec.mainClass=references.MinimalRunnableExample
 *
 * Or run the main method directly in IDE.
 *
 * This example aligns with examples/agent_teams/AgentTeamE2eExample.java, but removes the interaction loop,
 * streaming rendering, color control and other distracting logic, only keeping the "assemble -> trigger -> wrap-up"
 * main line for easy understanding by beginners.
 */
public final class MinimalRunnableExample {

    private MinimalRunnableExample() {
    }

    public static void main(String[] args) throws Exception {
        // 1. Assemble the team: both build() calls are required
        //    The first build() assembles TeamAgentSpec
        //    The second build() actually creates TeamAgent via TeamFactory
        LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
                .teamName("minimal_demo")
                .description("Minimal runnable team example")
                .lifecycle(LeaderTeammateAgentTeam.LIFECYCLE_TEMPORARY)
                .teammateMode(LeaderTeammateAgentTeam.TEAMMATE_MODE_BUILD)
                .spawnMode(LeaderTeammateAgentTeam.SPAWN_MODE_INPROCESS)
                .storage(LeaderTeammateAgentTeam.STORAGE_SQLITE)
                .leaderMemberName("team_leader")
                .leaderDisplayName("Team Lead")
                .leaderPersona("Experienced task coordinator, skilled at decomposing problems and assigning suitable members")
                .language("cn")
                // Model configuration: in real projects, typically use SharedExampleApiConfigLoader to read apiconfig.json
                // Here for self-contained example, pass directly; replace with your config loading method in real usage
                .configureModelClient(
                        "your-provider",           // MODEL_PROVIDER, e.g. openai / azure / qwen
                        "your-api-key",             // API_KEY
                        "https://your-api-base",    // API_BASE
                        "your-model-name",          // MODEL_NAME
                        false                       // SSL_VERIFY
                )
                .build()
                .build();

        // 2. Start Runner (framework internal threads, resource management)
        Runner.start();

        try {
            // 3. Trigger a task dispatch
            //    dispatchTask is a synchronous call, returns team execution result
            //    For temporary teams, leader will call shutdown_member + clean_team to wrap up after task completion
            String query = args.length > 0 ? args[0] : "Spawn 2 people to report numbers, divide into 2 dependent tasks";
            System.out.println(">>> Dispatching task: " + query);

            Map<String, Object> result = team.dispatchTask(query);

            // 4. Output result
            //    result contains team_id / session_id / status / leader / route / target / delivered_content / message_id
            System.out.println(">>> Execution result:");
            result.forEach((k, v) -> System.out.println("    " + k + " = " + v));
        } finally {
            // 5. Wrap up: close team + stop Runner
            //    For temporary teams, leader has usually already clean_team'd; close mainly releases local resources
            team.agent().close();
            Runner.stop();
            System.out.println(">>> Done.");
        }
    }
}
