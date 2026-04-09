  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exit point component of the workflow with optional response template rendering.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.end_comp.End}.
 */
public class End extends WorkflowComponent {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("(\\{\\{[^}]+\\}\\})");

    private final EndConfig conf;
    private final String template;
    private final List<String> segments;
    private final List<Boolean> isVariable;
    private boolean mix = false;

    public End(EndConfig conf) {
        if (conf != null) {
            this.conf = conf;
            this.template = conf.getResponseTemplate();
            this.segments = splitTemplate(template);
            this.isVariable = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                String seg = segments.get(i);
                if (seg.startsWith("{{") && seg.endsWith("}}")) {
                    isVariable.add(true);
                    segments.set(i, seg.substring(2, seg.length() - 2));
                } else {
                    isVariable.add(false);
                }
            }
        } else {
            this.conf = null;
            this.template = null;
            this.segments = null;
            this.isVariable = null;
        }
    }

    @SuppressWarnings("unchecked")
    public End(Map<String, Object> confMap) {
        this(confMap != null ? EndConfig.fromMap(confMap) : null);
    }

    public End() {
        this((EndConfig) null);
    }

    /**
     * Mark this End component as mixed-mode (concurrent data sources).
     * <p>
     * Mirrors Python's {@code End.set_mix()}.
     */
    public void setMix() {
        this.mix = true;
    }

    public boolean isMix() {
        return mix;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        if (template != null) {
            Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
            String rendered = renderTemplate(template, inputsMap);
            return Map.of("response", rendered);
        }
        if (inputs != null) {
            if (inputs instanceof Map) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) inputs).entrySet()) {
                    if (entry.getValue() != null) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return filtered.isEmpty() ? null : Map.of("output", filtered);
            }
            return Map.of("output", inputs);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
        List<Object> frames = new ArrayList<>();

        if (template != null) {
            int chunkIndex = 0;
            for (int i = 0; i < segments.size(); i++) {
                String seg = segments.get(i);
                Object data;
                if (isVariable.get(i)) {
                    data = getNestedValue(seg, inputsMap);
                } else {
                    data = seg;
                }
                if (data instanceof Iterator<?> iterator) {
                    while (iterator.hasNext()) {
                        Map<String, Object> frame = new HashMap<>();
                        frame.put("type", Constant.END_NODE_STREAM);
                        frame.put("index", chunkIndex++);
                        frame.put("payload", Map.of("response", iterator.next()));
                        frames.add(frame);
                    }
                    continue;
                }
                if (data != null) {
                    Map<String, Object> frame = new HashMap<>();
                    frame.put("type", Constant.END_NODE_STREAM);
                    frame.put("index", chunkIndex++);
                    frame.put("payload", Map.of("response", data));
                    frames.add(frame);
                }
            }
        } else {
            if (inputsMap != null) {
                for (Map.Entry<String, Object> entry : inputsMap.entrySet()) {
                    frames.add(Map.of("output", Map.of(entry.getKey(), entry.getValue())));
                }
            }
        }
        return frames.iterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
        if (template != null) {
            return templateTransformIterator(inputsMap);
        }
        return outputTransformIterator(inputsMap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
        if (template != null) {
            Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
            String rendered = renderTemplate(template, materializeStreamingInputs(inputsMap));
            return Map.of("response", rendered);
        }
        if (inputs instanceof Map) {
            List<Object> chunks = new ArrayList<>();
            for (Map.Entry<List<String>, Object> entry : DictUtils.extractLeafNodes(inputs, null)) {
                String path = DictUtils.formatPath(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Iterator<?> iterator) {
                    while (iterator.hasNext()) {
                        chunks.add(Map.of(path, iterator.next()));
                    }
                } else {
                    chunks.add(Map.of(path, value));
                }
            }
            return Map.of("output", chunks);
        }
        return Map.of("output", inputs);
    }

    private static Map<String, Object> materializeStreamingInputs(Map<String, Object> inputs) {
        List<Map.Entry<List<String>, Object>> entries = new ArrayList<>();
        for (Map.Entry<List<String>, Object> entry : DictUtils.extractLeafNodes(inputs, null)) {
            Object value = entry.getValue();
            if (value instanceof Iterator<?> iterator) {
                List<Object> values = new ArrayList<>();
                while (iterator.hasNext()) {
                    values.add(iterator.next());
                }
                entries.add(new AbstractMap.SimpleEntry<>(entry.getKey(), values));
            } else {
                entries.add(new AbstractMap.SimpleEntry<>(entry.getKey(), value));
            }
        }
        return DictUtils.rebuildDict(entries);
    }

    private static OutputSchema buildTemplateFrame(int index, Object data) {
        return new OutputSchema(Constant.END_NODE_STREAM, index, Map.of("response", data));
    }

    private Iterator<Object> templateTransformIterator(Map<String, Object> inputsMap) {
        return new Iterator<>() {
            private int segmentIndex = 0;
            private int chunkIndex = 0;
            private Iterator<?> currentIterator;
            private Object nextFrame;
            private boolean prepared = false;

            @Override
            public boolean hasNext() {
                prepareNext();
                return nextFrame != null;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                Object current = nextFrame;
                nextFrame = null;
                prepared = false;
                return current;
            }

            private void prepareNext() {
                if (prepared) {
                    return;
                }
                prepared = true;
                nextFrame = null;

                while (true) {
                    if (currentIterator != null) {
                        if (currentIterator.hasNext()) {
                            nextFrame = buildTemplateFrame(chunkIndex++, currentIterator.next());
                            return;
                        }
                        currentIterator = null;
                    }

                    if (segmentIndex >= segments.size()) {
                        return;
                    }

                    int currentSegmentIndex = segmentIndex++;
                    String seg = segments.get(currentSegmentIndex);
                    Object data = isVariable.get(currentSegmentIndex)
                            ? getNestedValue(seg, inputsMap)
                            : seg;
                    if (data instanceof Iterator<?> iterator) {
                        currentIterator = iterator;
                        continue;
                    }
                    if (data != null) {
                        nextFrame = buildTemplateFrame(chunkIndex++, data);
                        return;
                    }
                }
            }
        };
    }

    private Iterator<Object> outputTransformIterator(Map<String, Object> inputsMap) {
        List<Map.Entry<List<String>, Object>> entries = DictUtils.extractLeafNodes(inputsMap, null);
        return new Iterator<>() {
            private int entryIndex = 0;
            private String currentPath;
            private Iterator<?> currentIterator;
            private Object nextFrame;
            private boolean prepared = false;

            @Override
            public boolean hasNext() {
                prepareNext();
                return nextFrame != null;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                Object current = nextFrame;
                nextFrame = null;
                prepared = false;
                return current;
            }

            private void prepareNext() {
                if (prepared) {
                    return;
                }
                prepared = true;
                nextFrame = null;

                while (true) {
                    if (currentIterator != null) {
                        if (currentIterator.hasNext()) {
                            nextFrame = Map.of("output", Map.of(currentPath, currentIterator.next()));
                            return;
                        }
                        currentIterator = null;
                        currentPath = null;
                    }

                    if (entryIndex >= entries.size()) {
                        return;
                    }

                    Map.Entry<List<String>, Object> entry = entries.get(entryIndex++);
                    String path = DictUtils.formatPath(entry.getKey());
                    Object value = entry.getValue();
                    if (value instanceof Iterator<?> iterator) {
                        currentPath = path;
                        currentIterator = iterator;
                        continue;
                    }
                    nextFrame = Map.of("output", Map.of(path, value));
                    return;
                }
            }
        };
    }

    // ==================== Template rendering ====================

    /**
     * Render a template string with {{variable}} substitution.
     */
    static String renderTemplate(String template, Map<String, Object> inputs) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(template, lastEnd, matcher.start());
            String varName = matcher.group(1);
            varName = varName.substring(2, varName.length() - 2);
            Object value = getNestedValue(varName, inputs);
            result.append(value != null ? value.toString() : "");
            lastEnd = matcher.end();
        }
        result.append(template.substring(lastEnd));
        return result.toString();
    }

    /**
     * Split template into segments (static text and {{variable}} parts).
     */
    static List<String> splitTemplate(String template) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.add(template.substring(lastEnd, matcher.start()));
            }
            result.add(matcher.group(1));
            lastEnd = matcher.end();
        }
        if (lastEnd < template.length()) {
            result.add(template.substring(lastEnd));
        }
        return result;
    }

    /**
     * Get a value from a nested map using a dot-separated path.
     */
    @SuppressWarnings("unchecked")
    static Object getNestedValue(String path, Map<String, Object> data) {
        if (data == null || path == null) {
            return null;
        }
        // Try direct key first
        if (data.containsKey(path)) {
            return data.get(path);
        }
        // Try nested path
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
