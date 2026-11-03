/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.List;

/**
 * Prompt template resource manager.
 *
 * <p>Mirrors Python's {@code PromptMgr} in
 * {@code openjiuwen/core/runner/resources_manager/prompt_manager.py}.</p>
 */
public class PromptManager {

    private final ThreadSafeDict<String, PromptTemplate> repo = new ThreadSafeDict<>();
    private final ThreadSafeDict<String, Object> compatibilityRepo = new ThreadSafeDict<>();

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
            addPrompt(entry.templateId(), entry.template());
        }
    }

    public PromptTemplate removePrompt(String templateId) {
        PromptTemplate removed = repo.pop(templateId, null);
        if (removed == null) {
            compatibilityRepo.pop(templateId, null);
        }
        return removed;
    }

    public PromptTemplate getPrompt(String templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("template_id is invalid, can not be None");
        }
        return repo.get(templateId);
    }

    public void put(String templateId, Object template) {
        compatibilityRepo.put(templateId, template);
    }

    public boolean contains(String templateId) {
        return repo.get(templateId) != null || compatibilityRepo.get(templateId) != null;
    }

    public int size() {
        return repo.size() + compatibilityRepo.size();
    }

    public Object get(String templateId) {
        Object compatibilityValue = compatibilityRepo.get(templateId);
        return compatibilityValue != null ? compatibilityValue : repo.get(templateId);
    }

    /**
     * Typed Java carrier for Python's {@code tuple[str, PromptTemplate]} items in
     * {@code PromptMgr.add_prompts}.
     *
     * @param templateId prompt template id
     * @param template prompt template
     */
    public record PromptEntry(String templateId, PromptTemplate template) {
    }
}
