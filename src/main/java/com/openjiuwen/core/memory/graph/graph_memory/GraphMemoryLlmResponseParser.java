/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses graph-memory LLM responses into graph entities, relations, and merge instructions.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.parse_llm_response} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/parse_llm_response.py}.</p>
 */
public final class GraphMemoryLlmResponseParser {

    public static final Pattern MATCH_ISO_DATETIME = Pattern.compile(
            "([0-9]{1,4})-([0-9]{1,2})-([0-9]{1,2})T([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2})"
                    + "(?:Z|\\+([0-9]{1,2}):([0-9]{1,2}))?"
    );

    private GraphMemoryLlmResponseParser() {
    }

    public static GraphStoreUtils.TimestampOffset parseIso(String timeString) {
        if (timeString != null) {
            Matcher matcher = MATCH_ISO_DATETIME.matcher(timeString);
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                int second = Integer.parseInt(matcher.group(6));
                String isoString = String.format("%04d-%02d-%02dT%02d:%02d:%02d",
                        year, month, day, hour, minute, second);
                String offsetHour = matcher.group(7);
                String offsetMinute = matcher.group(8);
                if (offsetHour != null) {
                    isoString += String.format("+%02d", Integer.parseInt(offsetHour));
                    if (offsetMinute != null) {
                        isoString += String.format(":%02d", Integer.parseInt(offsetMinute));
                    }
                }
                return GraphStoreUtils.iso2timestamp(isoString);
            }
        }
        return new GraphStoreUtils.TimestampOffset(-1, 0);
    }

    public static Relation dictToRelation(Map<String, Object> response, List<Entity> entities) {
        return dictToRelation(response, entities, Map.of());
    }

    public static Relation dictToRelation(Map<String, Object> response,
                                          List<Entity> entities,
                                          Map<String, Object> kwargs) {
        Map<String, Object> normalized = unwrapSingleMapping(response);
        Object sourceId = normalized.get("source_id");
        Object targetId = normalized.get("target_id");
        Entity lhs;
        Entity rhs;
        String relationType;
        try {
            int sourceIndex = toInteger(sourceId) - 1;
            int targetIndex = toInteger(targetId) - 1;
            if (sourceIndex < 0 || targetIndex < 0) {
                throw new IllegalArgumentException("relation source_id and target_id must be valid 1-based indices");
            }
            lhs = entities.get(sourceIndex);
            rhs = entities.get(targetIndex);
            relationType = lhs == rhs ? "EntityFact" : "Relation";
        } catch (RuntimeException ex) {
            return null;
        }

        String name = stringValue(normalized, "name", "RELATION");
        String content = normalized.containsKey("fact") ? nullableString(normalized.get("fact")) : name;
        GraphStoreUtils.TimestampOffset validSince = parseIso(nullableString(
                normalized.getOrDefault("valid_since", "")));
        GraphStoreUtils.TimestampOffset validUntil = parseIso(nullableString(
                normalized.getOrDefault("valid_until", "")));

        Relation relation = new Relation(lhs, rhs);
        relation.setObjType(relationType);
        relation.setName(name);
        relation.setContent(content);
        relation.setValidSince(validSince.timestamp());
        relation.setValidUntil(validUntil.timestamp());
        relation.setOffsetSince(validSince.offset());
        relation.setOffsetUntil(validUntil.offset());
        applyGraphKwargs(relation, kwargs);
        return relation;
    }

    public static ParseAllRelationsResult parseAllRelations(List<Map<String, Object>> relations,
                                                            List<?> entities,
                                                            List<EntityTypeDefinition.EntityDef> entityTypes) {
        return parseAllRelations(relations, entities, entityTypes, Map.of());
    }

    public static ParseAllRelationsResult parseAllRelations(List<Map<String, Object>> relations,
                                                            List<?> entities,
                                                            List<EntityTypeDefinition.EntityDef> entityTypes,
                                                            Map<String, Object> kwargs) {
        List<Entity> declaredEntities = declareEntities(entities, entityTypes, kwargs);

        Set<String> existingContents = new LinkedHashSet<>();
        for (Map<String, Object> relation : relations) {
            String newContent = nullableString(relation.getOrDefault("content", "")).trim();
            boolean containedInOldContent = existingContents.stream().anyMatch(oldContent -> oldContent.contains(newContent));
            relation.put("content", containedInOldContent ? "" : newContent);
            existingContents.add(newContent);
        }

        List<Relation> parsedRelations = new ArrayList<>();
        for (Map<String, Object> relation : relations) {
            Relation parsed = dictToRelation(relation, declaredEntities, kwargs);
            if (parsed != null) {
                parsedRelations.add(parsed);
            }
        }

        Map<String, Entity> uniqueEntities = new LinkedHashMap<>();
        for (Entity entity : declaredEntities) {
            uniqueEntities.put(entity.getUuid(), entity);
        }
        return new ParseAllRelationsResult(List.copyOf(parsedRelations), List.copyOf(uniqueEntities.values()));
    }

    public static List<Entity> declareEntities(List<?> entities,
                                               List<EntityTypeDefinition.EntityDef> entityTypes) {
        return declareEntities(entities, entityTypes, Map.of());
    }

    public static List<Entity> declareEntities(List<?> entities,
                                               List<EntityTypeDefinition.EntityDef> entityTypes,
                                               Map<String, Object> kwargs) {
        int typeIdMax = entityTypes.size() - 1;
        List<Entity> result = new ArrayList<>(entities.size());
        for (Object item : entities) {
            if (item instanceof ExtractionModels.EntityDeclaration declaration) {
                int entityTypeIndex = Math.min(declaration.getEntityTypeId(), typeIdMax);
                EntityTypeDefinition.EntityDef entityType = pythonListGet(entityTypes, entityTypeIndex);
                Entity entity = new Entity();
                entity.setName(declaration.getName());
                entity.setContent("");
                entity.setObjType(entityType.getName());
                applyGraphKwargs(entity, kwargs);
                result.add(entity);
            } else if (item instanceof Entity entity) {
                result.add(entity);
            } else {
                throw new IllegalArgumentException("entities must contain EntityDeclaration or Entity values");
            }
        }
        return result;
    }

    public static ResolvedEntitiesResult resolveEntities(List<ExtractionModels.EntityDeclaration> candidates,
                                                         List<Entity> existing,
                                                         List<Map<String, Object>> duplication) {
        List<Object> result = new ArrayList<>(candidates);
        Map<String, Entity> nameLookup = new LinkedHashMap<>();
        Map<String, Entity> uuidLookup = new LinkedHashMap<>();
        for (Entity entity : existing) {
            nameLookup.put(entity.getName(), entity);
            uuidLookup.put(entity.getUuid(), entity);
        }
        int numExisting = existing.size();
        int numEntities = candidates.size() + numExisting;
        Map<String, LinkedHashSet<String>> mergeMap = new LinkedHashMap<>();
        Map<String, String> isTarget = new LinkedHashMap<>();

        for (Map<String, Object> duplicate : duplication) {
            Object duplicateId = duplicate.getOrDefault("id", "");
            Entity targetEntity = null;
            if (isNumericId(duplicateId)) {
                int index = toInteger(duplicateId) - 1;
                if (index < numExisting) {
                    targetEntity = pythonListGet(existing, index);
                }
            } else {
                targetEntity = nameLookup.get(nullableString(duplicate.get("name")));
            }
            if (targetEntity != null) {
                parseEntityMerging(duplicate, mergeMap, isTarget, result, existing,
                        targetEntity, numEntities, numExisting);
            }
        }

        Map<String, List<Entity>> mergeDict = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : mergeMap.entrySet()) {
            List<Entity> sourceEntities = new ArrayList<>();
            for (String sourceUuid : entry.getValue()) {
                sourceEntities.add(Objects.requireNonNull(uuidLookup.get(sourceUuid), sourceUuid));
            }
            mergeDict.put(entry.getKey(), sourceEntities);
        }
        mergeDict = resolveMergeDict(mergeDict, result, uuidLookup);

        List<MergeArgument> mergeArguments = new ArrayList<>();
        for (Map.Entry<String, List<Entity>> entry : mergeDict.entrySet()) {
            mergeArguments.add(new MergeArgument(Objects.requireNonNull(uuidLookup.get(entry.getKey()), entry.getKey()),
                    List.copyOf(entry.getValue())));
        }
        return new ResolvedEntitiesResult(List.copyOf(result), List.copyOf(mergeArguments), findToRemove(mergeDict));
    }

    public static Set<String> parseRelationMerging(Map<String, Object> response,
                                                   Relation relation,
                                                   List<Map<String, Object>> existingRelations) {
        Set<String> toRemove = new LinkedHashSet<>();
        int numExisting = existingRelations.size();
        Object needMerge = response.get("need_merging");
        String content = nullableString(response.getOrDefault("combined_content", "")).trim();
        Object duplicateIds = response.getOrDefault("duplicate_ids", List.of());

        if (truthy(needMerge) && !content.isEmpty()) {
            relation.setContent(content);
            GraphStoreUtils.TimestampOffset validSince = parseIso(nullableString(
                    response.getOrDefault("valid_since", "")));
            if (validSince.timestamp() >= 0) {
                relation.setValidSince(validSince.timestamp());
                relation.setOffsetSince(validSince.offset());
            }
            GraphStoreUtils.TimestampOffset validUntil = parseIso(nullableString(
                    response.getOrDefault("valid_until", "")));
            if (validUntil.timestamp() >= 0) {
                relation.setValidUntil(validUntil.timestamp());
                relation.setOffsetUntil(validUntil.offset());
            }
            if (duplicateIds instanceof List<?> ids) {
                for (Object id : ids) {
                    if (id instanceof Number number) {
                        int index = number.intValue();
                        if (0 < index && index <= numExisting) {
                            toRemove.add(nullableString(existingRelations.get(index - 1).getOrDefault("uuid", "ERROR")));
                        }
                    }
                }
            }
        }
        return Set.copyOf(toRemove);
    }

    private static Map<String, List<Entity>> resolveMergeDict(Map<String, List<Entity>> mergeDict,
                                                              List<Object> result,
                                                              Map<String, Entity> uuidLookup) {
        Map<String, List<Entity>> sortedMergeDict = new LinkedHashMap<>();
        for (Map.Entry<String, List<Entity>> entry : mergeDict.entrySet()) {
            String targetUuid = entry.getKey();
            List<Entity> sourceEntities = new ArrayList<>(entry.getValue());
            Entity target = Objects.requireNonNull(uuidLookup.get(targetUuid), targetUuid);
            List<Integer> replaceIndexes = new ArrayList<>();
            Map<String, Integer> replaceCount = new LinkedHashMap<>();
            for (Entity source : sourceEntities) {
                replaceCount.put(source.getUuid(), 0);
            }
            for (Entity source : sourceEntities) {
                for (int index = 0; index < result.size(); index++) {
                    if (source == result.get(index)) {
                        replaceIndexes.add(index);
                        replaceCount.put(source.getUuid(), replaceCount.get(source.getUuid()) + 1);
                    }
                }
            }
            if (result.contains(target) || replaceIndexes.isEmpty()) {
                sortedMergeDict.put(targetUuid, List.copyOf(sourceEntities));
                for (Integer index : replaceIndexes) {
                    result.set(index, target);
                }
            } else {
                String newTargetUuid = mostFrequentUuid(replaceCount);
                Entity newTarget = Objects.requireNonNull(uuidLookup.get(newTargetUuid), newTargetUuid);
                sourceEntities.add(target);
                sourceEntities.remove(newTarget);
                sortedMergeDict.put(newTargetUuid, List.copyOf(sourceEntities));
                for (Integer index : replaceIndexes) {
                    result.set(index, newTarget);
                }
            }
        }
        return sortedMergeDict;
    }

    private static void parseEntityMerging(Map<String, Object> duplicate,
                                           Map<String, LinkedHashSet<String>> mergeMap,
                                           Map<String, String> isTarget,
                                           List<Object> result,
                                           List<Entity> existing,
                                           Entity targetEntity,
                                           int numEntities,
                                           int numExisting) {
        Object rawDuplicateIds = duplicate.getOrDefault("duplicate_ids", List.of());
        if (!(rawDuplicateIds instanceof Collection<?> duplicateIds)) {
            return;
        }
        for (Object rawDuplicateId : duplicateIds) {
            if (!isNumericId(rawDuplicateId)) {
                continue;
            }
            int duplicateIndex = toInteger(rawDuplicateId) - 1;
            if (numExisting <= duplicateIndex && duplicateIndex < numEntities) {
                result.set(duplicateIndex - numExisting, targetEntity);
            } else if (0 <= duplicateIndex && duplicateIndex < numExisting) {
                Entity sourceEntity = existing.get(duplicateIndex);
                if (targetEntity.getUuid().equals(sourceEntity.getUuid())) {
                    continue;
                }
                if (mergeMap.containsKey(targetEntity.getUuid())) {
                    isTarget.put(sourceEntity.getUuid(), targetEntity.getUuid());
                    mergeMap.get(targetEntity.getUuid()).add(sourceEntity.getUuid());
                } else if (mergeMap.containsKey(sourceEntity.getUuid())) {
                    isTarget.put(targetEntity.getUuid(), sourceEntity.getUuid());
                    mergeMap.get(sourceEntity.getUuid()).add(targetEntity.getUuid());
                } else {
                    String targetOfTargetUuid = isTarget.getOrDefault(targetEntity.getUuid(), targetEntity.getUuid());
                    isTarget.put(sourceEntity.getUuid(), targetOfTargetUuid);
                    mergeMap.computeIfAbsent(targetOfTargetUuid, ignored -> new LinkedHashSet<>())
                            .add(sourceEntity.getUuid());
                }
            }
        }
    }

    private static Set<String> findToRemove(Map<String, List<Entity>> mergeDict) {
        Set<String> toRemove = new LinkedHashSet<>();
        for (List<Entity> entityList : mergeDict.values()) {
            for (Entity entity : entityList) {
                toRemove.add(entity.getUuid());
            }
        }
        toRemove.removeAll(mergeDict.keySet());
        return Set.copyOf(toRemove);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapSingleMapping(Map<String, Object> response) {
        if (response.size() == 1) {
            Object value = response.values().iterator().next();
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return copy;
            }
        }
        return response;
    }

    private static void applyGraphKwargs(com.openjiuwen.core.foundation.store.graph.BaseGraphObject object,
                                         Map<String, Object> kwargs) {
        if (kwargs == null || kwargs.isEmpty()) {
            return;
        }
        if (kwargs.containsKey("uuid")) {
            object.setUuid(nullableString(kwargs.get("uuid")));
        }
        if (kwargs.containsKey("created_at")) {
            object.setCreatedAt(toLong(kwargs.get("created_at")));
        }
        if (kwargs.containsKey("user_id")) {
            object.setUserId(nullableString(kwargs.get("user_id")));
        }
        if (kwargs.containsKey("language")) {
            object.setLanguage(nullableString(kwargs.get("language")));
        }
        if (kwargs.containsKey("metadata") && kwargs.get("metadata") instanceof Map<?, ?> metadata) {
            object.setMetadata(stringObjectMap(metadata));
        }
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String stringValue(Map<String, Object> map, String key, String fallback) {
        return map.containsKey(key) ? nullableString(map.get(key)) : fallback;
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            return Integer.parseInt(string);
        }
        throw new NumberFormatException("not an integer: " + value);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            return Long.parseLong(string);
        }
        throw new NumberFormatException("not a long: " + value);
    }

    private static boolean isNumericId(Object value) {
        if (value instanceof Number) {
            return true;
        }
        return value instanceof String string && string.chars().allMatch(Character::isDigit) && !string.isEmpty();
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private static <T> T pythonListGet(List<T> values, int index) {
        int resolvedIndex = index < 0 ? values.size() + index : index;
        return values.get(resolvedIndex);
    }

    private static String mostFrequentUuid(Map<String, Integer> counts) {
        String bestUuid = null;
        int bestCount = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestUuid = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return bestUuid;
    }

    public record ParseAllRelationsResult(List<Relation> relations, List<Entity> entities) {
    }

    public record MergeArgument(Entity target, List<Entity> sources) {
    }

    public record ResolvedEntitiesResult(List<Object> entities,
                                         List<MergeArgument> mergingArgs,
                                         Set<String> entityUuidsToRemove) {
    }
}
