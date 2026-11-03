/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for graph store operations.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.store.graph.utils} in
 * {@code openjiuwen/core/foundation/store/graph/utils.py}.</p>
 */
public final class GraphStoreUtils {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/store/graph/utils.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of("batched");
    public static final String UNKNOWN_DATETIME = "Unknown Datetime";

    private static final LoggerProtocol STORE_LOGGER = Loggers.STORE;
    private static final Pattern TEMPLATE_FIELD = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final LocalDateTime LOCAL_EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

    private GraphStoreUtils() {
    }

    public static <T> List<List<T>> batched(Iterable<T> iterable, int size) {
        return batched(iterable, size, false);
    }

    public static <T> List<List<T>> batched(Iterable<T> iterable, int size, boolean strict) {
        if (size < 1) {
            throw new IllegalArgumentException("n must be at least one");
        }
        List<List<T>> result = new ArrayList<>();
        List<T> batch = new ArrayList<>(size);
        for (T item : iterable) {
            batch.add(item);
            if (batch.size() == size) {
                result.add(List.copyOf(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            if (strict) {
                throw new IllegalArgumentException("batched(): incomplete batch");
            }
            result.add(List.copyOf(batch));
        }
        return List.copyOf(result);
    }

    public static <T, M> CompletableFuture<MetadataResult<T, M>> withMetadata(CompletableFuture<T> future, M metadata) {
        return future.thenApply(result -> new MetadataResult<>(result, metadata));
    }

    public static String getUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static CompletableFuture<List<Object>> ensureUniqueUuids(GraphStore backend,
                                                                    List<?> ids,
                                                                    String collection) {
        return ensureUniqueUuids(backend, ids, collection, false);
    }

    public static CompletableFuture<List<Object>> ensureUniqueUuids(GraphStore backend,
                                                                    List<?> ids,
                                                                    String collection,
                                                                    boolean skip) {
        return ensureUniqueUuids(backend, ids, collection, skip, GraphStoreUtils::getUuid);
    }

    static CompletableFuture<List<Object>> ensureUniqueUuids(GraphStore backend,
                                                             List<?> ids,
                                                             String collection,
                                                             boolean skip,
                                                             Supplier<String> uuidSupplier) {
        List<Object> uniqueIds = new ArrayList<>(ids.size());
        for (Object id : ids) {
            uniqueIds.add(isMissingId(id) ? uuidSupplier.get() : id);
        }
        if (skip || backend.isEmpty(collection)) {
            return CompletableFuture.completedFuture(List.copyOf(uniqueIds));
        }
        return replaceExistingUuids(backend, uniqueIds, collection, new ArrayList<>(uniqueIds), uuidSupplier)
                .thenApply(List::copyOf);
    }

    public static String formatListOfMessages(List<Map<String, Object>> messages) {
        return formatListOfMessages(messages, Map.of(), "{role}: {content}\n");
    }

    public static String formatListOfMessages(List<Map<String, Object>> messages,
                                              Map<String, String> roleReplace,
                                              String template) {
        Map<String, String> replacements = roleReplace == null ? Map.of() : roleReplace;
        StringBuilder result = new StringBuilder();
        for (Map<String, Object> message : messages) {
            Map<String, Object> values = new LinkedHashMap<>(message);
            Object rawRole = values.remove("role");
            String role = rawRole == null ? "" : String.valueOf(rawRole);
            values.put("role", replacements.getOrDefault(role, role));
            result.append(renderTemplate(template, values));
        }
        return result.toString();
    }

    public static double safeTimestamp(OffsetDateTime dateTime) {
        if (dateTime.getYear() < 1970) {
            OffsetDateTime epoch = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            return secondsBetween(epoch, dateTime);
        }
        return dateTime.toEpochSecond() + dateTime.getNano() / 1_000_000_000.0d;
    }

    public static double safeTimestamp(LocalDateTime dateTime) {
        if (dateTime.getYear() < 1970) {
            return secondsBetween(LOCAL_EPOCH, dateTime);
        }
        return dateTime.atZone(ZoneId.systemDefault()).toEpochSecond() + dateTime.getNano() / 1_000_000_000.0d;
    }

    public static OffsetDateTime safeDatetimeFromTimestamp(double timestamp) {
        return safeDatetimeFromTimestamp(timestamp, ZoneOffset.UTC);
    }

    public static OffsetDateTime safeDatetimeFromTimestamp(double timestamp, ZoneOffset timezone) {
        ZoneOffset tz = timezone == null ? ZoneOffset.UTC : timezone;
        OffsetDateTime epoch = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, tz);
        Duration delta = durationFromSeconds(timestamp);
        if (timestamp < 0) {
            return epoch.minus(delta);
        }
        return OffsetDateTime.ofInstant(Instant.EPOCH.plus(delta), tz);
    }

    public static long getCurrentUtcTimestamp() {
        return (long) safeTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static String formatTimestamp(double timestamp) {
        return formatTimestamp(timestamp, ZoneOffset.UTC, "(%a) %Y/%b/%d %H:%M:%S");
    }

    public static String formatTimestamp(double timestamp, ZoneOffset timezone, String pythonFormat) {
        if (timestamp == -1) {
            return UNKNOWN_DATETIME;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(toJavaDateTimePattern(pythonFormat), Locale.ENGLISH);
        return safeDatetimeFromTimestamp(timestamp, timezone).format(formatter);
    }

    public static String formatTimestampIso(double timestamp) {
        return formatTimestampIso(timestamp, ZoneOffset.UTC);
    }

    public static String formatTimestampIso(double timestamp, ZoneOffset timezone) {
        if (timestamp == -1) {
            return UNKNOWN_DATETIME;
        }
        OffsetDateTime dateTime = safeDatetimeFromTimestamp(timestamp, timezone == null ? ZoneOffset.UTC : timezone);
        String local = dateTime.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        if (timezone == null) {
            return local;
        }
        return local + formatOffset(timezone);
    }

    public static TimestampOffset iso2timestamp(String isoString) {
        String normalized = isoString.replace("24:00:00", "23:59:59");
        if (normalized.endsWith("+")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            if (hasExplicitOffset(normalized)) {
                OffsetDateTime dateTime = OffsetDateTime.parse(normalized);
                return new TimestampOffset((long) safeTimestamp(dateTime), storeTzOffset(toPythonTzName(dateTime.getOffset())));
            }
            LocalDateTime dateTime = LocalDateTime.parse(normalized);
            return new TimestampOffset((long) safeTimestamp(dateTime), storeTzOffset(null));
        } catch (RuntimeException ex) {
            STORE_LOGGER.error("Graph Store: invalid iso -> timestamp conversion ({}): {}", normalized, ex);
            return new TimestampOffset(-1, 0);
        }
    }

    public static Optional<OffsetDateTime> loadStoredTimeFromDb(double timestamp, int offset) {
        if (timestamp != -1) {
            return Optional.of(safeDatetimeFromTimestamp(timestamp, loadTzOffset(offset)));
        }
        return Optional.empty();
    }

    static int storeTzOffset(String timezoneString) {
        if (timezoneString != null && !removePrefix(timezoneString, "UTC").isEmpty()) {
            String[] offsets = removePrefix(timezoneString, "UTC+").split(":");
            String hour = offsets.length > 0 ? offsets[0] : "0";
            String minute = offsets.length > 1 ? offsets[1] : "0";
            return Integer.parseInt(hour) * 4 + Integer.parseInt(minute) / 15;
        }
        return 0;
    }

    static ZoneOffset loadTzOffset(int timezoneOffset) {
        return ZoneOffset.ofTotalSeconds(timezoneOffset * 15 * 60);
    }

    private static CompletableFuture<List<Object>> replaceExistingUuids(GraphStore backend,
                                                                        List<Object> uniqueIds,
                                                                        String collection,
                                                                        List<?> probeIds,
                                                                        Supplier<String> uuidSupplier) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("collection", collection);
        kwargs.put("output_fields", List.of("uuid"));
        return backend.query(collection, probeIds, null, false, kwargs).thenCompose(rows -> {
            List<Object> duplicates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                duplicates.add(row.get("uuid"));
            }
            if (duplicates.isEmpty()) {
                return CompletableFuture.completedFuture(uniqueIds);
            }
            List<Object> newIds = new ArrayList<>();
            for (Object duplicate : duplicates) {
                int index = uniqueIds.indexOf(duplicate);
                if (index < 0) {
                    CompletableFuture<List<Object>> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new IllegalStateException("duplicate uuid not present: " + duplicate));
                    return failed;
                }
                String replacement = uuidSupplier.get();
                uniqueIds.set(index, replacement);
                newIds.add(replacement);
            }
            return replaceExistingUuids(backend, uniqueIds, collection, newIds, uuidSupplier);
        });
    }

    private static boolean isMissingId(Object id) {
        return id == null || Boolean.FALSE.equals(id) || "".equals(id);
    }

    private static String renderTemplate(String template, Map<String, Object> values) {
        Matcher matcher = TEMPLATE_FIELD.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("missing template field: " + key);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(values.get(key))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static double secondsBetween(OffsetDateTime start, OffsetDateTime end) {
        return Duration.between(start, end).toNanos() / 1_000_000_000.0d;
    }

    private static double secondsBetween(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toNanos() / 1_000_000_000.0d;
    }

    private static Duration durationFromSeconds(double seconds) {
        long nanos = Math.round(seconds * 1_000_000_000.0d);
        return Duration.ofNanos(nanos);
    }

    private static String toJavaDateTimePattern(String pythonFormat) {
        return pythonFormat
                .replace("%a", "EEE")
                .replace("%Y", "yyyy")
                .replace("%b", "MMM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%M", "mm")
                .replace("%S", "ss");
    }

    private static boolean hasExplicitOffset(String value) {
        int timeSeparator = value.indexOf('T');
        if (timeSeparator < 0) {
            timeSeparator = value.indexOf(' ');
        }
        if (timeSeparator < 0) {
            return false;
        }
        String timePart = value.substring(timeSeparator + 1);
        return timePart.endsWith("Z") || timePart.contains("+") || timePart.indexOf('-', 1) >= 0;
    }

    private static String toPythonTzName(ZoneOffset offset) {
        if (ZoneOffset.UTC.equals(offset)) {
            return "UTC";
        }
        return "UTC" + offset.getId();
    }

    private static String formatOffset(ZoneOffset offset) {
        if (ZoneOffset.UTC.equals(offset)) {
            return "+00:00";
        }
        return offset.getId();
    }

    private static String removePrefix(String value, String prefix) {
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    /**
     * Tuple-style result for Python's {@code with_metadata} in
     * {@code openjiuwen/core/foundation/store/graph/utils.py}.
     */
    public record MetadataResult<T, M>(T result, M metadata) {
    }

    /**
     * Tuple-style timestamp and timezone offset result for Python's {@code iso2timestamp} in
     * {@code openjiuwen/core/foundation/store/graph/utils.py}.
     */
    public record TimestampOffset(long timestamp, int offset) {
    }
}
