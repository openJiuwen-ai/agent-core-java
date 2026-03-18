/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.*;

/**
 * Template streaming/rendering processor for the End component.
 * <p>
 * Manages template segments, variable positions, and supports both
 * synchronous rendering and streaming output.
 * <p>
 * Mirrors Python's {@code TemplateProcessor} from {@code end_comp.py}.
 */
public class TemplateProcessor {

    private final String template;
    private final List<String> segments;
    private final Set<Integer> variablePositions;
    private int currentPosition;
    private int chunkIndex;
    private int dataSourceCount;
    private int count;

    public TemplateProcessor(String template) {
        this.template = template;
        List<String> rawSegments = TemplateUtils.renderTemplateToList(template);
        this.segments = new ArrayList<>(rawSegments);
        this.variablePositions = new HashSet<>();
        for (int i = 0; i < segments.size(); i++) {
            String seg = segments.get(i);
            if (seg.startsWith("{{") && seg.endsWith("}}")) {
                variablePositions.add(i);
                segments.set(i, seg.substring(2, seg.length() - 2));
            }
        }
        this.currentPosition = 0;
        this.chunkIndex = 0;
        this.dataSourceCount = 1;
        this.count = 0;
    }

    public void setDataSourceCount(int dataSourceCount) {
        this.dataSourceCount = dataSourceCount;
        this.count = 0;
    }

    public int currentPosition() {
        return currentPosition;
    }

    public String getCurrentSegment() {
        return getSegment(currentPosition);
    }

    private String getSegment(int pos) {
        if (pos >= segments.size()) {
            return "";
        }
        return segments.get(pos);
    }

    public boolean shouldRender() {
        return variablePositions.contains(currentPosition);
    }

    public int advancePosition() {
        currentPosition++;
        return currentPosition;
    }

    /**
     * Render the entire template with the given inputs (synchronous).
     */
    public String render(Map<String, Object> inputs) {
        return TemplateUtils.renderTemplate(template, inputs);
    }

    /**
     * Reset position and counters.
     */
    public void reset() {
        if (currentPosition != 0) {
            currentPosition = 0;
        }
        chunkIndex = 0;
        count = 0;
    }

    public boolean isFinished() {
        return currentPosition >= segments.size();
    }

    /**
     * Render the template as a stream of frames.
     * Each frame is a {@code Map} with "data" and "index" keys.
     * <p>
     * Mirrors Python's {@code TemplateProcessor.render_stream(inputs, session, timeout)}.
     * In Java the iteration is synchronous via an {@link Iterator}.
     */
    public Iterator<Map<String, Object>> renderStream(Map<String, Object> inputs, NodeSessionApi session) {
        List<Map<String, Object>> frames = new ArrayList<>();
        boolean hasAnyValue = needRender(inputs);

        while (!isFinished()) {
            String segment = getCurrentSegment();
            if (!shouldRender()) {
                Map<String, Object> frame = new HashMap<>();
                frame.put("data", segment);
                frame.put("index", chunkIndex++);
                frames.add(frame);
                advancePosition();
                continue;
            }

            Object value = SessionUtils.getValueByNestedPath(segment, inputs);
            if (value == null) {
                advancePosition();
                continue;
            }

            if (value instanceof Iterator<?> iter) {
                while (iter.hasNext()) {
                    Map<String, Object> frame = new HashMap<>();
                    frame.put("data", iter.next());
                    frame.put("index", chunkIndex++);
                    frames.add(frame);
                }
            } else {
                Map<String, Object> frame = new HashMap<>();
                frame.put("data", value);
                frame.put("index", chunkIndex++);
                frames.add(frame);
            }
            advancePosition();
        }

        count++;
        if (count == dataSourceCount) {
            reset();
        }

        return frames.iterator();
    }

    private boolean needRender(Object inputs) {
        if (!(inputs instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = (Map<String, Object>) inputs;
        for (int pos = 0; pos < segments.size(); pos++) {
            if (variablePositions.contains(pos)) {
                if (SessionUtils.getValueByNestedPath(segments.get(pos), inputMap) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
