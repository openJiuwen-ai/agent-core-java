/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.memory;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.*;

/**
 * Edit memory tool — performs string replacement on memory files.
 * <p>
 * Mirrors Python's {@code EditMemoryTool} in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class EditMemoryTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public EditMemoryTool(String language, String agentId, Object memoryContext) {
        super(buildCard(language, agentId), null);
        this.memoryContext = memoryContext;
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "EditMemoryTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language) ? "编辑记忆内容。" : "Edit memory content.";
        return toolCard(id, "edit_memory", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String path = (String) inputs.get("path");
        if (path == null || path.isBlank()) {
            return new ToolOutput(false, null, "path is required");
        }
        String oldText = (String) inputs.get("old_text");
        if (oldText == null || oldText.isEmpty()) {
            return new ToolOutput(false, null, "old_text is required");
        }
        String newText = (String) inputs.get("new_text");
        if (newText == null) {
            newText = "";
        }

        Map<String, Object> result = invokeMemoryEdit(path, oldText, newText);
        return new ToolOutput(true, result, null);
    }

    private Map<String, Object> invokeMemoryEdit(String path, String oldText, String newText) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("success", true);

        try {
            if (memoryContext instanceof com.openjiuwen.core.memory.lite.MemoryToolContext ctx) {
                ctx.edit(path, oldText, newText);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return result;
    }
}