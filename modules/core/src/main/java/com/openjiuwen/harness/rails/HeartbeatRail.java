/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Public class HeartbeatRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class HeartbeatRail extends DeepAgentRail {
    /**
     * RUN_KIND_HEARTBEAT.
     * 
     * @since 0.1.7
     */
    public static final String RUN_KIND_HEARTBEAT = "heartbeat";

    /**
     * SECTION_PRIORITY.
     * 
     * @since 0.1.7
     */
    public static final int SECTION_PRIORITY = 80;

    private DeepAgent owner;
    private Path heartbeatPath;

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 80;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            owner = deepAgent;
            heartbeatPath = deepAgent.getWorkspace().root().resolve("HEARTBEAT.md").normalize();
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (owner != null) {
            owner.getAgent().getPromptBuilder().removeSection(sectionName());
        }
        owner = null;
        heartbeatPath = null;
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner == null || !isHeartbeatRun(ctx)) {
            removeSection();
            return;
        }
        String prompt = buildHeartbeatPrompt(readHeartbeatContent(), owner.getWorkspace().getLanguage());
        owner.getAgent().addPromptBuilderSection(sectionName(), prompt, SECTION_PRIORITY);
        if (ctx.getInputs() instanceof ModelCallInputs inputs) {
            injectHeartbeatMessage(inputs, prompt);
        }
    }

    /**
     * sectionName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String sectionName() {
        return "heartbeat";
    }

    /**
     * describe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String describe() {
        return "Inject heartbeat system prompt for heartbeat runs";
    }

    /**
     * hasHeartbeatPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasHeartbeatPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(sectionName());
    }

    /**
     * heartbeatPath.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Path heartbeatPath() {
        return heartbeatPath;
    }

    /**
     * buildHeartbeatPrompt.
     * 
     * @param heartbeatContent heartbeatContent
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public String buildHeartbeatPrompt(String heartbeatContent, String language) {
        String cleaned = cleanHeartbeatContent(heartbeatContent);
        String section = !cleaned.isBlank() ? cleaned : ("en".equals(language) ? "(No heartbeat content)" : "（无心跳内容）");
        if ("en".equals(language)) {
            return "## Heartbeat\n" + section + "\n\n" + "When you receive a heartbeat message:\n"
                    + "- If there is no heartbeat content above, reply exactly: HEARTBEAT_OK\n"
                    + "- If there is heartbeat content above, the matters concerning the heartbeat content "
                    + "must be handled and a reply must be made (do not include HEARTBEAT_OK)\n\n"
                    + "The system recognizes HEARTBEAT_OK as a heartbeat acknowledgment.\n\n"
                    + "Important Constraints:\n"
                    + "- When modifying HEARTBEAT.md, DO NOT add <!-- --> comment markers to content "
                    + "that originally had no such markers\n"
                    + "- Non-commented text may only be modified or deleted when explicitly requested "
                    + "by the user; otherwise preserve it as-is";
        }
        return "## 心跳检测\n" + section + "\n\n" + "当收到心跳检测消息时：\n" + "- 若上方无心跳内容，请精确回复：HEARTBEAT_OK\n"
                + "- 若上方有心跳内容，必须处理心跳内容的事项并进行回复（不要包含 HEARTBEAT_OK）\n\n" + "系统会识别 HEARTBEAT_OK 作为心跳确认。\n\n" + "重要约束：\n"
                + "- 若需修改 HEARTBEAT.md 文件，禁止给原本没有 <!-- --> 注释的内容添加注释标记\n" + "- 非注释文本仅可在用户明确要求时修改或删除，否则必须保持原样";
    }

    /**
     * isHeartbeatRun.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private boolean isHeartbeatRun(AgentCallbackContext ctx) {
        Object runKind = ctx.getExtra() != null ? ctx.getExtra().get("run_kind") : null;
        return runKind != null && RUN_KIND_HEARTBEAT.equalsIgnoreCase(String.valueOf(runKind));
    }

    /**
     * readHeartbeatContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String readHeartbeatContent() {
        if (heartbeatPath == null || !Files.exists(heartbeatPath)) {
            return "";
        }
        try {
            return Files.readString(heartbeatPath);
        } catch (IOException ignored) {
            return "";
        }
    }

    /**
     * cleanHeartbeatContent.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static String cleanHeartbeatContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String stripped = line.trim();
            if (stripped.startsWith("<!--") && stripped.endsWith("-->")) {
                continue;
            }
            if (!stripped.isBlank()) {
                cleaned.add(stripped);
            }
        }
        return String.join("\n", cleaned);
    }

    /**
     * injectHeartbeatMessage.
     * 
     * @param inputs inputs
     * @param prompt prompt
     * @since 0.1.7
     */
    private void injectHeartbeatMessage(ModelCallInputs inputs, String prompt) {
        List<Object> messages =
            inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
        for (Object message : messages) {
            if (message instanceof BaseMessage baseMessage && "system".equalsIgnoreCase(baseMessage.getRole())
                    && String.valueOf(baseMessage.getContent()).contains("HEARTBEAT_OK")) {
                return;
            }
        }
        messages.add(0, new SystemMessage(prompt));
        inputs.setMessages(messages);
    }

    /**
     * removeSection.
     * 
     * @since 0.1.7
     */
    private void removeSection() {
        if (owner != null) {
            owner.getAgent().getPromptBuilder().removeSection(sectionName());
        }
    }
}
