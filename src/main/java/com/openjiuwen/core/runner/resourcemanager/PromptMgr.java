/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for PromptTemplate instances.
 * <p>
 * Unlike other managers, PromptMgr stores instances directly (not providers).
 * Mirrors Python's {@code PromptMgr} in {@code resources_manager/prompt_manager.py}.
 */
public class PromptMgr {

    private final ConcurrentHashMap<String, PromptTemplate> repo = new ConcurrentHashMap<>();

    public void addPrompt(String templateId, PromptTemplate template) {
        if (templateId == null) {
            throw new IllegalArgumentException("template_id is invalid, can not be None");
        }
        if (template == null) {
            throw new IllegalArgumentException("template is invalid, can not be None");
        }
        repo.put(templateId, template);
    }

    public void addPrompts(List<PromptEntry> templates) {
        if (templates == null) {
            return;
        }
        for (PromptEntry entry : templates) {
            addPrompt(entry.id(), entry.template());
        }
    }

    public PromptTemplate removePrompt(String templateId) {
        return repo.remove(templateId);
    }

    /**
     * Clear all registered prompts.
     */
    public void clear() {
        repo.clear();
    }

    public PromptTemplate getPrompt(String templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("template_id is invalid, can not be None");
        }
        return repo.get(templateId);
    }

    public record PromptEntry(String id, PromptTemplate template) {
    }
}
