/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.designer.DesignerJson;
import com.openjiuwen.designer.execution.DesignerNodeException;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Extracts one JSON object from a model reply.
 *
 * @since 2026-11-06
 */
final class DirectorJson {

    private static final Pattern THINK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern FENCE = Pattern.compile("(?is)```(?:json)?\\s*(.*?)```");

    private DirectorJson() {
    }

    static JsonNode extractObject(String reply) {
        String text = THINK.matcher(reply == null ? "" : reply).replaceAll("");
        var fence = FENCE.matcher(text);
        if (fence.find()) {
            text = fence.group(1);
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new DesignerNodeException("model did not return a JSON object");
        }
        try {
            return DesignerJson.mapper().readTree(text.substring(start, end + 1));
        } catch (IOException ex) {
            throw new DesignerNodeException("model returned invalid JSON", ex);
        }
    }
}
