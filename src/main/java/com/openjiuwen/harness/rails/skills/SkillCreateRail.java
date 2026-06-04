/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rail for skill creation workflow.
 * <p>
 * Mirrors Python's {@code SkillCreateRail} in
 * {@code openjiuwen.harness.rails.skills.skill_create_rail}.
 */
public class SkillCreateRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(SkillCreateRail.class);

    private static final String FOLLOW_UP_PROMPT_CN =
            "**重要：你必须先调用 ask_user 工具向用户确认，不可跳过此步骤。**\n"
                    + "系统检测到对话中存在可复用模式，可能值得创建新技能。请按以下步骤执行：\n"
                    + "1. 调用 ask_user 工具向用户确认：\n"
                    + "   - 问题：\"我检测到您可能值得创建一个新技能。是否创建？\"\n"
                    + "   - 选项：[\"创建\"，\"跳过\"，\"自定义指令：（请描述需求）\"]\n"
                    + "2. 如果用户选择\"创建\"或提供了自定义指令，请调用 **skill-creator** 技能，"
                    + "根据用户的要求和当前对话上下文执行技能创建。\n"
                    + "   新技能应保存到技能目录：%s";

    private static final String FOLLOW_UP_PROMPT_EN =
            "**Important: You MUST call the ask_user tool to confirm with the user first. Do not skip this step.**\n"
                    + "The system detected a reusable pattern that may be worth creating as a new skill. "
                    + "Please follow these steps:\n"
                    + "1. Use ask_user tool to confirm with the user:\n"
                    + "   - Question: \"I detected a pattern that may be worth creating as a new skill. Create it?\"\n"
                    + "   - Options: [\"Create\", \"Skip\", \"Custom instruction: (describe your needs)\"]\n"
                    + "2. If user chooses \"Create\" or provides a custom instruction, invoke the **skill-creator** skill "
                    + "to execute the skill creation.\n"
                    + "   Save the new skill to: %s";

    private final String skillsDir;
    private final String language;
    private final boolean autoTrigger;
    private final int toolCallThreshold;
    private final int toolDiversityThreshold;
    private boolean proposalSent;
    private Object builder;

    public SkillCreateRail() {
        this("./skills");
    }

    public SkillCreateRail(String skillsDir) {
        this(skillsDir, "cn", true, 10, 5);
    }

    public SkillCreateRail(Path skillsDir) {
        this(skillsDir != null ? skillsDir.toString() : "./skills");
    }

    public SkillCreateRail(String skillsDir, String language, boolean autoTrigger,
                           int toolCallThreshold, int toolDiversityThreshold) {
        super();
        this.skillsDir = (skillsDir == null || skillsDir.isBlank()) ? "./skills" : skillsDir;
        this.language = (language == null || language.isBlank()) ? "cn" : language;
        this.autoTrigger = autoTrigger;
        this.toolCallThreshold = toolCallThreshold;
        this.toolDiversityThreshold = toolDiversityThreshold;
        this.proposalSent = false;
        setPriority(85);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[SkillCreateRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[SkillCreateRail] Uninitialized");
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        proposalSent = false;
    }

    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        if (!autoTrigger || proposalSent || !shouldProposeNewSkill()) {
            return;
        }
        Object controller = resolveLoopController(ctx != null ? ctx.getAgent() : null);
        if (controller == null) {
            LOG.warn("[SkillCreateRail] skill creation proposal dropped: no TaskLoopController available");
            return;
        }
        String prompt = ("en".equalsIgnoreCase(language) ? FOLLOW_UP_PROMPT_EN : FOLLOW_UP_PROMPT_CN).formatted(skillsDir);
        if (enqueueFollowUp(controller, prompt)) {
            proposalSent = true;
            LOG.info("[SkillCreateRail] follow_up enqueued successfully");
        }
    }

    public boolean shouldProposeNewSkill() {
        List<String> toolCalls = collectToolCalls(builder);
        int totalCalls = toolCalls.size();
        Set<String> uniqueTools = new LinkedHashSet<>(toolCalls);
        return totalCalls >= toolCallThreshold && uniqueTools.size() >= toolDiversityThreshold;
    }

    public String getSkillsDir() {
        return skillsDir;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isAutoTrigger() {
        return autoTrigger;
    }

    public int getToolCallThreshold() {
        return toolCallThreshold;
    }

    public int getToolDiversityThreshold() {
        return toolDiversityThreshold;
    }

    public boolean isProposalSent() {
        return proposalSent;
    }

    public Object getBuilder() {
        return builder;
    }

    public void setBuilder(Object builder) {
        this.builder = builder;
    }

    protected List<String> collectToolCalls(Object sourceBuilder) {
        List<String> toolCalls = new ArrayList<>();
        for (Object step : resolveSteps(sourceBuilder)) {
            if (!isToolStep(step)) {
                continue;
            }
            Object detail = readObject(step, "detail");
            if (detail == null) {
                detail = readObject(step, "inputs");
            }
            String toolName = readString(detail, "toolName");
            if (toolName == null || toolName.isBlank()) {
                toolName = readString(detail, "tool_name");
            }
            if (toolName != null && !toolName.isBlank()) {
                toolCalls.add(toolName);
            }
        }
        return toolCalls;
    }

    private static List<?> resolveSteps(Object sourceBuilder) {
        if (sourceBuilder == null) {
            return List.of();
        }
        if (sourceBuilder instanceof TrajectoryBuilder trajectoryBuilder) {
            return trajectoryBuilder.getSteps();
        }
        Object steps = readObject(sourceBuilder, "steps");
        if (steps instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private static boolean isToolStep(Object step) {
        if (step instanceof TrajectoryStep trajectoryStep) {
            return trajectoryStep.getKindEnum() == StepKind.TOOL || "tool".equalsIgnoreCase(trajectoryStep.getKind());
        }
        String kind = readString(step, "kind");
        return "tool".equalsIgnoreCase(kind);
    }

    protected static Object resolveLoopController(Object agent) {
        if (agent == null) {
            return null;
        }
        Object direct = readObject(agent, "_loop_controller");
        if (direct != null) {
            return direct;
        }
        direct = readObject(agent, "loopController");
        if (direct != null) {
            return direct;
        }
        direct = readObject(agent, "loop_controller");
        if (direct != null) {
            return direct;
        }
        return null;
    }

    protected static boolean enqueueFollowUp(Object controller, String prompt) {
        for (String methodName : List.of("enqueueFollowUp", "enqueue_follow_up", "pushFollowUp", "push_follow_up")) {
            try {
                Method method = findMethod(controller.getClass(), methodName, String.class);
                if (method == null) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(controller, prompt);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> startType, String name, Class<?>... parameterTypes) {
        Class<?> type = startType;
        while (type != null) {
            try {
                return type.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        try {
            return startType.getMethod(name, parameterTypes);
        } catch (Exception ignored) {
            return null;
        }
    }

    protected static Object readObject(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        if (target instanceof ToolCallDetail detail && "toolName".equals(name)) {
            return detail.getToolName();
        }
        String getter = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception exc) {
                return null;
            }
        }
        return null;
    }

    protected static String readString(Object target, String name) {
        Object value = readObject(target, name);
        return value != null ? String.valueOf(value) : null;
    }
}
