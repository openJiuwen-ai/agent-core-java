/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.foundation.store.BaseKVStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KV-backed {@link StepJournal} implementation reusing the shared
 * {@link BaseKVStore} abstraction.
 *
 * <p>Records are stored per session under a fixed key prefix, so replay is a
 * prefix read and purge walks sessions. Keys follow the shape
 * {@code stepjournal:<sessionId>:<seq>}; payload sanitization happens before
 * {@link #append} is reached via {@link StepPayloadSanitizer}.</p>
 *
 * @since 0.1.17
 */
public class KvStepJournal implements StepJournal {
    /** KV key prefix shared by all journal records. */
    public static final String KEY_PREFIX = "stepjournal";

    private final BaseKVStore kvStore;
    private final StepPayloadSanitizer sanitizer;

    /**
     * Create a journal over the given KV store.
     *
     * @param kvStore shared KV store, never null
     */
    public KvStepJournal(BaseKVStore kvStore) {
        this(kvStore, new StepPayloadSanitizer());
    }

    /**
     * Create a journal with a custom sanitizer.
     *
     * @param kvStore shared KV store, never null
     * @param sanitizer payload sanitizer applied before persisting
     */
    public KvStepJournal(BaseKVStore kvStore, StepPayloadSanitizer sanitizer) {
        this.kvStore = kvStore;
        this.sanitizer = sanitizer == null ? new StepPayloadSanitizer() : sanitizer;
    }

    @Override
    public void append(StepRecord record) {
        if (record == null) {
            return;
        }
        try {
            StepRecord sanitized = sanitize(record);
            kvStore.set(recordKey(sanitized.getSessionId(), sanitized.getSeq()), sanitized.toMap()).join();
        } catch (RuntimeException ignored) {
            // Audit must not break the execution path.
        }
    }

    @Override
    public List<StepRecord> replay(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return List.of();
        }
        Map<String, Object> entries;
        try {
            entries = kvStore.getByPrefix(recordPrefix(sessionId)).join();
        } catch (RuntimeException ignored) {
            return List.of();
        }
        List<StepRecord> records = new ArrayList<>();
        if (entries == null) {
            return records;
        }
        List<Long> seqs = new ArrayList<>();
        for (String key : entries.keySet()) {
            seqs.add(seqFromKey(key));
        }
        seqs.sort(Long::compare);
        for (Long seq : seqs) {
            StepRecord record = StepRecord.fromMap(entries.get(recordKey(sessionId, seq)));
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    @Override
    public int purgeBefore(long beforeEpochMillis) {
        Map<String, Object> entries;
        try {
            entries = kvStore.getByPrefix(allRecordsPrefix()).join();
        } catch (RuntimeException ignored) {
            return 0;
        }
        int removed = 0;
        if (entries == null) {
            return removed;
        }
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            StepRecord record = StepRecord.fromMap(entry.getValue());
            if (record != null && record.getTimestamp() < beforeEpochMillis) {
                expired.add(entry.getKey());
            }
        }
        try {
            kvStore.batchDelete(expired, null).join();
            removed = expired.size();
        } catch (RuntimeException ignored) {
            removed = 0;
        }
        return removed;
    }

    private StepRecord sanitize(StepRecord record) {
        String payload = sanitizer.sanitize(record.getPayloadJson());
        return new StepRecord(record.getSessionId(), record.getAgentId(), record.getSeq(),
                record.getStepType(), record.getStepName(), payload, record.getStatus(), record.getTimestamp());
    }

    private String recordKey(String sessionId, long seq) {
        return KEY_PREFIX + ":" + sessionId + ":" + String.format("%016d", seq);
    }

    private String recordPrefix(String sessionId) {
        return KEY_PREFIX + ":" + sessionId + ":";
    }

    private String allRecordsPrefix() {
        return KEY_PREFIX + ":";
    }

    private long seqFromKey(String key) {
        int lastColon = key.lastIndexOf(':');
        if (lastColon < 0 || lastColon == key.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(key.substring(lastColon + 1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
