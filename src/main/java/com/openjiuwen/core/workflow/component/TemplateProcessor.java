/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared template-rendering state for the {@link End} component, enabling
 * concurrent {@code renderStream} calls (mix-mode STREAM+TRANSFORM, or
 * INVOKE+COLLECT via {@link TemplateBatchProcessor}) to coordinate production
 * of each template segment exactly once.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.end_comp.TemplateProcessor}.
 * Uses {@link ReentrantLock}+{@link Condition}+{@link BlockingQueue} instead of
 * asyncio.Lock/Condition/AsyncGenerator. Each {@link #renderStream} call spawns
 * a worker that runs the shared render loop, pushing {@code Map{"data","index"}}
 * frames into a per-call queue that the caller drains synchronously.
 *
 * @since 0.1.7
 */
public class TemplateProcessor {
    private static final ExecutorService RENDER_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("end-template-render", false);
    private static final Object END_SENTINEL = new Object();
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1000);

    private final String template;
    private final List<String> segments;
    private final Set<Integer> variablePositions;
    private int currentPosition;
    private int chunkIndex;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private int dataSourceCount;
    private int count;

    /**
     * TemplateProcessor.
     *
     * @param template template
     * @since 0.1.7
     */
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

    /**
     * setDataSourceCount.
     *
     * @param dataSourceCount dataSourceCount
     * @since 0.1.7
     */
    public void setDataSourceCount(int dataSourceCount) {
        this.dataSourceCount = dataSourceCount;
        this.count = 0;
    }

    /**
     * currentPosition.
     *
     * @return the result
     * @since 0.1.7
     */
    public int currentPosition() {
        return currentPosition;
    }

    /**
     * getCurrentSegment.
     *
     * @return the result
     * @since 0.1.7
     */
    public String getCurrentSegment() {
        return getSegment(currentPosition);
    }

    /**
     * getSegment.
     *
     * @param pos pos
     * @return the result
     * @since 0.1.7
     */
    private String getSegment(int pos) {
        if (pos >= segments.size()) {
            return "";
        }
        return segments.get(pos);
    }

    /**
     * shouldRender.
     *
     * @return the result
     * @since 0.1.7
     */
    public boolean shouldRender() {
        return variablePositions.contains(currentPosition);
    }

    /**
     * advancePosition.
     *
     * @return the result
     * @since 0.1.7
     */
    public int advancePosition() {
        currentPosition++;
        return currentPosition;
    }

    /**
     * Render the entire template with the given inputs (synchronous).
     *
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public String render(Map<String, Object> inputs) {
        return TemplateUtils.renderTemplate(template, inputs);
    }

    /**
     * Reset position and counters.
     *
     * @since 0.1.7
     */
    public void reset() {
        if (currentPosition != 0) {
            currentPosition = 0;
        }
        chunkIndex = 0;
        count = 0;
    }

    /**
     * isFinished.
     *
     * @return the result
     * @since 0.1.7
     */
    public boolean isFinished() {
        return currentPosition >= segments.size();
    }

    /**
     * Render the template as a stream of {@code Map{"data":..., "index":...}}
     * frames. Concurrent calls coordinate via the shared lock+condition: a call
     * that has no value for the current variable segment waits until a sibling
     * call advances the position, then re-reads. Static segments are yielded by
     * whichever call reaches them first. On exhaustion, all iterators in
     * {@code inputs} are consumed and the per-call counter is incremented;
     * when it reaches {@code dataSourceCount} the shared state is reset for the
     * next batch.
     * <p>
     * Mirrors Python's {@code TemplateProcessor.render_stream(inputs, session, timeout)}.
     *
     * @param inputs  inputs
     * @param session session (used for timeout env lookup)
     * @return the result
     * @since 0.1.7
     */
    public Iterator<Map<String, Object>> renderStream(Map<String, Object> inputs, NodeSessionApi session) {
        return renderStream(inputs, resolveRenderTimeoutMs(session));
    }

    /**
     * renderStream.
     *
     * @param inputs    inputs
     * @param timeoutMs timeoutMs (0 = infinite, -1 = no wait/skip)
     * @return the result
     * @since 0.1.7
     */
    public Iterator<Map<String, Object>> renderStream(Map<String, Object> inputs, long timeoutMs) {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        Map<String, Object> safeInputs = inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>();
        CompletableFuture.runAsync(() -> {
            try {
                runRenderLoop(safeInputs, timeoutMs, queue);
            } finally {
                try {
                    queue.put(END_SENTINEL);
                } catch (InterruptedException e) {
                    // do not self-interrupt (G.CON.10); worker is exiting anyway
                }
                consumeAllIterators(safeInputs);
                lock.lock();
                try {
                    count++;
                    if (count == dataSourceCount) {
                        reset();
                    }
                } finally {
                    lock.unlock();
                }
                lock.lock();
                try {
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }, RENDER_EXECUTOR);
        return new FrameIterator(queue);
    }

    /**
     * runRenderLoop.
     *
     * @param inputs    inputs
     * @param timeoutMs timeoutMs
     * @param queue     queue
     */
    private void runRenderLoop(Map<String, Object> inputs, long timeoutMs, BlockingQueue<Object> queue) {
        boolean hasAnyValue = needRender(inputs);
        boolean shouldWait = false;
        while (true) {
            if (shouldWait) {
                if (!awaitRenderCondition(timeoutMs)) {
                    return;
                }
                shouldWait = false;
            }
            RenderOutcome outcome = renderCurrentSegment(inputs, queue, hasAnyValue);
            if (outcome == RenderOutcome.FINISHED) {
                return;
            }
            if (outcome == RenderOutcome.NEEDS_WAIT) {
                shouldWait = true;
            }
        }
    }

    /**
     * awaitRenderCondition.
     *
     * @param timeoutMs timeoutMs
     * @return {@code false} if interrupted
     */
    private boolean awaitRenderCondition(long timeoutMs) {
        lock.lock();
        try {
            try {
                if (timeoutMs > 0) {
                    if (!condition.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                        advancePosition();
                    }
                } else if (timeoutMs == 0) {
                    condition.await();
                } else {
                    // timeoutMs < 0: no wait, skip
                    advancePosition();
                }
            } catch (InterruptedException e) {
                // do not self-interrupt (G.CON.10); bail out cooperatively
                return false;
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    /**
     * renderCurrentSegment.
     *
     * @param inputs inputs
     * @param queue queue
     * @param hasAnyValue hasAnyValue
     * @return {@link RenderOutcome} describing the outcome
     */
    private RenderOutcome renderCurrentSegment(
            Map<String, Object> inputs, BlockingQueue<Object> queue, boolean hasAnyValue) {
        lock.lock();
        try {
            if (isFinished()) {
                return RenderOutcome.FINISHED;
            }
            String segment = getCurrentSegment();
            if (!shouldRender()) {
                offerFrame(queue, frameOf(chunkIndex++, segment));
                advancePosition();
                condition.signalAll();
                return RenderOutcome.ADVANCED;
            }
            Object value = SessionUtils.getValueByNestedPath(segment, inputs);
            if (value == null) {
                if (count < dataSourceCount) {
                    return RenderOutcome.NEEDS_WAIT;
                }
                if (!hasAnyValue) {
                    advancePosition();
                    condition.signalAll();
                    return RenderOutcome.ADVANCED;
                }
                return RenderOutcome.NEEDS_WAIT;
            }
            if (value instanceof Iterator<?> iter) {
                while (iter.hasNext()) {
                    offerFrame(queue, frameOf(chunkIndex++, iter.next()));
                }
            } else {
                offerFrame(queue, frameOf(chunkIndex++, value));
            }
            advancePosition();
            condition.signalAll();
            return RenderOutcome.ADVANCED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * renderCurrentSegment outcome.
     */
    private enum RenderOutcome {
        /** Template finished, no more segments. */
        FINISHED,
        /** Segment produced output or advanced position; signal others. */
        ADVANCED,
        /** No value available; caller should wait. */
        NEEDS_WAIT
    }

    /**
     * offerFrame.
     *
     * @param queue queue
     * @param frame frame
     */
    private void offerFrame(BlockingQueue<Object> queue, Map<String, Object> frame) {
        try {
            queue.put(frame);
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10); frame drop is acceptable on interrupt
        }
    }

    /**
     * frameOf.
     *
     * @param index index
     * @param data  data
     * @return the result
     */
    private static Map<String, Object> frameOf(int index, Object data) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("data", data);
        frame.put("index", index);
        return frame;
    }

    /**
     * resolveRenderTimeoutMs.
     *
     * @param session session
     * @return the result
     */
    private static long resolveRenderTimeoutMs(NodeSessionApi session) {
        if (session == null) {
            return 5000L;
        }
        Object raw = session.getEnv(
                com.openjiuwen.core.session.constants.SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY);
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
     * needRender.
     *
     * @param inputs inputs
     * @return the result
     */
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

    /**
     * consumeAllIterators.
     *
     * @param inputs inputs
     */
    @SuppressWarnings("unchecked")
    private static void consumeAllIterators(Map<String, Object> inputs) {
        for (Map.Entry<List<String>, Object> entry : DictUtils.extractLeafNodes(inputs, null)) {
            Object value = entry.getValue();
            if (value instanceof Iterator<?> it) {
                while (it.hasNext()) {
                    it.next();
                }
            }
        }
    }

    /**
     * FrameIterator.
     */
    private static final class FrameIterator implements Iterator<Map<String, Object>> {
        private final BlockingQueue<Object> queue;
        private Map<String, Object> nextFrame;
        private boolean isExhausted = false;

        FrameIterator(BlockingQueue<Object> queue) {
            this.queue = queue;
        }

        @Override
        public boolean hasNext() {
            if (isExhausted) {
                return false;
            }
            if (nextFrame != null) {
                return true;
            }
            try {
                Object item = queue.take();
                if (item == END_SENTINEL) {
                    isExhausted = true;
                    return false;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> frame = (Map<String, Object>) item;
                nextFrame = frame;
                return true;
            } catch (InterruptedException e) {
                // do not self-interrupt (G.CON.10); mark iterator exhausted
                isExhausted = true;
                return false;
            }
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
    }
}
