/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import com.openjiuwen.agentevolving.optimizer.LlmResilience;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Detects team-domain evolution signals from user input and trajectories.
 *
 * <p>Mirrors Python's {@code TeamSignalDetector} in
 * {@code openjiuwen/agent_evolving/signal/team.py}.</p>
 */
public class TeamSignalDetector {

    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("team-signal-detector-io");

    private static final String USER_REQUEST_PROMPT_EN = """
            Determine if the following user input contains improvement suggestions
            for the current team task or collaboration approach.
            If yes, extract a summary of the improvement intent.

            Team skill description: {team_skill_description}
            Current roles: {roles}
            User input: {user_messages}

            Output JSON: {"is_improvement": true/false, "intent": "str"}
            """;

    private static final String USER_REQUEST_PROMPT_CN = """
            判断以下用户输入是否包含对当前团队任务或团队协作方式的改进意见。
            如果是，提取改进意图的摘要。

            团队技能描述：{team_skill_description}
            当前角色：{roles}
            用户输入：{user_messages}

            输出 JSON: {"is_improvement": true/false, "intent": "str"}
            """;

    private static final String TRAJECTORY_ISSUE_PROMPT_EN = """
            Analyze the following execution trajectory and determine whether the team skill has deficiencies.

            Current team skill:
            {skill_content}

            Trajectory summary:
            {trajectory_summary}

            Analyze from these dimensions:
            - Role coordination (collaboration breaks, data not passed)
            - Constraint violations (timeout, output format issues)
            - Workflow inefficiency (redundant calls, extra steps)
            - Role capability gaps (repeated failures, poor output quality)

            If issues exist, output a JSON array:
            [{"issue_type": str, "description": str, "affected_role": str, "severity": "low"|"medium"|"high"}]
            If no issues, output empty array [].
            """;

    private static final String TRAJECTORY_ISSUE_PROMPT_CN = """
            分析以下执行轨迹，判断团队技能是否存在不足需要演进。

            当前团队技能：
            {skill_content}

            执行轨迹摘要：
            {trajectory_summary}

            请从角色配合、约束违反、流程效率和角色能力等维度分析。
            如果存在不足，输出 JSON 数组：
            [{"issue_type": str, "description": str, "affected_role": str, "severity": "low"|"medium"|"high"}]
            如果没有问题，输出空数组 []。
            """;

    private final Model llm;
    private final String model;
    private final String language;
    private final LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy;
    private final LlmResilience.LLMInvokePolicy userIntentLlmPolicy;

    public TeamSignalDetector(
            Model llm,
            String model,
            String language,
            LlmResilience.LLMInvokePolicy llmPolicy,
            LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy,
            LlmResilience.LLMInvokePolicy userIntentLlmPolicy
    ) {
        LlmResilience.LLMInvokePolicy policy = llmPolicy != null
                ? llmPolicy
                : (trajectoryIssueLlmPolicy != null ? trajectoryIssueLlmPolicy : userIntentLlmPolicy);
        if (policy == null) {
            throw new IllegalArgumentException("TeamSignalDetector requires at least one LLM policy");
        }
        this.llm = llm;
        this.model = model;
        this.language = language != null ? language : "cn";
        this.trajectoryIssueLlmPolicy = trajectoryIssueLlmPolicy != null ? trajectoryIssueLlmPolicy : policy;
        this.userIntentLlmPolicy = userIntentLlmPolicy != null ? userIntentLlmPolicy : policy;
    }

