/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exit point component of the workflow with optional response template rendering.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.end_comp.End}.
 * The template path uses {@link TemplateProcessor} so that concurrent mix-mode
 * calls (STREAM+TRANSFORM) and (INVOKE+COLLECT) share segment position state
 * and coordinate via a condition variable, matching the Python reference.
 *
 * @since 0.1.7
 */
public class End extends WorkflowComponent {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("(\\{\\{[^}]+\\}\\})");

    /**
     * Sentinel marking "no result produced" from {@link #renderBatch} (partner
     * call already produced the answer, or interrupted). Replaces {@code null}
     * per G.MET.06 — {@code invoke}/{@code collect} treat it as "no output".
     */
    private static final Object RENDER_BATCH_EMPTY = new Object();
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1000);

    private final EndConfig conf;
    private final String template;
    private final List<String> segments;
    private final List<Boolean> isVariable;
    private final TemplateProcessor templateProcessor;
    private boolean mix = false;

    /**
     * Lock+condition guarding the {@code _render} (batch mix-mode) rendezvous
     * between INVOKE and COLLECT calls on this End instance. The first call
     * creates the {@link TemplateBatchProcessor} and waits for the second call
     * to merge inputs and render, mirroring Python's {@code End._render}.
     */
    private final ReentrantLock renderLock = new ReentrantLock();
    private final Condition renderCondition = renderLock.newCondition();
    private TemplateBatchProcessor batchTemplate;

    /**
     * End.
     *
     * @param conf conf
     * @since 0.1.7
     */
    public End(EndConfig conf) {
        if (conf != null) {
            this.conf = conf;
            this.template = conf.getResponseTemplate();
            this.templateProcessor = new TemplateProcessor(template);
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
            this.templateProcessor = null;
            this.segments = null;
            this.isVariable = null;
        }
    }

    /**
     * End.
     *
     * @param confMap confMap
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public End(Map<String, Object> confMap) {
        this(confMap != null ? EndConfig.fromMap(confMap) : null);
    }

    /**
     * End.
     *
     * @since 0.1.7
     */
    public End() {
        this((EndConfig) null);
    }

    /**
     * Mark this End component as mixed-mode (concurrent data sources).
     * <p>
     * Mirrors Python's {@code End.set_mix()}: configures the shared
     * {@link TemplateProcessor} for two concurrent render_stream data sources.
     *
     * @since 0.1.7
     */
    @Override
    public void setMix() {
        this.mix = true;
        if (templateProcessor != null) {
            templateProcessor.setDataSourceCount(2);
            templateProcessor.reset();
        }
    }

    /**
     * isMix.
     *
     * @return the result
     * @since 0.1.7
     */
    public boolean isMix() {
        return mix;
    }

    /**
     * invoke.
     *
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        if (template != null) {
            Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
            Object result = renderBatch(inputsMap, session);
            // RENDER_BATCH_EMPTY means partner call already produced the answer
            return result == RENDER_BATCH_EMPTY ? null : result;
        }
        if (inputs != null) {
            if (inputs instanceof Map) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) inputs).entrySet()) {
                    if (entry.getValue() != null) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                return Map.of("output", filtered.isEmpty() ? Map.of() : filtered);
            }
            return Map.of("output", inputs);
        }
        return null;
    }

    /**
     * stream.
     *
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();

        if (template != null) {
            Iterator<Map<String, Object>> frames = templateProcessor.renderStream(inputsMap, session);
            return new TemplateFrameAdapter(frames);
        }
        List<Object> frames = new ArrayList<>();
        if (inputsMap != null) {
            for (Map.Entry<String, Object> entry : inputsMap.entrySet()) {
                frames.add(wrapOutput(entry.getKey(), entry.getValue()));
            }
        }
        return frames.iterator();
    }

    /**
     * transform.
     *
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
        if (template != null) {
            Iterator<Map<String, Object>> frames = templateProcessor.renderStream(inputsMap, session);
            return new TemplateFrameAdapter(frames);
        }
        return outputTransformIterator(inputsMap);
    }

    /**
     * collect.
     *
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
        if (template != null) {
            Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : new HashMap<>();
            Object result = renderBatch(inputsMap, session);
            // RENDER_BATCH_EMPTY means partner call already produced the answer
            return result == RENDER_BATCH_EMPTY ? null : result;
        }
        if (inputs instanceof Map) {
            List<Object> chunks = new ArrayList<>();
            for (Map.Entry<List<String>, Object> entry : DictUtils.extractLeafNodes(inputs, null)) {
                String path = DictUtils.formatPath(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Iterator<?> iterator) {
                    while (iterator.hasNext()) {
                        chunks.add(singleKeyMap(path, iterator.next()));
                    }
                } else {
                    chunks.add(singleKeyMap(path, value));
                }
            }
            return Map.of("output", chunks);
        }
        return Map.of("output", inputs);
    }

    /**
     * renderBatch mirrors Python {@code End._render}: for mix-mode INVOKE+COLLECT
     * pair, the first call creates a {@link TemplateBatchProcessor}, awaits the
     * second call on {@link #renderCondition}, and on timeout renders alone;
     * the second call merges inputs, renders, and notifies the first.
     *
     * @param inputs inputs
     * @param session session
     * @return {@code {"response": answer}} or {@code null} when the partner call
     *         already produced the answer
     * @since 0.1.14
     */
    @SuppressWarnings("unchecked")
    private Object renderBatch(Map<String, Object> inputs, NodeSessionApi session) {
        long timeoutMs = resolveBatchReaderTimeoutMs(session);
        renderLock.lock();
        try {
            if (batchTemplate == null) {
                batchTemplate = new TemplateBatchProcessor(templateProcessor, inputs);
                Object firstResult = awaitPartnerOrTimeout(inputs, session, timeoutMs);
                batchTemplate = null;
                return firstResult;
            }
            // Second call: merge inputs, render, notify
            String answer = batchTemplate.render(inputs, session);
            renderCondition.signalAll();
            batchTemplate = null;
            return Map.of("response", answer);
        } finally {
            renderLock.unlock();
        }
    }

    /**
     * awaitPartnerOrTimeout.
     *
     * @param inputs inputs
     * @param session session
     * @param timeoutMs timeoutMs
     * @return {@link #RENDER_BATCH_EMPTY} when partner already produced answer or interrupted
     */
    private Object awaitPartnerOrTimeout(Map<String, Object> inputs, NodeSessionApi session, long timeoutMs) {
        try {
            if (timeoutMs > 0) {
                if (!renderCondition.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    // Timeout: render alone if no partner arrived
                    if (!batchTemplate.isRendered()) {
                        String answer = batchTemplate.render(inputs, session);
                        return Map.of("response", answer);
                    }
                    return RENDER_BATCH_EMPTY;
                }
            } else {
                renderCondition.await();
            }
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10); bail out cooperatively
            return RENDER_BATCH_EMPTY;
        }
        // Notified by partner call — partner produced the answer
        return RENDER_BATCH_EMPTY;
    }

    /**
     * resolveBatchReaderTimeoutMs.
     *
     * @param session session
     * @return the result
     */
    private static long resolveBatchReaderTimeoutMs(NodeSessionApi session) {
        if (session == null) {
            return 5000L;
        }
        Object raw = session.getEnv(SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY);
        if (raw == null) {
            return 5000L;
        }
        Optional<BigDecimal> seconds = toSeconds(raw);
        if (seconds.isEmpty()) {
            return 5000L;
        }
        BigDecimal value = seconds.get();
        return value.signum() > 0
                ? value.multiply(MILLIS_PER_SECOND).setScale(0, RoundingMode.HALF_UP).longValue()
                : -1L;
    }

    /**
     * toSeconds.
     *
     * @param raw raw
     * @return the result, or {@link Optional#empty()} if not a number
     */
    private static Optional<BigDecimal> toSeconds(Object raw) {
        if (raw instanceof Number n) {
            return Optional.of(BigDecimal.valueOf(n.doubleValue()));
        }
        try {
            return Optional.of(new BigDecimal(raw.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Build a single-key map for one collected chunk. Mirrors Python
     * {@code End.collect} which appends {@code {format_path(path): value}} chunks.
     *
     * @param key key
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> singleKeyMap(String key, Object value) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put(key, value);
        return chunk;
    }

    /**
     * materializeStreamingInputs.
     *
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * buildTemplateFrame.
     *
     * @param index index
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private static OutputSchema buildTemplateFrame(int index, Object data) {
        return new OutputSchema(Constant.END_NODE_STREAM, index, Map.of("response", data));
    }

    /**
     * templateTransformIterator.
     *
     * @param inputsMap inputsMap
     * @return the result
     * @since 0.1.7
     */
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
                    Object data = isVariable.get(currentSegmentIndex) ? getNestedValue(seg, inputsMap) : seg;
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

    /**
     * outputTransformIterator.
     *
     * @param inputsMap inputsMap
     * @return the result
     * @since 0.1.7
     */
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
                            nextFrame = wrapOutput(currentPath, currentIterator.next());
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
                    nextFrame = wrapOutput(path, value);
                    return;
                }
            }
        };
    }

    /**
     * wrapOutput.
     *
     * @param key key
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> wrapOutput(String key, Object value) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(key, value);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("output", output);
        return frame;
    }

    // ==================== Template rendering ====================

    /**
     * Render a template string with {{variable}} substitution.
     *
     * @param template template
     * @param inputs inputs
     * @return String
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
     *
     * @param template template
     * @return List<String>
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
     *
     * @param path path
     * @param data data
     * @return Object
     */
    @SuppressWarnings("unchecked")
    static Object getNestedValue(String path, Map<String, Object> data) {
        if (data == null || path == null) {
            return null;
        }
        if (data.containsKey(path)) {
            return data.get(path);
        }
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

    /**
     * Adapter converting {@code Map{"data","index"}} frames from
     * {@link TemplateProcessor#renderStream} into {@link OutputSchema} frames
     * with {@code payload={response: data}}, matching Python {@code End.stream}
     * / {@code End.transform} which yield
     * {@code OutputSchema(type=END_NODE_STREAM, index=frame.index, payload=dict(response=frame.data))}.
     *
     * @since 0.1.14
     */
    private static final class TemplateFrameAdapter implements Iterator<Object> {
        private final Iterator<Map<String, Object>> delegate;

        TemplateFrameAdapter(Iterator<Map<String, Object>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            Map<String, Object> frame = delegate.next();
            Object indexObj = frame.get("index");
            int index = indexObj instanceof Number ? ((Number) indexObj).intValue() : 0;
            Object data = frame.get("data");
            return new OutputSchema(Constant.END_NODE_STREAM, index, Map.of("response", data));
        }
    }
}
