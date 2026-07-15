/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Public class ContextAssembleRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ContextAssembleRail extends DeepAgentRail {
    private static final int WORKSPACE_PRIORITY = 30;
    private static final int TOOLS_PRIORITY = 40;
    private static final int CONTEXT_PRIORITY = 50;
    private static final int MAX_WORKSPACE_ENTRIES = 80;
    private static final int MAX_CONTEXT_FILES = 8;
    private DeepAgent owner;

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 85;
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
            this.owner = deepAgent;
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
        if (agent instanceof DeepAgent deepAgent) {
            for (String sectionName : sectionNames()) {
                deepAgent.getAgent().getPromptBuilder().removeSection(sectionName);
            }
        }
        owner = null;
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner == null) {
            return;
        }
        List<String> injected = new ArrayList<>();
        String language = owner.getWorkspace().getLanguage();
        addSection("workspace", buildWorkspaceSection(language), WORKSPACE_PRIORITY, injected);
        addSection("tools", buildToolsSection(language, ctx), TOOLS_PRIORITY, injected);
        addSection("context", buildContextSection(language), CONTEXT_PRIORITY, injected);
        injectSystemMessages(ctx, injected);
    }

    /**
     * sectionNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> sectionNames() {
        return List.of("workspace", "tools", "context");
    }

    /**
     * describe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String describe() {
        return "Assemble workspace, tools, and context prompt sections";
    }

    /**
     * hasContextSections.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasContextSections() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection("workspace")
                && owner.getAgent().getPromptBuilder().hasSection("tools")
                && owner.getAgent().getPromptBuilder().hasSection("context");
    }

    /**
     * addSection.
     * 
     * @param name name
     * @param content content
     * @param priority priority
     * @param injected injected
     * @since 0.1.7
     */
    private void addSection(String name, String content, int priority, List<String> injected) {
        owner.getAgent().getPromptBuilder().removeSection(name);
        if (content == null || content.isBlank()) {
            return;
        }
        String language = owner.getWorkspace().getLanguage();
        owner.getAgent().getPromptBuilder().addSection(new PromptSection(name,
                Map.of(language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language, content),
                priority));
        injected.add(content);
    }

    /**
     * buildWorkspaceSection.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private String buildWorkspaceSection(String language) {
        Path root = owner.getWorkspace().root();
        List<String> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 2)) {
            stream.filter(path -> !path.equals(root))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString())).limit(MAX_WORKSPACE_ENTRIES)
                    .forEach(path -> {
                        String rel = root.relativize(path).toString().replace('\\', '/');
                        entries.add((Files.isDirectory(path) ? "- [dir] " : "- [file] ") + rel);
                    });
        } catch (IOException ignored) {
            entries.add("- (workspace listing unavailable)");
        }
        String title = "en".equalsIgnoreCase(language) ? "## Workspace" : "## 工作区";
        return title + "\n\nRoot: " + root + "\n\n" + String.join("\n", entries);
    }

    /**
     * buildToolsSection.
     * 
     * @param language language
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private String buildToolsSection(String language, AgentCallbackContext ctx) {
        List<ToolInfo> tools = new ArrayList<>();
        if (ctx != null && ctx.getInputs() instanceof ModelCallInputs inputs && inputs.getTools() != null) {
            tools.addAll(inputs.getTools());
        }
        if (tools.isEmpty()) {
            tools.addAll(owner.getAgent().getAbilityManager().listToolInfo());
        }
        List<String> lines = tools.stream()
                .map(tool -> "- " + tool.getName()
                        + (tool.getDescription() == null || tool.getDescription().isBlank()
                                ? ""
                                : ": " + tool.getDescription()))
                .toList();
        String title = "en".equalsIgnoreCase(language) ? "## Available Tools" : "## 可用工具";
        return title + "\n\n" + String.join("\n", lines);
    }

    /**
     * buildContextSection.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private String buildContextSection(String language) {
        Path contextDir = owner.getWorkspace().getNodePath("context");
        String title = "en".equalsIgnoreCase(language) ? "## Context Files" : "## 上下文文件";
        if (!Files.isDirectory(contextDir)) {
            return title + "\n\n(no context files)";
        }
        List<String> parts = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(contextDir, 1)) {
            stream.filter(Files::isRegularFile).sorted().limit(MAX_CONTEXT_FILES)
                    .forEach(path -> parts.add("### " + path.getFileName() + "\n\n" + readSnippet(path)));
        } catch (IOException ignored) {
            parts.add("(context files unavailable)");
        }
        return title + "\n\n" + String.join("\n\n", parts);
    }

    /**
     * readSnippet.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private String readSnippet(Path path) {
        try {
            String content = Files.readString(path);
            return content.length() <= 4000 ? content : content.substring(0, 4000) + "\n...";
        } catch (IOException ignored) {
            return "";
        }
    }

    /**
     * injectSystemMessages.
     * 
     * @param ctx ctx
     * @param sections sections
     * @since 0.1.7
     */
    private void injectSystemMessages(AgentCallbackContext ctx, List<String> sections) {
        if (!(ctx.getInputs() instanceof ModelCallInputs inputs) || sections.isEmpty()) {
            return;
        }
        List<Object> messages =
            inputs.getMessages() == null ? new ArrayList<>() : new ArrayList<>(inputs.getMessages());
        for (String section : sections) {
            messages.add(new SystemMessage(section));
        }
        inputs.setMessages(messages);
    }
}
