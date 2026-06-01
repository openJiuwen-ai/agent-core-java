/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * SKILL.md content parameter handle for self-evolution.
 *
 * <p>Manages skill experience records in memory until user approves:</p>
 * <ul>
 *   <li>setParameter("experiences", record_or_list): sync, enqueue into stagedRecords</li>
 *   <li>flushToStore(store): async, write staged records to EvolutionStore one by one</li>
 *   <li>discardStaged(): sync, drop all staged records (user rejection path)</li>
 * </ul>
 *
 * <p>Does NOT read or write files directly. All persistent IO via EvolutionStore.</p>
 *
 * <p>Mirrors Python's SkillCallOperator in openjiuwen.core.operator.skill_call.base.</p>
 */
public class SkillCallOperator extends Operator {

    private final String skillName;
    private final BiConsumer<String, Object> onParameterUpdated;
    private final List<Object> stagedRecords;  // List of EvolutionRecord; head-first write queue
    private final List<Object> flushedRecords;  // records successfully written to store
    private Map<String, Object> cachedState;

    /**
     * Create SkillCallOperator with skill name.
     *
     * @param skillName the skill name for operator ID
     */
    public SkillCallOperator(String skillName) {
        this(skillName, null);
    }

    /**
     * Create SkillCallOperator with skill name and callback.
     *
     * @param skillName the skill name for operator ID
     * @param onParameterUpdated callback when parameters change
     */
    public SkillCallOperator(String skillName, BiConsumer<String, Object> onParameterUpdated) {
        this.skillName = skillName;
        this.onParameterUpdated = onParameterUpdated;
        this.stagedRecords = new ArrayList<>();
        this.flushedRecords = new ArrayList<>();
        this.cachedState = new LinkedHashMap<>();
    }

    @Override
    public String getOperatorId() {
        return "skill_call_" + skillName;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        Map<String, TunableSpec> tunables = new LinkedHashMap<>();
        tunables.put("experiences", new TunableSpec(
                "experiences",
                "skill_experience",
                "content",
                Map.of("type", "record")));
        return tunables;
    }

    /**
     * Sync: enqueue EvolutionRecord(s) into stagedRecords.
     *
     * <p>value may be a single EvolutionRecord or a List of EvolutionRecord.
     * Records are appended to the tail of the staging queue in order.</p>
     *
     * @param target parameter name (should be "experiences")
     * @param value record or list of records to enqueue
     */
    @Override
    public void setParameter(String target, Object value) {
        if (!"experiences".equals(target) || value == null) {
            return;
        }
        List<Object> items;
        if (value instanceof List) {
            items = (List<Object>) value;
        } else {
            items = Collections.singletonList(value);
        }
        stagedRecords.addAll(items);
        if (onParameterUpdated != null) {
            onParameterUpdated.accept(target, items);
        }
    }

    /**
     * Result of flushing an explicit record batch to EvolutionStore.
     */
    public static class FlushResult {
        private final int flushedCount;
        private final List<Object> remainingRecords;

        public FlushResult(int flushedCount, List<Object> remainingRecords) {
            this.flushedCount = flushedCount;
            this.remainingRecords = remainingRecords;
        }

        public int getFlushedCount() {
            return flushedCount;
        }

        public List<Object> getRemainingRecords() {
            return remainingRecords;
        }
    }

