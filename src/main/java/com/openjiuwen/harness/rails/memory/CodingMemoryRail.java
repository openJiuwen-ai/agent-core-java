/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.CodingMemorySection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Coding-agent memory rail with recall state and prompt injection.
 *
 * <p>Mirrors Python's {@code CodingMemoryRail} in
 * {@code openjiuwen/harness/rails/memory/coding_memory_rail.py}.</p>
 */
public class CodingMemoryRail extends DeepAgentRail {

    public static final int MAX_RECALL_RESULTS = 5;
    public static final int MAX_RECALL_TOTAL_BYTES = 10 * 1024;

    private final String codingMemoryDir;
    private final Object embeddingConfig;
    private final String language;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private boolean managerInitialized;
    private String recalledContent;
    private int totalMemories;

    public CodingMemoryRail(String codingMemoryDir, Object embeddingConfig) {
        this(codingMemoryDir, embeddingConfig, "cn");
    }

    public CodingMemoryRail(String codingMemoryDir, Object embeddingConfig, String language) {
        setPriority(80);
        this.codingMemoryDir = codingMemoryDir == null || codingMemoryDir.isBlank()
                ? "coding_memory"
                : codingMemoryDir;
        this.embeddingConfig = embeddingConfig;
        this.language = "en".equals(language) ? "en" : "cn";
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        registerToolNames(agent);
    }

    @Override
    public void uninit(DeepAgent agent) {
        ownedToolNames.clear();
        ownedToolIds.clear();
        managerInitialized = false;
        recalledContent = null;
        totalMemories = 0;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (!managerInitialized) {
            managerInitialized = embeddingConfig != null;
        }
        recalledContent = null;
        Object recall = ctx.get("coding_memory_recall");
        if (recall != null) {
            recalledContent = trimRecall(String.valueOf(recall));
        }
        Object count = ctx.get("coding_memory_count");
        if (count instanceof Number number) {
            totalMemories = number.intValue();
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        Object runKind = ctx.getValues().getOrDefault("run_kind", "");
        boolean readOnly = "cron".equals(String.valueOf(runKind)) || "heartbeat".equals(String.valueOf(runKind));
        String resolvedLanguage = String.valueOf(ctx.getValues().getOrDefault("language", language));
        PromptSection section = CodingMemorySection.buildCodingMemorySection(
                resolvedLanguage,
                readOnly,
                codingMemoryDir
        );
        if (recalledContent != null && !recalledContent.isBlank()) {
            section = appendRecalledContent(section, resolvedLanguage);
            ctx.put("coding_memory_recalled_content", recalledContent);
        }
        ctx.put("memory_section", section);
        ctx.put("coding_memory_total", totalMemories);
    }

    public String getCodingMemoryDir() {
        return codingMemoryDir;
    }

    public boolean isManagerInitialized() {
        return managerInitialized;
    }

    public String getRecalledContent() {
        return recalledContent;
    }

    public Set<String> getOwnedToolNames() {
        return new LinkedHashSet<>(ownedToolNames);
    }

    private String trimRecall(String raw) {
        if (raw == null) {
            return null;
        }
        byte[] bytes = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= MAX_RECALL_TOTAL_BYTES) {
            return raw;
        }
        return new String(bytes, 0, MAX_RECALL_TOTAL_BYTES, java.nio.charset.StandardCharsets.UTF_8);
    }

    private PromptSection appendRecalledContent(PromptSection section, String resolvedLanguage) {
        Map<String, String> content = new LinkedHashMap<>(section.getContent());
        String base = section.render(resolvedLanguage);
        String header = "cn".equals(resolvedLanguage) ? "## 已加载的相关记忆\n\n" : "## Loaded relevant memories\n\n";
        String footer = "cn".equals(resolvedLanguage)
                ? "\n\n（共 " + totalMemories + " 条记忆，用 coding_memory_read 读取其他。）"
                : "\n\n(" + totalMemories + " total. Use coding_memory_read for others.)";
        content.put(resolvedLanguage, base + "\n\n" + header + recalledContent + footer);
        return new PromptSection(section.getName(), content, section.getPriority());
    }

    private void registerToolNames(DeepAgent agent) {
        ownedToolNames.add("coding_memory_read");
        ownedToolNames.add("coding_memory_write");
        ownedToolNames.add("coding_memory_edit");
        if (agent != null && agent.getCard() != null) {
            ownedToolIds.add(agent.getCard().getId() + ":coding_memory");
        }
    }
}
