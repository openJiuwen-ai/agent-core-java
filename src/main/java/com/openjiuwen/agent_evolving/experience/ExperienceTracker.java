/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.checkpointing.StoreRecordsHelper;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Tracks presented experiences and updates usage-based scores.
 *
 * <p>Mirrors Python's {@code ExperienceTracker} in
 * {@code openjiuwen/agent_evolving/experience/tracker.py}.</p>
 */
public class ExperienceTracker {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final String PRESENTED_RECORDS_ATTR = "_experience_tracker_presented_records";
    private static final String EVAL_COUNTER_ATTR = "_experience_tracker_eval_counter";
    private static final Map<Object, SessionState> FALLBACK_SESSION_STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final EvolutionStore store;
    private final ExperienceScorer scorer;
    private final int evalInterval;

    public ExperienceTracker(EvolutionStore store, ExperienceScorer scorer, int evalInterval) {
        this.store = store;
        this.scorer = scorer;
        this.evalInterval = evalInterval;
    }

    public static List<PresentedEntry> getSessionPresentedRecords(Object session) {
        if (session == null) {
            return List.of();
        }
        Object value = readSessionValue(session, PRESENTED_RECORDS_ATTR);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return normalizePresentedEntries(list);
    }

    public static void setSessionPresentedRecords(Object session, List<PresentedEntry> records) {
        if (session == null) {
            return;
        }
        List<PresentedEntry> safeRecords = records == null ? List.of() : new ArrayList<>(records);
        writeSessionValue(session, PRESENTED_RECORDS_ATTR, safeRecords);
    }