    /**
     * Async: write only the specified records to EvolutionStore.
     *
     * <p>This method is used by approval flows that operate on immutable
     * snapshot batches. It never reads or mutates stagedRecords.</p>
     *
     * @param store the evolution store (placeholder: Object)
     * @param records the records to flush
     * @return CompletableFuture with FlushResult
     */
    public CompletableFuture<FlushResult> flushRecordsToStore(Object store, List<Object> records) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> remainingRecords = new ArrayList<>(records);
            int flushed = 0;
            while (!remainingRecords.isEmpty()) {
                Object record = remainingRecords.get(0);
                try {
                    appendRecord(store, record);
                    remainingRecords.remove(0);
                    flushedRecords.add(record);
                    flushed += 1;
                } catch (Exception e) {
                    Loggers.OPERATOR.warning(
                            "[SkillCallOperator] flush failed at record {}: {}; {} record(s) remain in explicit batch",
                            recordId(record), e.getMessage(), remainingRecords.size());
                    break;
                }
            }
            return new FlushResult(flushed, remainingRecords);
        });
    }

    /**
     * Async: write staged records to EvolutionStore one by one.
     *
     * <p>The current staging queue is snapshotted and cleared before any IO.
     * Records staged concurrently while this method awaits store writes are
     * appended to the now-empty live queue and are preserved for the next
     * flush call. If the snapshotted batch partially fails, its unwritten
     * tail is prepended back ahead of any newer staged records.</p>
     *
     * @param store the evolution store (placeholder: Object)
     * @return CompletableFuture with count flushed
     */
    public CompletableFuture<Integer> flushToStore(Object store) {
        List<Object> snapshot = takeSnapshot();
        return flushRecordsToStore(store, snapshot).thenApply(result -> {
            if (!result.getRemainingRecords().isEmpty()) {
                stagedRecords.addAll(0, result.getRemainingRecords());
            }
            return result.getFlushedCount();
        });
    }

    /**
     * Discard all in-memory staged records on user rejection.
     *
     * <p>Only clears the in-memory buffer; nothing is written to or deleted
     * from EvolutionStore (records were never persisted before approval).</p>
     *
     * @return count discarded
     */
    public int discardStaged() {
        int count = stagedRecords.size();
        stagedRecords.clear();
        return count;
    }

    /**
     * Atomically snapshot the current staged records and clear the queue.
     *
     * <p>Returns a stable copy of whatever was staged at the time of the call.
     * Subsequent records will start a fresh queue independent of this snapshot,
     * so concurrent approval requests for the same skill operate on disjoint
     * batches.</p>
     *
     * @return snapshot of staged records
     */
    public List<Object> takeSnapshot() {
        List<Object> snapshot = new ArrayList<>(stagedRecords);
        stagedRecords.clear();
        return snapshot;
    }

    /**
     * Get a copy of current staged records pending approval.
     *
     * @return copy of staged records
     */
    public List<Object> getStagedRecords() {
        return new ArrayList<>(stagedRecords);
    }

    /**
     * Async: load skill content + existing records from store into cachedState.
     *
     * @param store the evolution store (placeholder: Object)
     * @return CompletableFuture
     */
    public CompletableFuture<Void> refreshState(Object store) {
        return CompletableFuture.runAsync(() -> {
            Object skillContent = readSkillContent(store);
            Object descRecords = getPendingRecords(store, EvolutionTarget.DESCRIPTION);
            Object bodyRecords = getPendingRecords(store, EvolutionTarget.BODY);
            Object existingMessages = cachedState.getOrDefault("messages", Collections.emptyList());

            Map<String, Object> nextState = new LinkedHashMap<>();
            nextState.put("skill_content", skillContent);
            nextState.put("desc_records", descRecords);
            nextState.put("body_records", bodyRecords);
            nextState.put("messages", existingMessages);
            cachedState = nextState;
        });
    }

    @Override
    public Map<String, Object> getState() {
        return new LinkedHashMap<>(cachedState);
    }

    @Override
    public void loadState(Map<String, Object> state) {
        this.cachedState = new LinkedHashMap<>(state);
    }

    /**
     * Execute one operator step - not implemented for skill call operator.
     *
     * <p>SkillCallOperator manages parameters only, not execution.</p>
     *
     * @param inputs input data
     * @param session session context
     * @param kwargs additional arguments
     * @return null (not applicable)
     */
    public Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) {
        // SkillCallOperator manages parameters only, not execution
        return null;
    }

    /**
     * Get skill name.
     *
     * @return skill name
     */
    public String getSkillName() {
        return skillName;
    }

    /**
     * Get flushed records (successfully written to store).
     *
     * @return copy of flushed records
     */
    public List<Object> getFlushedRecords() {
        return new ArrayList<>(flushedRecords);
    }

    private void appendRecord(Object store, Object record) throws Exception {
        if (store == null) {
            throw new IllegalArgumentException("store is required");
        }
        if (store instanceof EvolutionStore evolutionStore && record instanceof EvolutionRecord evolutionRecord) {
            boolean saved = evolutionStore.saveRecord(skillName, evolutionRecord);
            if (!saved) {
                throw new IllegalStateException("saveRecord returned false");
            }
            return;
        }

        Method method = findMethod(store.getClass(), record, "appendRecord", "append_record");
        Object result = invokeStoreMethod(store, method, record);
        if (result instanceof Boolean && !((Boolean) result)) {
            throw new IllegalStateException(method.getName() + " returned false");
        }
    }

    private Object readSkillContent(Object store) {
        if (store instanceof EvolutionStore evolutionStore) {
            return evolutionStore.readSkillContent(skillName);
        }
        try {
            Method method = findMethod(store.getClass(), null, "readSkillContent", "read_skill_content");
            return invokeStoreMethod(store, method, null);
        } catch (Exception e) {
            return null;
        }
    }

    private Object getPendingRecords(Object store, EvolutionTarget target) {
        if (store instanceof EvolutionStore evolutionStore) {
            List<EvolutionRecord> records = evolutionStore.loadRecords(skillName);
            if (records == null) {
                return Collections.emptyList();
            }
            List<EvolutionRecord> pending = new ArrayList<>();
            for (EvolutionRecord record : records) {
                if (record != null && record.isPending()
                        && record.getChange() != null
                        && target.equals(record.getChange().getTarget())) {
                    pending.add(record);
                }
            }
            return pending;
        }
        try {
            Method method = findMethod(store.getClass(), target, "getPendingRecords", "get_pending_records");
            return invokeStoreMethod(store, method, target);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Method findMethod(Class<?> type, Object secondArgument, String... names) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            for (String name : names) {
                if (name.equals(method.getName()) && accepts(method, secondArgument)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        Class<?> parent = type.getSuperclass();
        if (parent != null) {
            return findMethod(parent, secondArgument, names);
        }
        throw new NoSuchMethodException(String.join("/", names));
    }

    private boolean accepts(Method method, Object secondArgument) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 1) {
            return parameterTypes[0].isAssignableFrom(String.class);
        }
        if (parameterTypes.length != 2 || !parameterTypes[0].isAssignableFrom(String.class)) {
            return false;
        }
        return secondArgument == null || wrap(parameterTypes[1]).isInstance(secondArgument);
    }

    private Object invokeStoreMethod(Object store, Method method, Object secondArgument) throws Exception {
        try {
            if (method.getParameterCount() == 1) {
                return method.invoke(store, skillName);
            }
            return method.invoke(store, skillName, secondArgument);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private Object recordId(Object record) {
        if (record instanceof EvolutionRecord evolutionRecord) {
            return evolutionRecord.getId();
        }
        return String.valueOf(record);
    }
}
