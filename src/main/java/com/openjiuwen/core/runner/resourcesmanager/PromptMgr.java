// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.List;

/**
 * Prompt模板管理器
 * 
 * 对应Python: resources_manager/prompt_manager.py - PromptMgr
 */
public class PromptMgr {
    
    private final ThreadSafeDict<String, Object> repo = new ThreadSafeDict<>();
    
    public PromptMgr() {
    }
    
    /**
     * 添加Prompt模板
     * 
     * @param templateId 模板ID
     * @param template 模板对象
     * @throws JiuWenBaseException 如果templateId或template为null
     */
    public void addPrompt(String templateId, Object template) {
        if (templateId == null) {
            throw new JiuWenBaseException(
                StatusCode.SESSION_PROMPT_ADD_FAILED.getCode(),
                StatusCode.SESSION_PROMPT_ADD_FAILED.getMessage()
                    .replace("{reason}", "template_id is invalid, can not be None")
            );
        }
        if (template == null) {
            throw new JiuWenBaseException(
                StatusCode.SESSION_PROMPT_ADD_FAILED.getCode(),
                StatusCode.SESSION_PROMPT_ADD_FAILED.getMessage()
                    .replace("{reason}", "template is invalid, can not be None")
            );
        }
        repo.put(templateId, template);
    }
    
    /**
     * 批量添加Prompt模板
     * 
     * @param templates 模板条目列表
     */
    public void addPrompts(List<PromptEntry> templates) {
        if (templates == null) {
            return;
        }
        for (PromptEntry entry : templates) {
            addPrompt(entry.templateId(), entry.template());
        }
    }
    
    /**
     * 获取Prompt模板
     * 
     * @param templateId 模板ID
     * @return 模板对象，不存在返回null
     * @throws JiuWenBaseException 如果templateId为null
     */
    public Object getPrompt(String templateId) {
        if (templateId == null) {
            throw new JiuWenBaseException(
                StatusCode.SESSION_PROMPT_GET_FAILED.getCode(),
                StatusCode.SESSION_PROMPT_GET_FAILED.getMessage()
                    .replace("{reason}", "template_id is invalid, can not be None")
            );
        }
        return repo.get(templateId);
    }
    
    /**
     * 移除Prompt模板
     * 
     * @param templateId 模板ID
     * @return 被移除的模板对象，不存在返回null
     */
    public Object removePrompt(String templateId) {
        return repo.pop(templateId, null);
    }
    
    /**
     * Prompt条目记录
     */
    public record PromptEntry(String templateId, Object template) {}
}