    public static int getSessionEvalCounter(Object session) {
        if (session == null) {
            return 0;
        }
        Object value = readSessionValue(session, EVAL_COUNTER_ATTR);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public static void setSessionEvalCounter(Object session, int value) {
        if (session == null) {
            return;
        }
        writeSessionValue(session, EVAL_COUNTER_ATTR, value);
    }

    public CompletionStage<Void> recordPresented(Object session, String skillName, String presentationSnippet) {
        return store.getRecordsByScore(skillName, 0.5d)
                .thenCompose(records -> {
                    List<EvolutionRecord> bodyRecords = filterBodyRecords(records);
                    if (bodyRecords.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    List<EvolutionRecord> selectedRecords = bodyRecords.subList(0, Math.min(5, bodyRecords.size()));
                    Map<String, StoreRecordsHelper.RecordUpdate> updates = buildPresentationUpdates(selectedRecords);
                    if (updates.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return store.updateRecordScores(skillName, updates).thenAccept(updated -> {
                        appendPresentedEntries(session, skillName, selectedRecords, updates.keySet(), presentationSnippet);
                        LOGGER.debug(
                                "[ExperienceTracker] tracked {} presented records for skill={}",
                                updates.size(),
                                skillName
                        );
                    });
                })
                .exceptionally(failure -> {
                    LOGGER.debug(
                            "[ExperienceTracker] track presented records failed: {}",
                            unwrap(failure).getMessage()
                    );
                    return null;
                });
    }

    public CompletionStage<Void> recordPresentedRecords(
            Object session,
            String skillName,
            String presentationSnippet,
            List<String> recordIds
    ) {
        if (recordIds == null || recordIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Set<String> requestedIds = new LinkedHashSet<>(recordIds);
        return store.loadFullEvolutionLog(skillName)
                .thenCompose(evolutionLog -> {
                    List<EvolutionRecord> bodyRecords = new ArrayList<>();
                    EvolutionLog safeLog = evolutionLog == null ? EvolutionLog.empty(skillName) : evolutionLog;
                    for (EvolutionRecord record : safeLog.getEntries()) {
                        if (record != null && requestedIds.contains(record.getId()) && isBodyRecord(record)) {
                            bodyRecords.add(record);
                        }
                    }
                    if (bodyRecords.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Map<String, StoreRecordsHelper.RecordUpdate> updates = buildPresentationUpdates(bodyRecords);
                    if (updates.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return store.updateRecordScores(skillName, updates)
                            .thenAccept(updated -> appendPresentedEntries(
                                    session,
                                    skillName,
                                    bodyRecords,
                                    updates.keySet(),
                                    presentationSnippet
                            ));
                })
                .exceptionally(failure -> {
                    LOGGER.debug(
                            "[ExperienceTracker] track explicit presented records failed: {}",
                            unwrap(failure).getMessage()
                    );
                    return null;
                });
    }

    public List<PresentedEntry> consumeEvalState(Object session) {
        int counter = getSessionEvalCounter(session) + 1;
        if (counter >= evalInterval) {
            List<PresentedEntry> presentedEntries = getSessionPresentedRecords(session);
            setSessionPresentedRecords(session, List.of());
            setSessionEvalCounter(session, 0);
            return presentedEntries;
        }
        setSessionEvalCounter(session, counter);
        return List.of();
    }

    public CompletionStage<Void> evaluatePresented(List<PresentedEntry> presentedEntries) {
        if (presentedEntries == null || presentedEntries.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<SkillSnippetKey, List<EvolutionRecord>> groups = new LinkedHashMap<>();
        for (PresentedEntry entry : presentedEntries) {
            if (entry == null || entry.record() == null) {
                continue;
            }
            SkillSnippetKey key = new SkillSnippetKey(entry.skillName(), entry.presentationSnippet());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry.record());
        }

        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<SkillSnippetKey, List<EvolutionRecord>> group : groups.entrySet()) {
            chain = chain.thenCompose(ignored -> evaluateGroup(group.getKey(), group.getValue()));
        }
        return chain.exceptionally(failure -> {
            Throwable cause = unwrap(failure);
            LOGGER.warning("[ExperienceTracker] async evaluation failed: {}", cause.getMessage());
            return null;
        });
    }

    private CompletionStage<Void> evaluateGroup(SkillSnippetKey key, List<EvolutionRecord> records) {
        return scorer.evaluate(key.presentationSnippet(), records)
                .thenCompose(evalResults -> {
                    if (evalResults == null || evalResults.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Map<String, StoreRecordsHelper.RecordUpdate> updates = new LinkedHashMap<>();
                    for (Map<String, Object> result : evalResults) {
                        String recordId = stringValue(result == null ? null : result.get("record_id"));
                        if (recordId == null || recordId.isEmpty()) {
                            continue;
                        }
                        for (EvolutionRecord record : records) {
                            if (Objects.equals(record.getId(), recordId)) {
                                double newScore = ExperienceScorer.updateScore(record, result);
                                updates.put(recordId, StoreRecordsHelper.RecordUpdate.of(newScore, record.getUsageStats()));
                                break;
                            }
                        }
                    }
                    if (updates.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return store.updateRecordScores(key.skillName(), updates).thenAccept(updated -> LOGGER.info(
                            "[ExperienceTracker] async evaluation updated {} record(s) for skill={}",
                            updates.size(),
                            key.skillName()
                    ));
                });
    }

    private static List<EvolutionRecord> filterBodyRecords(List<EvolutionRecord> records) {
        List<EvolutionRecord> bodyRecords = new ArrayList<>();
        for (EvolutionRecord record : records == null ? List.<EvolutionRecord>of() : records) {
            if (isBodyRecord(record)) {
                bodyRecords.add(record);
            }
        }
        return bodyRecords;
    }

    private static Map<String, StoreRecordsHelper.RecordUpdate> buildPresentationUpdates(List<EvolutionRecord> records) {
        Map<String, StoreRecordsHelper.RecordUpdate> updates = new LinkedHashMap<>();
        String now = OffsetDateTime.now(ZoneOffset.UTC).toString().replace("Z", "+00:00");
        for (EvolutionRecord record : records) {
            if (record == null || record.getId() == null) {
                continue;
            }
            UsageStats existingStats = record.getUsageStats() == null ? new UsageStats() : record.getUsageStats();
            UsageStats newStats = new UsageStats(
                    existingStats.getTimesPresented() + 1,
                    existingStats.getTimesUsed(),
                    existingStats.getTimesPositive(),
                    existingStats.getTimesNegative(),
                    now,
                    existingStats.getLastEvaluatedAt()
            );
            record.setUsageStats(newStats);
            updates.put(record.getId(), StoreRecordsHelper.RecordUpdate.of(record.getScore(), newStats));
        }
        return updates;
    }

    private static void appendPresentedEntries(
            Object session,
            String skillName,
            List<EvolutionRecord> records,
            Set<String> updatedIds,
            String presentationSnippet
    ) {
        List<PresentedEntry> newEntries = new ArrayList<>();
        for (EvolutionRecord record : records) {
            if (record != null && updatedIds.contains(record.getId())) {
                newEntries.add(new PresentedEntry(skillName, record, presentationSnippet));
            }
        }
        if (newEntries.isEmpty()) {
            return;
        }
        List<PresentedEntry> existing = new ArrayList<>(getSessionPresentedRecords(session));
        existing.addAll(newEntries);
        setSessionPresentedRecords(session, existing);
    }

    private static boolean isBodyRecord(EvolutionRecord record) {
        if (record == null) {
            return false;
        }
        Object dynamicTarget = readRecordTarget(record);
        if (dynamicTarget != null) {
            return isBodyTarget(dynamicTarget);
        }
        EvolutionPatch change = record.getChange();
        return change != null && change.getTarget() == EvolutionTarget.BODY;
    }

    private static Object readRecordTarget(EvolutionRecord record) {
        try {
            Method method = record.getClass().getMethod("getTarget");
            return method.invoke(record);
        } catch (NoSuchMethodException ignored) {
            Field field = findField(record.getClass(), "target");
            if (field == null) {
                return null;
            }
            try {
                field.setAccessible(true);
                return field.get(record);
            } catch (IllegalAccessException ignoredAccess) {
                return null;
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static boolean isBodyTarget(Object target) {
        if (target == EvolutionTarget.BODY) {
            return true;
        }
        if (target instanceof String text) {
            return "body".equals(text) || "BODY".equals(text);
        }
        return false;
    }

    private static Object readSessionValue(Object session, String key) {
        if (session instanceof Map<?, ?> map) {
            return map.get(key);
        }
        Field field = findField(session.getClass(), key);
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(session);
            } catch (IllegalAccessException ignored) {
                return fallbackState(session).get(key);
            }
        }
        return fallbackState(session).get(key);
    }

    @SuppressWarnings("unchecked")
    private static void writeSessionValue(Object session, String key, Object value) {
        if (session instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put(key, value);
            return;
        }
        Field field = findField(session.getClass(), key);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(session, value);
                return;
            } catch (IllegalAccessException ignored) {
                fallbackState(session).put(key, value);
                return;
            }
        }
        fallbackState(session).put(key, value);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static SessionState fallbackState(Object session) {
        synchronized (FALLBACK_SESSION_STATE) {
            return FALLBACK_SESSION_STATE.computeIfAbsent(session, ignored -> new SessionState());
        }
    }

    private static List<PresentedEntry> normalizePresentedEntries(List<?> list) {
        List<PresentedEntry> entries = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof PresentedEntry entry) {
                entries.add(entry);
            } else if (item instanceof List<?> tuple && tuple.size() >= 3 && tuple.get(1) instanceof EvolutionRecord record) {
                entries.add(new PresentedEntry(stringValue(tuple.get(0)), record, stringValue(tuple.get(2))));
            } else if (item instanceof Object[] tuple
                    && tuple.length >= 3
                    && tuple[1] instanceof EvolutionRecord record) {
                entries.add(new PresentedEntry(stringValue(tuple[0]), record, stringValue(tuple[2])));
            }
        }
        return entries;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure;
    }

    /**
     * One presented record with the snippet from its presentation time.
     */
    public record PresentedEntry(String skillName, EvolutionRecord record, String presentationSnippet) {
    }

    private record SkillSnippetKey(String skillName, String presentationSnippet) {
    }

    private static final class SessionState {
        private List<PresentedEntry> presentedRecords = List.of();
        private int evalCounter;

        private Object get(String key) {
            if (PRESENTED_RECORDS_ATTR.equals(key)) {
                return presentedRecords;
            }
            if (EVAL_COUNTER_ATTR.equals(key)) {
                return evalCounter;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private void put(String key, Object value) {
            if (PRESENTED_RECORDS_ATTR.equals(key)) {
                presentedRecords = value instanceof List<?> list
                        ? new ArrayList<>((List<PresentedEntry>) list)
                        : List.of();
            } else if (EVAL_COUNTER_ATTR.equals(key) && value instanceof Number number) {
                evalCounter = number.intValue();
            }
        }
    }
}
