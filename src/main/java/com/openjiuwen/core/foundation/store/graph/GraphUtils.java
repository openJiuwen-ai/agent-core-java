/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Graph store utility functions.
 * <p>
 * Mirrors Python's {@code store.graph.utils}.
 */
public final class GraphUtils {

    private GraphUtils() {
    }

    /**
     * Batch an iterable into fixed-size chunks.
     * <p>
     * Mirrors Python 3.12's {@code itertools.batched()}.
     *
     * @param iterable the source iterable
     * @param n        batch size (must be >= 1)
     * @param strict   if true, raise if the last batch is shorter than n
     * @param <T>      element type
     * @return iterator of batches (each batch is a List)
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
                    return null;
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
     */
    public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n) {
        return batched(iterable, n, false);
    }

    public static <T, M> CompletableFuture<Map.Entry<T, M>> withMetadata(CompletableFuture<T> future, M metadata) {
        Objects.requireNonNull(future, "future");
        return future.thenApply(result -> new AbstractMap.SimpleImmutableEntry<>(result, metadata));
    }

    public static String getUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static List<Object> ensureUniqueUuids(GraphStore backend, List<?> ids, String collection, boolean skip)
            throws Exception {
        List<Object> uniqueIds = new ArrayList<>();
        for (Object id : ids) {
            uniqueIds.add(id == null || id.toString().isBlank() ? getUuid() : id);
        }
        if (skip || backend == null) {
            return uniqueIds;
        }
        if (!backend.isEmpty(collection)) {
            List<Object> pending = new ArrayList<>(uniqueIds);
            List<String> duplicates = duplicateIds(backend, collection, pending);
            while (!duplicates.isEmpty()) {
                List<Object> replacementIds = new ArrayList<>();
                for (String duplicate : duplicates) {
                    int index = uniqueIds.indexOf(duplicate);
                    String replacement = getUuid();
                    uniqueIds.set(index, replacement);
                    replacementIds.add(replacement);
                }
                duplicates = duplicateIds(backend, collection, replacementIds);
            }
        }
        return uniqueIds;
    }

    public static String formatListOfMessages(List<Map<String, Object>> messages,
                                              Map<String, String> roleReplace,
                                              String template) {
        String actualTemplate = template == null ? "{role}: {content}\n" : template;
        Map<String, String> replacements = roleReplace == null ? Map.of() : roleReplace;
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> message : messages) {
            Map<String, Object> copy = new LinkedHashMap<>(message);
            Object role = copy.remove("role");
            String formatted = actualTemplate
                    .replace("{role}", replacements.getOrDefault(String.valueOf(role), String.valueOf(role)))
                    .replace("{content}", String.valueOf(copy.getOrDefault("content", "")));
            builder.append(formatted);
        }
        return builder.toString();
    }

    public static double safeTimestamp(ZonedDateTime dateTime) {
        if (dateTime.getYear() < 1970) {
            return Duration.between(Instant.EPOCH, dateTime.toInstant()).getSeconds();
        }
        return dateTime.toInstant().getEpochSecond();
    }

    public static int getCurrentUtcTimestamp() {
        return (int) Instant.now().getEpochSecond();
    }

    public static String formatTimestamp(long timestamp, ZoneOffset offset, String pattern) {
        if (timestamp == -1) {
            return "Unknown Datetime";
        }
        ZoneOffset actualOffset = offset == null ? ZoneOffset.UTC : offset;
        String actualPattern = pattern == null ? "(EEE) yyyy/MMM/dd HH:mm:ss" : pattern;
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(timestamp), actualOffset).format(
                java.time.format.DateTimeFormatter.ofPattern(actualPattern));
    }

    public static String formatTimestampIso(long timestamp, ZoneOffset offset) {
        if (timestamp == -1) {
            return "Unknown Datetime";
        }
        ZoneOffset actualOffset = offset == null ? ZoneOffset.UTC : offset;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), actualOffset)
                .withNano(0)
                .toString();
    }

    public static TimestampWithOffset iso2timestamp(String isoString) {
        try {
            String normalized = isoString.replace("24:00:00", "23:59:59");
            if (normalized.endsWith("+")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            OffsetDateTime parsed = OffsetDateTime.parse(normalized);
            return new TimestampWithOffset(parsed.toEpochSecond(), storeTzOffset(parsed.getOffset().getId()));
        } catch (DateTimeException ex) {
            return new TimestampWithOffset(-1, 0);
        }
    }

    public static OffsetDateTime loadStoredTimeFromDb(long timestamp, int offset) {
        if (timestamp == -1) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), loadTzOffset(offset));
    }

    public static int storeTzOffset(String tzString) {
        if (tzString == null || tzString.isBlank() || "UTC".equals(tzString)) {
            return 0;
        }
        String normalized = tzString.replace("UTC", "");
        ZoneOffset offset = ZoneOffset.of(normalized);
        return offset.getTotalSeconds() / (15 * 60);
    }

    public static ZoneOffset loadTzOffset(int tzOffset) {
        return ZoneOffset.ofTotalSeconds(tzOffset * 15 * 60);
    }

    public record TimestampWithOffset(long timestamp, int offset) {
    }

    private static List<String> duplicateIds(GraphStore backend, String collection, List<Object> ids) throws Exception {
        List<Map<String, Object>> existing = backend.query(collection, ids, null, false);
        List<String> duplicates = new ArrayList<>();
        for (Map<String, Object> item : existing) {
            Object uuid = item.get("uuid");
            if (uuid != null) {
                duplicates.add(String.valueOf(uuid));
            }
        }
        return duplicates;
    }
}
