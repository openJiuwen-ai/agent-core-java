/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Graph store utility functions.
 * <p>
 * Mirrors Python's {@code store.graph.utils}.
 * 
 * @since 0.1.7
 */
public final class GraphUtils {
    /**
     * GraphUtils.
     * 
     * @since 0.1.7
     */
    private GraphUtils() {
    }

    /**
     * Batch an iterable into fixed-size chunks.
     * <p>
     * Mirrors Python 3.12's {@code itertools.batched()}.
     * 
     * @param iterable the source iterable
     * @param n batch size (must be >= 1)
     * @param strict if true, raise if the last batch is shorter than n
     * @return iterator of batches (each batch is a List)
     * @since 0.1.7
     */
    public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n, boolean strict) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least one, got " + n);
        }
        Iterator<T> source = iterable.iterator();

        return new Iterator<>() {
            private List<T> nextBatch = null;
            private boolean done = false;
            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (nextBatch != null) {
                    return true;
                }
                nextBatch = fetchBatch();
                if (nextBatch == null) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public List<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                List<T> result = nextBatch;
                nextBatch = null;
                return result;
            }

            private List<T> fetchBatch() {
                if (!source.hasNext()) {
                    return java.util.Collections.emptyList();
                }
                List<T> batch = new ArrayList<>(n);
                for (int i = 0; i < n && source.hasNext(); i++) {
                    batch.add(source.next());
                }
                if (strict && batch.size() != n) {
                    throw new IllegalArgumentException(
                            "batched(): incomplete batch (strict mode), got " + batch.size() + " items, expected " + n);
                }
                return batch;
            }
        };
    }

    /**
     * Batch an iterable into fixed-size chunks (non-strict).
     * 
     * @param iterable iterable
     * @param n n
     * @return the result
     * @since 0.1.7
     */
    public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n) {
        return batched(iterable, n, false);
    }

    /**
     * Public record EmbedTask used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record EmbedTask(Object target, String attributeName, String text) {
    }

    /**
     * getUuid.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String getUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * getCurrentUtcTimestamp.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static int getCurrentUtcTimestamp() {
        return (int) Instant.now().getEpochSecond();
    }

    /**
     * formatListOfMessages.
     * 
     * @param messages messages
     * @param roleReplace roleReplace
     * @param template template
     * @return the result
     * @since 0.1.7
     */
    public static String formatListOfMessages(List<Map<String, Object>> messages, Map<String, String> roleReplace,
            String template) {
        StringBuilder result = new StringBuilder();
        Map<String, String> roles = roleReplace != null ? roleReplace : Map.of();
        String resolvedTemplate = template != null ? template : "{role}: {content}\n";
        for (Map<String, Object> raw : messages) {
            Map<String, Object> message = new java.util.LinkedHashMap<>(raw);
            String role = String.valueOf(message.remove("role"));
            String line = resolvedTemplate.replace("{role}", roles.getOrDefault(role, role)).replace("{content}",
                    String.valueOf(message.getOrDefault("content", "")));
            result.append(line);
        }
        return result.toString();
    }

    /**
     * safeTimestamp.
     * 
     * @param datetime datetime
     * @return the result
     * @since 0.1.7
     */
    public static float safeTimestamp(java.time.LocalDateTime datetime) {
        return datetime.toEpochSecond(ZoneOffset.UTC);
    }

    /**
     * safeDatetimeFromTimestamp.
     * 
     * @param timestamp timestamp
     * @param offset offset
     * @since 0.1.7
     */
    public static java.time.OffsetDateTime safeDatetimeFromTimestamp(long timestamp, ZoneOffset offset) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), offset);
    }

    /**
     * formatTimestamp.
     * 
     * @param timestamp timestamp
     * @param offset offset
     * @param format format
     * @return the result
     * @since 0.1.7
     */
    public static String formatTimestamp(long timestamp, ZoneOffset offset, String format) {
        if (timestamp == -1) {
            return "Unknown Datetime";
        }
        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern(format != null ? format : "(EEE) yyyy/MMM/dd HH:mm:ss");
        return safeDatetimeFromTimestamp(timestamp, offset != null ? offset : ZoneOffset.UTC).format(formatter);
    }

    /**
     * formatTimestampIso.
     * 
     * @param timestamp timestamp
     * @param offset offset
     * @return the result
     * @since 0.1.7
     */
    public static String formatTimestampIso(long timestamp, ZoneOffset offset) {
        if (timestamp == -1) {
            return "Unknown Datetime";
        }
        return safeDatetimeFromTimestamp(timestamp, offset != null ? offset : ZoneOffset.UTC).toString();
    }

    /**
     * iso2timestamp.
     * 
     * @param isoString isoString
     * @return the result
     * @since 0.1.7
     */
    public static int[] iso2timestamp(String isoString) {
        try {
            String normalized = isoString.replace("24:00:00", "23:59:59");
            if (normalized.endsWith("+")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(normalized);
            return new int[]{(int) offsetDateTime.toEpochSecond(), storeTzOffset(offsetDateTime.getOffset())};
        } catch (DateTimeParseException e) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(isoString);
                return new int[]{(int) localDateTime.toEpochSecond(ZoneOffset.UTC), 0};
            } catch (DateTimeParseException ignored) {
                return new int[]{-1, 0};
            }
        }
    }

    /**
     * loadStoredTimeFromDb.
     * 
     * @param timestamp timestamp
     * @param offset offset
     * @return the result
     * @since 0.1.7
     */
    public static OffsetDateTime loadStoredTimeFromDb(long timestamp, int offset) {
        if (timestamp == -1) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), loadTzOffset(offset));
    }

    /**
     * ensureUniqueUuids.
     * 
     * @param backend backend
     * @param ids ids
     * @param collection collection
     * @param skip skip
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public static <T> List<T> ensureUniqueUuids(GraphStore backend, List<T> ids, String collection, boolean skip)
            throws Exception {
        List<T> uniqueIds = new ArrayList<>();
        for (T id : ids) {
            uniqueIds.add(id == null ? (T) getUuid() : id);
        }
        if (skip || backend.isEmpty(collection)) {
            return uniqueIds;
        }
        List<Object> queryIds = new ArrayList<>(uniqueIds.stream().map(Object.class::cast).toList());
        List<Map<String, Object>> dupList = backend.query(collection, queryIds, null, false);
        while (!dupList.isEmpty()) {
            List<Object> newUuids = new ArrayList<>();
            for (Map<String, Object> dup : dupList) {
                Object dupUuid = dup.get("uuid");
                int idx = uniqueIds.indexOf(dupUuid);
                if (idx >= 0) {
                    String replacement = getUuid();
                    uniqueIds.set(idx, (T) replacement);
                    newUuids.add(replacement);
                }
            }
            dupList = backend.query(collection, newUuids, null, false);
        }
        return uniqueIds;
    }

    /**
     * storeTzOffset.
     * 
     * @param offset offset
     * @return the result
     * @since 0.1.7
     */
    private static int storeTzOffset(ZoneOffset offset) {
        return offset.getTotalSeconds() / 60 / 15;
    }

    /**
     * loadTzOffset.
     * 
     * @param offset offset
     * @return the result
     * @since 0.1.7
     */
    private static ZoneOffset loadTzOffset(int offset) {
        return ZoneOffset.ofTotalSeconds(offset * 15 * 60);
    }
}