    public CompletionStage<UserIntent> detectUserIntent(List<?> messages, String teamSkillContent) {
        List<String> userMessages = userMessages(messages);
        if (userMessages.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String prompt = userPromptTemplate()
                .replace("{team_skill_description}", TeamSignals.limit(nullToEmpty(teamSkillContent), 1000))
                .replace("{roles}", TeamSignals.extractRolesSummary(teamSkillContent))
                .replace("{user_messages}", TeamSignals.limit(String.join("\n", userMessages), 2000));
        return CompletableFuture.supplyAsync(() -> {
            try {
                String raw = LlmResilience.invokeTextWithRetry(
                        llm,
                        model,
                        prompt,
                        userIntentLlmPolicy,
                        null,
                        null,
                        text -> TeamSignals.parseTeamModelJson(text) instanceof Map<?, ?>);
                Object parsed = TeamSignals.parseTeamModelJson(raw);
                if (parsed instanceof Map<?, ?> map && truthy(map.get("is_improvement"))) {
                    return new UserIntent(true, stringValue(map.get("intent")));
                }
                UserIntent none = null;
                return none;
            } catch (Exception exc) {
                Loggers.AGENT.warning("[TeamSignalDetector] detect_user_intent failed: {}", exc.getMessage());
                throw new CompletionException(exc);
            }
        }, IO_EXECUTOR);
    }

    public CompletionStage<List<EvolutionSignal>> detectTrajectorySignals(
            Trajectory trajectory,
            String skillName,
            String skillContent
    ) {
        return detectTrajectoryIssues(trajectory, skillContent).thenApply(issues -> {
            if (issues.isEmpty()) {
                return List.of();
            }
            return List.of(TeamSignals.makeTeamTrajectorySignal(skillName, skillContent, issues));
        });
    }

    public CompletionStage<List<Map<String, String>>> detectTrajectoryIssues(Trajectory trajectory, String skillContent) {
        String trajectorySummary = TeamSignals.buildTeamTrajectorySummary(trajectory);
        String prompt = trajectoryPromptTemplate()
                .replace("{skill_content}", TeamSignals.limit(nullToEmpty(skillContent), 10_000))
                .replace("{trajectory_summary}", trajectorySummary);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String raw = LlmResilience.invokeTextWithRetry(
                        llm,
                        model,
                        prompt,
                        trajectoryIssueLlmPolicy,
                        null,
                        null,
                        text -> TeamSignals.parseTeamModelJson(text) instanceof List<?>);
                Object parsed = TeamSignals.parseTeamModelJson(raw);
                if (!(parsed instanceof List<?> list)) {
                    return List.<Map<String, String>>of();
                }
                List<Map<String, String>> issues = new ArrayList<>();
                for (Object item : list) {
                    Map<String, String> normalized = TeamSignals.normalizeIssue(item);
                    if (normalized != null && List.of("medium", "high").contains(normalized.get("severity"))) {
                        issues.add(normalized);
                    }
                }
                return issues;
            } catch (Exception exc) {
                Loggers.AGENT.warning("[TeamSignalDetector] detect_trajectory_issues failed: {}", exc.getMessage());
                throw new CompletionException(exc);
            }
        }, IO_EXECUTOR);
    }

    private String userPromptTemplate() {
        return "cn".equals(language) ? USER_REQUEST_PROMPT_CN : USER_REQUEST_PROMPT_EN;
    }

    private String trajectoryPromptTemplate() {
        return "cn".equals(language) ? TRAJECTORY_ISSUE_PROMPT_CN : TRAJECTORY_ISSUE_PROMPT_EN;
    }

    private static List<String> userMessages(List<?> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, messages.size() - 10);
        List<String> result = new ArrayList<>();
        for (Object message : messages.subList(start, messages.size())) {
            Object role = value(message, "role");
            if ("user".equals(role == null ? "" : String.valueOf(role))) {
                result.add(stringValue(value(message, "content")));
            }
        }
        return result;
    }

    private static Object value(Object source, String name) {
        if (source instanceof Map<?, ?> map) {
            return map.get(name);
        }
        if (source == null) {
            Object none = null;
            return none;
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : List.of("get" + suffix, "is" + suffix, name)) {
            Method method = findMethod(source.getClass(), methodName);
            if (method != null) {
                try {
                    method.setAccessible(true);
                    return method.invoke(source);
                } catch (ReflectiveOperationException ignored) {
                    Object none = null;
                    return none;
                }
            }
        }
        Field field = findField(source.getClass(), name);
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(source);
            } catch (ReflectiveOperationException ignored) {
                Object none = null;
                return none;
            }
        }
        Object none = null;
        return none;
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method none = null;
        return none;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        Field none = null;
        return none;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return !String.valueOf(value).isEmpty() && !"false".equalsIgnoreCase(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
