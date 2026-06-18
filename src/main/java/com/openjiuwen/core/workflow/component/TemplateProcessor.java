/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Template streaming/rendering processor for the End component.
 * <p>
 * Manages template segments, variable positions, and supports both
 * synchronous rendering and streaming output.
 * <p>
 * Mirrors Python's {@code TemplateProcessor} in
 * {@code openjiuwen/core/workflow/components/flow/end_comp.py}.
 */
public class TemplateProcessor {

    private final String template;
    private final List<String> segments;
    private final Set<Integer> variablePositions;
    private int currentPosition;
    private int chunkIndex;
    private int dataSourceCount;
    private int count;
    private final Object lock = new Object();

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
        synchronized (lock) {
            this.dataSourceCount = dataSourceCount;
            this.count = 0;
        }
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
        synchronized (lock) {
            resetLocked();
        }
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
    public Iterator<Map<String, Object>> renderStream(Map<String, Object> inputs, BaseSession session) {
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
        long waitTimeoutMs = resolveTimeoutMillis(session);
        boolean hasAnyValue = needRender(safeInputs);

        return new Iterator<>() {
            private Iterator<?> currentIterator;
            private Map<String, Object> nextFrame;
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (finished) {
                    return false;
                }
                if (nextFrame != null) {
                    return true;
                }
                nextFrame = prepareNext();
                if (nextFrame == null) {
                    finished = true;
                    finish(safeInputs);
                    return false;
                }
                return true;
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                Map<String, Object> current = nextFrame;
                nextFrame = null;
                return current;
            }

            private Map<String, Object> prepareNext() {
                while (true) {
                    if (currentIterator != null) {
                        if (currentIterator.hasNext()) {
                            return frame(currentIterator.next());
                        }
                        currentIterator = null;
                        synchronized (lock) {
                            advancePosition();
                            lock.notifyAll();
                        }
                        continue;
                    }

                    synchronized (lock) {
                        if (currentPosition >= segments.size()) {
                            return null;
                        }

                        String segment = getSegment(currentPosition);
                        if (!variablePositions.contains(currentPosition)) {
                            Map<String, Object> frame = frameLocked(segment);
                            advancePosition();
                            lock.notifyAll();
                            return frame;
                        }

                        Object value = SessionUtils.getValueByNestedPath(segment, safeInputs);
                        if (value == null) {
                            if (shouldWaitForAnotherSource() || hasAnyValue) {
                                long waitedMs = waitForTemplatePosition(waitTimeoutMs);
                                if (waitTimeoutMs > 0
                                        && waitedMs >= waitTimeoutMs
                                        && currentPosition < segments.size()
                                        && segment.equals(getSegment(currentPosition))
                                        && SessionUtils.getValueByNestedPath(segment, safeInputs) == null) {
                                    advancePosition();
                                    lock.notifyAll();
                                }
                                continue;
                            }
                            advancePosition();
                            lock.notifyAll();
                            continue;
                        }

                        if (value instanceof Iterator<?> iterator) {
                            currentIterator = iterator;
                            continue;
                        }
                        Map<String, Object> frame = frameLocked(value);
                        advancePosition();
                        lock.notifyAll();
                        return frame;
                    }
                }
            }
        };
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

    private boolean shouldWaitForAnotherSource() {
        return dataSourceCount > 1 && count < dataSourceCount;
    }

    private long waitForTemplatePosition(long waitTimeoutMs) {
        long start = System.nanoTime();
        try {
            if (waitTimeoutMs > 0) {
                lock.wait(waitTimeoutMs);
            } else {
                lock.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private Map<String, Object> frame(Object data) {
        synchronized (lock) {
            return frameLocked(data);
        }
    }

    private Map<String, Object> frameLocked(Object data) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("data", data);
        frame.put("index", chunkIndex++);
        return frame;
    }

    private void finish(Map<String, Object> inputs) {
        consumeAllIterators(inputs);
        synchronized (lock) {
            count++;
            if (count == dataSourceCount) {
                resetLocked();
            }
            lock.notifyAll();
        }
    }

    private void resetLocked() {
        currentPosition = 0;
        chunkIndex = 0;
        count = 0;
    }

    private static void consumeAllIterators(Object value) {
        if (value instanceof Iterator<?> iterator) {
            while (iterator.hasNext()) {
                iterator.next();
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                consumeAllIterators(child);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                consumeAllIterators(child);
            }
        }
    }

    private static long resolveTimeoutMillis(BaseSession session) {
        Object raw = session != null
                ? WorkflowSessionSupport.getEnv(session, SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY)
                : null;
        if (raw instanceof Number number) {
            return Math.max(0L, Math.round(number.doubleValue() * 1000));
        }
        if (raw != null) {
            try {
                return Math.max(0L, Math.round(Double.parseDouble(String.valueOf(raw)) * 1000));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
