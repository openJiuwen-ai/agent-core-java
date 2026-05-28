/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;

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
     * <p>Placeholder implementation - EvolutionStore type may not exist yet.</p>
     *
     * @param store the evolution store (placeholder: Object)
     * @param records the records to flush
     * @return CompletableFuture with FlushResult
     */
    public CompletableFuture<FlushResult> flushRecordsToStore(Object store, List<Object> records) {
        // Placeholder implementation - EvolutionStore integration pending
        return CompletableFuture.completedFuture(new FlushResult(records.size(), Collections.emptyList()));
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
     * <p>Placeholder implementation - EvolutionStore integration pending.</p>
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
     * <p>Placeholder implementation - EvolutionStore integration pending.</p>
     *
     * @param store the evolution store (placeholder: Object)
     * @return CompletableFuture
     */
    public CompletableFuture<Void> refreshState(Object store) {
        // Placeholder implementation - EvolutionStore integration pending
        return CompletableFuture.completedFuture(null);
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
    @Override
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
}