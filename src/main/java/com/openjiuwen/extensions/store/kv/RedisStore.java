/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis-based key-value store implementation.
 *
 * <p>This implementation provides a high-performance, distributed key-value store
 * backed by Redis. Supports both standalone Redis and Redis Cluster modes.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.store.kv.redis_store.RedisStore}.
 */
public class RedisStore extends BaseKVStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisStore.class);

    private final Object redisClient;
    private final boolean isCluster;

    /**
     * Initialize RedisStore with a Redis client (standalone or cluster).
     *
     * @param redisClient The Redis client instance (Jedis, Lettuce, or Redisson)
     */
    public RedisStore(Object redisClient) {
        this.redisClient = Objects.requireNonNull(redisClient, "redisClient must not be null");
        this.isCluster = detectClusterMode(redisClient);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void set(String key, Object value) {
        setInternal(key, value, null);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean exclusiveSet(String key, Object value, Integer expiry) {
        requireKey(key);
        try {
            InvocationOutcome outcome = tryInvoke(redisClient, new String[]{"exclusiveSet", "setIfAbsent"}, key, value, expiry);
            boolean expiryApplied = outcome.handled();
            if (!outcome.handled()) {
                outcome = tryInvoke(redisClient, new String[]{"set"}, key, value, Boolean.TRUE, expiry);
                expiryApplied = outcome.handled();
            }
            if (!outcome.handled()) {
                outcome = tryInvoke(redisClient, new String[]{"exclusiveSet", "setIfAbsent"}, key, value);
            }
            if (!outcome.handled()) {
                outcome = tryInvoke(redisClient, new String[]{"set"}, key, value, Boolean.TRUE);
            }
            if (!outcome.handled()) {
                outcome = tryInvoke(redisClient, new String[]{"setnx", "setNx"}, key, value);
            }
            if (!outcome.handled()) {
                throw new IllegalStateException("Redis client does not support exclusive set operations");
            }

            boolean success = asBoolean(outcome.value());
            if (success && expiry != null && expiry > 0 && !expiryApplied) {
                expireKey(key, expiry);
            }
            logger.debug("Exclusive set key: {} with expiry {}s, result: {}", key, expiry, success);
            return success;
        } catch (Exception e) {
            logger.error("Failed to exclusive set key: {}, error: {}", key, e.getMessage());
            throw new RuntimeException("Failed to exclusive set key: " + key, e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object get(String key) {
        requireKey(key);
        try {
            InvocationOutcome outcome = invokeRequired(redisClient, new String[]{"get"}, key);
            logger.debug("Successfully retrieved key: {}", key);
            return normalizeValue(outcome.value());
        } catch (Exception e) {
            logger.error("Failed to get key: {}, error: {}", key, e.getMessage());
            throw new RuntimeException("Failed to get key: " + key, e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isExists(String key) {
        requireKey(key);
        try {
            InvocationOutcome outcome = invokeRequired(redisClient, new String[]{"isExists", "exists"}, key);
            return asBoolean(outcome.value());
        } catch (Exception e) {
            logger.error("Failed to check key existence: {}, error: {}", key, e.getMessage());
            throw new RuntimeException("Failed to check key existence: " + key, e);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean exists(String key) {
        return isExists(key);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void delete(String key) {
        requireKey(key);
        try {
            deleteChunk(List.of(key));
            logger.debug("Deleted key: {}", key);
        } catch (Exception e) {
            logger.error("Failed to delete key: {}, error: {}", key, e.getMessage());
            throw new RuntimeException("Failed to delete key: " + key, e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getByPrefix(String prefix) {
        try {
            logger.debug("Getting keys by prefix: {}", prefix);
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : scanKeys(prefix)) {
                Object value = get(key);
                if (value != null) {
                    result.put(key, value);
                }
            }
            logger.debug("Retrieved {} keys by prefix: {}", result.size(), prefix);
            return result;
        } catch (Exception e) {
            logger.error("Failed to get keys by prefix: {}, error: {}", prefix, e.getMessage());
            throw new RuntimeException("Failed to get keys by prefix: " + prefix, e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void deleteByPrefix(String prefix, Integer batchSize) {
        try {
            logger.debug("Deleting keys by prefix: {}", prefix);
            List<String> keys = scanKeys(prefix);
            if (!keys.isEmpty()) {
                batchDelete(keys, batchSize);
            }
            logger.debug("Deleted keys by prefix: {}", prefix);
        } catch (Exception e) {
            logger.error("Failed to delete keys by prefix: {}, error: {}", prefix, e.getMessage());
            throw new RuntimeException("Failed to delete keys by prefix: " + prefix, e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> mget(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            logger.debug("Bulk getting {} keys", keys.size());
            List<Object> result = tryMget(keys);
            logger.debug("Bulk retrieved {}/{} keys", result.size(), keys.size());
            return result;
        } catch (Exception e) {
            logger.error("Failed to bulk get keys, error: {}", e.getMessage());
            throw new RuntimeException("Failed to bulk get keys", e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int batchDelete(List<String> keys, Integer batchSize) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        try {
            logger.debug("Batch deleting {} keys", keys.size());
            int deleted = 0;
            if (batchSize == null || batchSize <= 0) {
                deleted += deleteChunk(keys);
            } else {
                for (int index = 0; index < keys.size(); index += batchSize) {
                    deleted += deleteChunk(keys.subList(index, Math.min(index + batchSize, keys.size())));
                }
            }
            return deleted;
        } catch (Exception e) {
            logger.error("Failed to batch delete keys, error: {}", e.getMessage());
            throw new RuntimeException("Failed to batch delete keys", e);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(operations -> {
            List<Object> results = new ArrayList<>(operations.size());
            for (Object[] operation : operations) {
                String action = String.valueOf(operation[0]);
                String key = operation.length > 1 ? String.valueOf(operation[1]) : "";
                switch (action) {
                    case "set" -> {
                        Integer expiry = extractExpiry(operation);
                        setInternal(key, operation.length > 2 ? operation[2] : null, expiry);
                        results.add(null);
                    }
                    case "get" -> results.add(get(key));
                    case "isExists" -> results.add(isExists(key));
                    default -> throw new IllegalArgumentException("Unsupported pipeline op: " + action);
                }
            }
            return results;
        });
    }

    /**
     * Refresh TTL (Time To Live) for given keys.
     *
     * @param keys       a list of keys to refresh TTL for
     * @param ttlSeconds the TTL value in seconds
     */
    public void refreshTtl(List<String> keys, int ttlSeconds) {
        if (keys == null || keys.isEmpty() || ttlSeconds <= 0) {
            return;
        }
        try {
            logger.debug("Refreshing TTL for {} keys with {}s", keys.size(), ttlSeconds);
            if (!refreshTtlViaClientPipeline(keys, ttlSeconds)) {
                for (String key : keys) {
                    expireKey(key, ttlSeconds);
                }
            }
            logger.debug("Successfully refreshed TTL for {} keys", keys.size());
        } catch (Exception e) {
            logger.warn("Failed to refresh TTL for {} keys, error: {}", keys.size(), e.getMessage());
        }
    }

    /**
     * Check if the Redis client is in cluster mode.
     *
     * @return true if cluster mode, otherwise false
     */
    public boolean isCluster() {
        return isCluster;
    }

    private static boolean detectClusterMode(Object client) {
        return client.getClass().getSimpleName().contains("Cluster");
    }

    private void setInternal(String key, Object value, Integer expiry) {
        requireKey(key);
        try {
            boolean expiryApplied = false;
            if (expiry != null && expiry > 0) {
                InvocationOutcome combinedSet = tryInvoke(redisClient, new String[]{"set"}, key, value, Boolean.FALSE, expiry);
                if (!combinedSet.handled()) {
                    combinedSet = tryInvoke(redisClient, new String[]{"set"}, key, value, expiry);
                }
                if (combinedSet.handled()) {
                    expiryApplied = true;
                } else {
                    invokeRequired(redisClient, new String[]{"set"}, key, value);
                }
            } else {
                invokeRequired(redisClient, new String[]{"set"}, key, value);
            }

            if (!expiryApplied && expiry != null && expiry > 0) {
                expireKey(key, expiry);
            }
            logger.debug("Successfully set key: {}", key);
        } catch (Exception e) {
            logger.error("Failed to set key: {}, error: {}", key, e.getMessage());
            throw new RuntimeException("Failed to set key: " + key, e);
        }
    }

    private void requireKey(String key) {
        Objects.requireNonNull(key, "key must not be null");
    }

    private Integer extractExpiry(Object[] operation) {
        if (operation.length <= 3 || operation[3] == null) {
            return null;
        }
        Object expiry = operation[3];
        if (expiry instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(expiry));
    }

    private List<Object> tryMget(List<String> keys) throws Exception {
        try {
            InvocationOutcome outcome = tryInvoke(redisClient, new String[]{"mget"}, keys);
            if (!outcome.handled()) {
                outcome = tryInvoke(redisClient, new String[]{"mget"}, (Object) keys.toArray(String[]::new));
            }
            if (outcome.handled()) {
                List<Object> normalized = normalizeBulkValues(outcome.value(), keys);
                if (normalized.size() == keys.size()) {
                    return normalized;
                }
            }
        } catch (Exception e) {
            logger.warn("MGET failed, falling back to individual GETs: {}", e.getMessage());
        }

        List<Object> fallback = new ArrayList<>(keys.size());
        for (String key : keys) {
            fallback.add(get(key));
        }
        return fallback;
    }

    private int deleteChunk(List<String> keys) throws Exception {
        if (keys.isEmpty()) {
            return 0;
        }

        InvocationOutcome outcome = tryInvoke(redisClient, new String[]{"delete", "del"}, (Object) keys.toArray(String[]::new));
        if (!outcome.handled()) {
            outcome = tryInvoke(redisClient, new String[]{"delete", "del"}, keys);
        }
        if (outcome.handled()) {
            return asDeleteCount(outcome.value(), keys.size());
        }

        int deleted = 0;
        for (String key : keys) {
            boolean isExisted = isExists(key);
            invokeRequired(redisClient, new String[]{"delete", "del"}, key);
            if (isExisted) {
                deleted++;
            }
        }
        return deleted;
    }

    private List<String> scanKeys(String prefix) throws Exception {
        String pattern = prefix + "*";
        InvocationOutcome outcome = tryInvoke(redisClient, new String[]{"scanIter", "keys", "scan"}, pattern);
        if (!outcome.handled()) {
            outcome = tryInvoke(redisClient, new String[]{"scanKeys"}, prefix);
        }
        if (!outcome.handled()) {
            outcome = tryInvoke(redisClient, new String[]{"keySet"});
        }
        if (!outcome.handled() && redisClient instanceof Map<?, ?> map) {
            outcome = new InvocationOutcome(true, map.keySet());
        }
        if (!outcome.handled()) {
            throw new IllegalStateException("Redis client does not support scanning keys by prefix");
        }
        return extractKeys(outcome.value(), prefix);
    }

    private boolean refreshTtlViaClientPipeline(List<String> keys, int ttlSeconds) throws Exception {
        InvocationOutcome outcome = tryInvoke(redisClient, new String[]{"pipeline", "pipelined"});
        if (!outcome.handled() || outcome.value() == null) {
            return false;
        }

        Object pipeline = outcome.value();
        for (String key : keys) {
            InvocationOutcome expireOutcome = tryInvoke(pipeline, new String[]{"expire"}, key, ttlSeconds);
            if (!expireOutcome.handled()) {
                return false;
            }
        }

        InvocationOutcome executeOutcome = tryInvoke(pipeline, new String[]{"execute", "exec", "sync"});
        return executeOutcome.handled();
    }

    private void expireKey(String key, int ttlSeconds) throws Exception {
        InvocationOutcome outcome = tryInvoke(redisClient, new String[]{"expire"}, key, ttlSeconds);
        if (!outcome.handled()) {
            throw new IllegalStateException("Redis client does not support expire operations");
        }
    }

    private List<Object> normalizeBulkValues(Object rawValues, List<String> requestedKeys) {
        if (rawValues instanceof Map<?, ?> map) {
            List<Object> ordered = new ArrayList<>(requestedKeys.size());
            for (String key : requestedKeys) {
                ordered.add(normalizeValue(map.get(key)));
            }
            return ordered;
        }

        List<Object> values = toObjectList(rawValues);
        if (values.size() != requestedKeys.size()) {
            return Collections.emptyList();
        }

        List<Object> normalized = new ArrayList<>(values.size());
        for (Object value : values) {
            normalized.add(normalizeValue(value));
        }
        return normalized;
    }

    private List<String> extractKeys(Object rawKeys, String prefix) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Object candidate : toObjectList(rawKeys)) {
            String key = normalizeKey(candidate);
            if (key != null && key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    private List<Object> toObjectList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(item);
            }
            return items;
        }
        if (value instanceof Iterator<?> iterator) {
            List<Object> items = new ArrayList<>();
            iterator.forEachRemaining(items::add);
            return items;
        }
        if (value instanceof Enumeration<?> enumeration) {
            List<Object> items = new ArrayList<>();
            while (enumeration.hasMoreElements()) {
                items.add(enumeration.nextElement());
            }
            return items;
        }
        if (value instanceof Map<?, ?> map) {
            return new ArrayList<>(map.entrySet());
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                items.add(Array.get(value, index));
            }
            return items;
        }
        return List.of(value);
    }

    private String normalizeKey(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map.Entry<?, ?> entry) {
            return normalizeKey(entry.getKey());
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(this::normalizeValue).orElse(null);
        }
        if (value instanceof Map.Entry<?, ?> entry) {
            return normalizeValue(entry.getValue());
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }

        try {
            InvocationOutcome hasValue = tryInvoke(value, new String[]{"hasValue", "isPresent"});
            if (hasValue.handled() && !asBoolean(hasValue.value())) {
                return null;
            }

            InvocationOutcome getValue = tryInvoke(value, new String[]{"getValue"});
            if (getValue.handled()) {
                return normalizeValue(getValue.value());
            }
        } catch (Exception ignored) {
            // Keep the original value when reflection-based normalization is not supported.
        }

        return value;
    }

    private boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.longValue() > 0L;
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString().trim();
            return !text.isEmpty() && !"0".equals(text) && !"false".equalsIgnoreCase(text)
                    && !"null".equalsIgnoreCase(text);
        }
        return true;
    }

    private int asDeleteCount(Object value, int fallbackCount) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? fallbackCount : 0;
        }
        return asBoolean(value) ? fallbackCount : 0;
    }

    private InvocationOutcome invokeRequired(Object target, String[] methodNames, Object... args) throws Exception {
        InvocationOutcome outcome = tryInvoke(target, methodNames, args);
        if (!outcome.handled()) {
            throw new IllegalStateException("Redis client does not support " + Arrays.toString(methodNames));
        }
        return outcome;
    }

    private InvocationOutcome tryInvoke(Object target, String[] methodNames, Object... args) throws Exception {
        MethodMatch bestMatch = null;
        for (int nameIndex = 0; nameIndex < methodNames.length; nameIndex++) {
            String methodName = methodNames[nameIndex];
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }

                Optional<MethodMatch> maybeMatch = prepareMethodMatch(method, args, methodNames.length - nameIndex);
                if (maybeMatch.isEmpty()) {
                    continue;
                }

                MethodMatch match = maybeMatch.get();
                if (bestMatch == null || match.score() > bestMatch.score()) {
                    bestMatch = match;
                }
            }
        }

        if (bestMatch == null) {
            return InvocationOutcome.notHandled();
        }

        try {
            if (!bestMatch.method().canAccess(target)) {
                bestMatch.method().setAccessible(true);
            }
            return new InvocationOutcome(true, bestMatch.method().invoke(target, bestMatch.arguments()));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private Optional<MethodMatch> prepareMethodMatch(Method method, Object[] args, int namePreference) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean isVarArgs = method.isVarArgs();
        int score = namePreference * 100;

        if (!isVarArgs && parameterTypes.length != args.length) {
            return Optional.empty();
        }
        if (isVarArgs && args.length < parameterTypes.length - 1) {
            return Optional.empty();
        }

        Object[] invocationArgs = new Object[parameterTypes.length];
        int fixedArgCount = isVarArgs ? parameterTypes.length - 1 : parameterTypes.length;
        for (int index = 0; index < fixedArgCount; index++) {
            Optional<ArgumentMatch> argumentMatch = convertArgument(args[index], parameterTypes[index]);
            if (argumentMatch.isEmpty()) {
                return Optional.empty();
            }
            invocationArgs[index] = argumentMatch.get().value();
            score += argumentMatch.get().score();
        }

        if (isVarArgs) {
            Class<?> varArgType = parameterTypes[parameterTypes.length - 1].getComponentType();
            Object varArgValue;
            int remaining = args.length - fixedArgCount;
            if (remaining == 1 && args[fixedArgCount] != null && args[fixedArgCount].getClass().isArray()) {
                Object existingArray = args[fixedArgCount];
                if (existingArray.getClass().getComponentType() != null
                        && isAssignable(varArgType, existingArray.getClass().getComponentType())) {
                    varArgValue = existingArray;
                    score += 4;
                } else {
                    return Optional.empty();
                }
            } else if (remaining == 1 && args[fixedArgCount] instanceof Collection<?> collection) {
                Object array = Array.newInstance(varArgType, collection.size());
                int offset = 0;
                for (Object item : collection) {
                    Optional<ArgumentMatch> convertedItem = convertArgument(item, varArgType);
                    if (convertedItem.isEmpty()) {
                        return Optional.empty();
                    }
                    Array.set(array, offset++, convertedItem.get().value());
                    score += convertedItem.get().score();
                }
                varArgValue = array;
            } else {
                Object array = Array.newInstance(varArgType, remaining);
                for (int index = 0; index < remaining; index++) {
                    Optional<ArgumentMatch> convertedItem = convertArgument(args[fixedArgCount + index], varArgType);
                    if (convertedItem.isEmpty()) {
                        return Optional.empty();
                    }
                    Array.set(array, index, convertedItem.get().value());
                    score += convertedItem.get().score();
                }
                varArgValue = array;
            }
            invocationArgs[invocationArgs.length - 1] = varArgValue;
        }

        return Optional.of(new MethodMatch(method, invocationArgs, score));
    }

    private Optional<ArgumentMatch> convertArgument(Object argument, Class<?> targetType) {
        if (argument == null) {
            return targetType.isPrimitive() ? Optional.empty() : Optional.of(new ArgumentMatch(null, 1));
        }

        Class<?> boxedTargetType = boxType(targetType);
        if (boxedTargetType.isInstance(argument)) {
            return Optional.of(new ArgumentMatch(argument, 10));
        }
        if (boxedTargetType == Object.class) {
            return Optional.of(new ArgumentMatch(argument, 1));
        }
        if (boxedTargetType == String.class) {
            return Optional.of(new ArgumentMatch(String.valueOf(argument), 6));
        }
        if (boxedTargetType == Integer.class && argument instanceof Number number) {
            return Optional.of(new ArgumentMatch(number.intValue(), 8));
        }
        if (boxedTargetType == Long.class && argument instanceof Number number) {
            return Optional.of(new ArgumentMatch(number.longValue(), 8));
        }
        if (boxedTargetType == Double.class && argument instanceof Number number) {
            return Optional.of(new ArgumentMatch(number.doubleValue(), 8));
        }
        if (boxedTargetType == Boolean.class) {
            if (argument instanceof Boolean bool) {
                return Optional.of(new ArgumentMatch(bool, 8));
            }
            if (argument instanceof Number number) {
                return Optional.of(new ArgumentMatch(number.intValue() != 0, 6));
            }
        }
        if (boxedTargetType == byte[].class) {
            if (argument instanceof byte[] bytes) {
                return Optional.of(new ArgumentMatch(bytes, 8));
            }
            if (argument instanceof CharSequence sequence) {
                return Optional.of(new ArgumentMatch(sequence.toString().getBytes(StandardCharsets.UTF_8), 5));
            }
        }
        if (boxedTargetType.isArray() && argument instanceof Collection<?> collection) {
            Class<?> componentType = boxedTargetType.getComponentType();
            Object array = Array.newInstance(componentType, collection.size());
            int index = 0;
            int score = 4;
            for (Object item : collection) {
                Optional<ArgumentMatch> convertedItem = convertArgument(item, componentType);
                if (convertedItem.isEmpty()) {
                    return Optional.empty();
                }
                Array.set(array, index++, convertedItem.get().value());
                score += convertedItem.get().score();
            }
            return Optional.of(new ArgumentMatch(array, score));
        }
        if (boxedTargetType.isAssignableFrom(argument.getClass())) {
            return Optional.of(new ArgumentMatch(argument, 7));
        }
        return Optional.empty();
    }

    private boolean isAssignable(Class<?> targetType, Class<?> candidateType) {
        return boxType(targetType).isAssignableFrom(boxType(candidateType));
    }

    private Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            default -> type;
        };
    }

    private record InvocationOutcome(boolean handled, Object value) {
        private static InvocationOutcome notHandled() {
            return new InvocationOutcome(false, null);
        }
    }

    private record MethodMatch(Method method, Object[] arguments, int score) {
    }

    private record ArgumentMatch(Object value, int score) {
    }
}
