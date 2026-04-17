/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.session.NodeSessionApi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Batch template renderer for the End component.
 * <p>
 * Collects inputs from multiple data sources and renders the template
 * once all inputs are available.
 * <p>
 * Mirrors Python's {@code TemplateBatchProcessor} from {@code end_comp.py}.
 */
public class TemplateBatchProcessor {

    private final TemplateProcessor template;
    private final Map<String, Object> inputs;
    private volatile boolean rendered = false;

    public TemplateBatchProcessor(TemplateProcessor template, Map<String, Object> inputs) {
        this.template = template;
        this.inputs = inputs != null ? new HashMap<>(inputs) : new HashMap<>();
    }

    public boolean isRendered() {
        return rendered;
    }

    /**
     * Render the template by merging the initial inputs with the additional ones.
     * Streams through the template processor and concatenates all frame data.
     * <p>
     * Mirrors Python's {@code TemplateBatchProcessor.render(inputs, session)}.
     */
    public String render(Map<String, Object> additionalInputs, NodeSessionApi session) {
        rendered = true;
        Map<String, Object> mergedInputs = new HashMap<>(this.inputs);
        if (additionalInputs != null) {
            mergedInputs.putAll(additionalInputs);
        }
        Iterator<Map<String, Object>> frames = template.renderStream(mergedInputs, session);
        StringBuilder answer = new StringBuilder();
        while (frames.hasNext()) {
            Map<String, Object> frame = frames.next();
            Object data = frame.get("data");
            if (data != null) {
                answer.append(data);
            }
        }
        return answer.toString();
    }
}
