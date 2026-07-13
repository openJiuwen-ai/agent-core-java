/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package references;

import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;
import com.openjiuwen.core.runner.Runner;

import java.util.Map;

/**
 * 最小可运行的 agent team 装配示例。
 *
 * 前置条件（缺一不可，否则跑不起来）：
 *   1. 把本文件或整个 examples/ 目录挪到 src/main/java/ 下，让它进入 Maven 编译路径。
 *   2. src/main/resources/apiconfig.json 填真实值（API_BASE / API_KEY / MODEL_PROVIDER / MODEL_NAME）。
 *      也可通过 -Dopenjiuwen.example.config=<path> 或 OPENJIUWEN_API_CONFIG 环境变量覆盖。
 *
 * 启动命令：
 *   mvn exec:java -Dexec.mainClass=references.MinimalRunnableExample
 *
 * 或在 IDE 里直接右键运行 main 方法。
 *
 * 本示例对齐 examples/agent_teams/AgentTeamE2eExample.java，但去掉了交互循环、
 * 流式渲染、颜色控制等干扰逻辑，只保留"装配 → 触发 → 收尾"主线，便于新手理解。
 */
public final class MinimalRunnableExample {

    private MinimalRunnableExample() {
    }

    public static void main(String[] args) throws Exception {
        // 1. 装配团队：两个 build() 缺一不可
        //    第一个 build() 组装 TeamAgentSpec
        //    第二个 build() 才真正通过 TeamFactory 创建 TeamAgent
        LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder()
                .teamName("minimal_demo")
                .description("最小可运行团队示例")
                .lifecycle(LeaderTeammateAgentTeam.LIFECYCLE_TEMPORARY)
                .teammateMode(LeaderTeammateAgentTeam.TEAMMATE_MODE_BUILD)
                .spawnMode(LeaderTeammateAgentTeam.SPAWN_MODE_INPROCESS)
                .storage(LeaderTeammateAgentTeam.STORAGE_SQLITE)
                .leaderMemberName("team_leader")
                .leaderDisplayName("组长")
                .leaderPersona("资深的任务协调者，擅长拆解问题并指派合适成员")
                .language("cn")
                // 模型配置：实际项目里通常用 SharedExampleApiConfigLoader 读 apiconfig.json
                // 这里为了示例自包含，直接传参；真实使用时替换为你的配置加载方式
                .configureModelClient(
                        "your-provider",           // MODEL_PROVIDER，如 openai / azure / qwen
                        "your-api-key",             // API_KEY
                        "https://your-api-base",    // API_BASE
                        "your-model-name",          // MODEL_NAME
                        false                       // SSL_VERIFY
                )
                .build()
                .build();

        // 2. 启动 Runner（框架内部线程、资源管理）
        Runner.start();

        try {
            // 3. 触发一次任务分发
            //    dispatchTask 是同步调用，返回团队执行结果
            //    对于 temporary 团队，leader 完成任务后会自行调 shutdown_member + clean_team 收尾
            String query = args.length > 0 ? args[0] : "拉2个人报数，分为2个有依赖的任务";
            System.out.println(">>> 派发任务: " + query);

            Map<String, Object> result = team.dispatchTask(query);

            // 4. 输出结果
            //    result 含 team_id / session_id / status / leader / route / target / delivered_content / message_id
            System.out.println(">>> 执行结果:");
            result.forEach((k, v) -> System.out.println("    " + k + " = " + v));
        } finally {
            // 5. 收尾：关闭团队 + 停止 Runner
            //    temporary 团队通常 leader 已自行 clean_team，这里 close 主要释放本地资源
            team.agent().close();
            Runner.stop();
            System.out.println(">>> Done.");
        }
    }
}
