/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Parser for LLM responses into graph entities and relations.
 *
 * <p>Mirrors Python's {@code parse_llm_response.py} module from
 * {@code openjiuwen.core.memory.graph.graph_memory.parse_llm_response}.</p>
 */
public final class ParseLlmResponse {

    private static final Logger LOGGER = Logger.getLogger(ParseLlmResponse.class.getName());

    private static final Pattern MATCH_ISO_DATETIME = Pattern.compile(
            "([0-9]{1,4})-([0-9]{1,2})-([0-9]{1,2})T([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2})(?:Z|\\+([0-9]{1,2}):([0-9]{1,2}))?"
    );

    private ParseLlmResponse() {
    }

    /**
     * Parse ISO 8601 datetime into UNIX timestamp and timezone offset, where
     * the offset is stored in 15-minute units like the Python helper.
     */
    public static int[] parseIso(String timeStr) {
        if (timeStr != null) {
            Matcher matcher = MATCH_ISO_DATETIME.matcher(timeStr);
            if (matcher.find()) {
                int yyyy = Integer.parseInt(matcher.group(1));
                int mm = Integer.parseInt(matcher.group(2));
                int dd = Integer.parseInt(matcher.group(3));
                int h = Integer.parseInt(matcher.group(4));
                int m = Integer.parseInt(matcher.group(5));
                int s = Integer.parseInt(matcher.group(6));

                String isoStr = String.format("%04d-%02d-%02dT%02d:%02d:%02d", yyyy, mm, dd, h, m, s)
                        .replace("24:00:00", "23:59:59");
                String offsetH = matcher.group(7);
                String offsetM = matcher.group(8);
                if (offsetH != null) {
                    isoStr += "+" + String.format("%02d", Integer.parseInt(offsetH));
                    if (offsetM != null) {
                        isoStr += ":" + String.format("%02d", Integer.parseInt(offsetM));
                    }
                } else if (matcher.group(0).endsWith("Z")) {
                    isoStr += "Z";
                }
                return iso2timestamp(isoStr);
            }
        }
        return new int[]{-1, 0};
    }

    private static int[] iso2timestamp(String isoStr) {
        try {
            if (isoStr.endsWith("Z") || isoStr.matches(".*[+-][0-9]{2}:[0-9]{2}$")) {
                OffsetDateTime dateTime = OffsetDateTime.parse(isoStr);
                int offsetQuarters = dateTime.getOffset().getTotalSeconds() / (15 * 60);
                return new int[]{Math.toIntExact(dateTime.toEpochSecond()), offsetQuarters};
            }
            LocalDateTime dateTime = LocalDateTime.parse(isoStr);
            return new int[]{Math.toIntExact(dateTime.toEpochSecond(ZoneOffset.UTC)), 0};
        } catch (DateTimeParseException | ArithmeticException e) {
            LOGGER.warning("Graph Store: invalid iso -> timestamp conversion (" + isoStr + "): " + e.getMessage());
            return new int[]{-1, 0};
        }
    }

    public static Relation dict2relation(Map<String, Object> response, List<Entity> entities) {
        return dict2relation(response, entities, Map.of());
    }

    /**
     * Parse a response dictionary into a relation object. Mirrors Python's
     * wrapper unwrapping, 1-based entity indexing, self-relation EntityFact,
     * timestamp parsing, and invalid-input {@code null} fallback.
     */
    public static Relation dict2relation(Map<String, Object> response, List<Entity> entities,
                                         Map<String, Object> kwargs) {
        if (response == null || entities == null) {
            return null;
        }
        Map<String, Object> normalized = unwrapSingleMap(response);
        try {
            int sourceId = toInt(normalized.get("source_id")) - 1;
            int targetId = toInt(normalized.get("target_id")) - 1;
            if (sourceId < 0 || targetId < 0) {
                return null;
            }
            Entity lhs = entities.get(sourceId);
            Entity rhs = entities.get(targetId);
            if (lhs == null || rhs == null) {
                return null;
            }

            Relation relation = new Relation();
            relation.setRelationType(lhs == rhs ? "EntityFact" : "Relation");
            relation.setName(asString(normalized.getOrDefault("name", "RELATION")));
            relation.setContent(asString(normalized.getOrDefault("fact", relation.getName())));
            int[] validSince = parseIso(asString(normalized.getOrDefault("valid_since", "")));
            int[] validUntil = parseIso(asString(normalized.getOrDefault("valid_until", "")));
            relation.setValidSince(validSince[0]);
            relation.setOffsetSince(validSince[1]);
            relation.setValidUntil(validUntil[0]);
            relation.setOffsetUntil(validUntil[1]);
            relation.setLhs(lhs);
            relation.setRhs(rhs);
            applyCommonKwargs(relation, kwargs);
            return relation;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static List<Entity> declareEntities(List<?> entities,
                                               List<EntityTypeDefinition.EntityDef> entityTypes,
                                               Map<String, Object> kwargs) {
        List<EntityTypeDefinition.EntityDef> effectiveTypes =
                entityTypes == null || entityTypes.isEmpty()
                        ? List.of(new EntityTypeDefinition.EntityDef())
                        : entityTypes;
        List<Entity> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (Object item : entities) {
            if (item instanceof Entity entity) {
                result.add(entity);
            } else if (item instanceof ExtractionModels.EntityDeclaration declaration) {
                int typeId = Math.min(Math.max(declaration.getEntityTypeId(), 0), effectiveTypes.size() - 1);
                Entity entity = new Entity();
                entity.setName(declaration.getName());
                entity.setContent("");
                entity.setEntityType(effectiveTypes.get(typeId).getName());
                applyCommonKwargs(entity, kwargs);
                result.add(entity);
            } else if (item instanceof GraphMemory.EntityDeclaration declaration) {
                int typeId = Math.min(Math.max(declaration.entityTypeId(), 0), effectiveTypes.size() - 1);
                Entity entity = new Entity();
                entity.setName(declaration.name());
                entity.setContent("");
                entity.setEntityType(effectiveTypes.get(typeId).getName());
                applyCommonKwargs(entity, kwargs);
                result.add(entity);
            }
        }
        return result;
    }

    public static List<Entity> declareEntities(List<?> entities, List<EntityTypeDefinition.EntityDef> entityTypes) {
        return declareEntities(entities, entityTypes, Map.of());
    }

    public static ParseAllRelationsResult parseAllRelations(List<Map<String, Object>> relations,
                                                            List<?> entities,
                                                            List<EntityTypeDefinition.EntityDef> entityTypes,
                                                            Map<String, Object> kwargs) {
        List<Entity> declaredEntities = declareEntities(entities, entityTypes, kwargs);
        Set<String> existingContents = new LinkedHashSet<>();
        List<Relation> parsedRelations = new ArrayList<>();

        if (relations != null) {
            for (Map<String, Object> relation : relations) {
                String newContent = asString(relation.getOrDefault("content", "")).trim();
                boolean duplicate = existingContents.stream().anyMatch(old -> old.contains(newContent));
                relation.put("content", duplicate ? "" : newContent);
                existingContents.add(newContent);
                Relation parsed = dict2relation(relation, declaredEntities, kwargs);
                if (parsed != null) {
                    parsedRelations.add(parsed);
                }
            }
        }

        Map<String, Entity> deduped = new LinkedHashMap<>();
        for (Entity entity : declaredEntities) {
            deduped.put(entity.getUuid(), entity);
        }
        return new ParseAllRelationsResult(parsedRelations, new ArrayList<>(deduped.values()));
    }

    public static ParseAllRelationsResult parseAllRelations(List<Map<String, Object>> relations,
                                                            List<?> entities,
                                                            List<EntityTypeDefinition.EntityDef> entityTypes) {
        return parseAllRelations(relations, entities, entityTypes, Map.of());
    }

    /**
     * Resolve and de-duplicate entity declarations against existing entities.
     */
    public static ResolveEntitiesResult resolveEntities(List<?> candidates,
                                                        List<Entity> existing,
                                                        List<Map<String, Object>> duplication) {
        List<Object> result = new ArrayList<>(candidates == null ? List.of() : candidates);
        List<Entity> existingEntities = existing == null ? List.of() : existing;
        Map<String, Entity> nameLookup = new LinkedHashMap<>();
        Map<String, Entity> uuidLookup = new LinkedHashMap<>();
        for (Entity entity : existingEntities) {
            nameLookup.put(entity.getName(), entity);
            uuidLookup.put(entity.getUuid(), entity);
        }

        int numExisting = existingEntities.size();
        int numEntities = result.size() + numExisting;
        Map<String, Set<String>> mergeMap = new LinkedHashMap<>();
        Map<String, String> isTarget = new LinkedHashMap<>();

        for (Map<String, Object> dup : duplication == null ? List.<Map<String, Object>>of() : duplication) {
            Object dupIdObj = dup.getOrDefault("id", "");
            Entity target = null;
            if (isNumeric(dupIdObj)) {
                int dupId = toInt(dupIdObj) - 1;
                if (dupId < numExisting && dupId >= 0) {
                    target = existingEntities.get(dupId);
                }
            } else {
                target = nameLookup.get(asString(dup.get("name")));
            }
            if (target != null) {
                parseEntityMerging(dup, mergeMap, isTarget, result, existingEntities, target, numEntities, numExisting);
            }
        }

        Map<String, List<Entity>> mergeDict = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : mergeMap.entrySet()) {
            List<Entity> sources = new ArrayList<>();
            for (String srcUuid : entry.getValue()) {
                Entity src = uuidLookup.get(srcUuid);
                if (src != null) {
                    sources.add(src);
                }
            }
            mergeDict.put(entry.getKey(), sources);
        }
        mergeDict = resolveMergeDict(mergeDict, result, uuidLookup);

        List<EntityMergeArg> args = new ArrayList<>();
        for (Map.Entry<String, List<Entity>> entry : mergeDict.entrySet()) {
            Entity target = uuidLookup.get(entry.getKey());
            if (target != null) {
                args.add(new EntityMergeArg(target, entry.getValue()));
            }
        }
        return new ResolveEntitiesResult(result, args, findToRemove(mergeDict));
    }

    public static Set<String> parseRelationMerging(Map<String, Object> response, Relation relation,
                                                   List<Map<String, Object>> existingRelations) {
        Set<String> toRemove = new LinkedHashSet<>();
        int numExisting = existingRelations == null ? 0 : existingRelations.size();
        boolean needMerge = asBoolean(response.get("need_merging"));
        String content = asString(response.getOrDefault("combined_content", "")).trim();
        Object dupIds = response.getOrDefault("duplicate_ids", List.of());

        if (needMerge && !content.isEmpty()) {
            relation.setContent(content);
            int[] validSince = parseIso(asString(response.getOrDefault("valid_since", "")));
            if (validSince[0] >= 0) {
                relation.setValidSince(validSince[0]);
                relation.setOffsetSince(validSince[1]);
            }
            int[] validUntil = parseIso(asString(response.getOrDefault("valid_until", "")));
            if (validUntil[0] >= 0) {
                relation.setValidUntil(validUntil[0]);
                relation.setOffsetUntil(validUntil[1]);
            }
            if (dupIds instanceof Iterable<?> iterable) {
                for (Object idObj : iterable) {
                    int i = toInt(idObj);
                    if (i > 0 && i <= numExisting) {
                        toRemove.add(asString(existingRelations.get(i - 1).getOrDefault("uuid", "ERROR")));
                    }
                }
            }
        }
        return toRemove;
    }

    public static Map<String, Object> parseRelationMerging(Map<String, Object> response) {
        return response == null ? new HashMap<>() : new HashMap<>(response);
    }

    public static Map<String, List<Entity>> resolveMergeDict(Map<String, List<Entity>> mergeDict,
                                                             List<Object> result,
                                                             Map<String, Entity> uuidLookup) {
        Map<String, List<Entity>> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, List<Entity>> entry : mergeDict.entrySet()) {
            String targetUuid = entry.getKey();
            List<Entity> sources = new ArrayList<>(entry.getValue());
            Entity target = uuidLookup.get(targetUuid);
            List<Integer> replaceIndexes = new ArrayList<>();
            Map<String, Integer> replaceCount = new LinkedHashMap<>();
            for (Entity source : sources) {
                replaceCount.put(source.getUuid(), 0);
                for (int i = 0; i < result.size(); i++) {
                    if (source == result.get(i)) {
                        replaceIndexes.add(i);
                        replaceCount.put(source.getUuid(), replaceCount.get(source.getUuid()) + 1);
                    }
                }
            }

            if (result.contains(target) || replaceIndexes.isEmpty()) {
                sorted.put(targetUuid, sources);
                for (Integer idx : replaceIndexes) {
                    result.set(idx, target);
                }
            } else {
                String newTargetUuid = replaceCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(targetUuid);
                Entity newTarget = uuidLookup.get(newTargetUuid);
                if (target != null && !sources.contains(target)) {
                    sources.add(target);
                }
                sources.remove(newTarget);
                sorted.put(newTargetUuid, sources);
                for (Integer idx : replaceIndexes) {
                    result.set(idx, newTarget);
                }
            }
        }
        return sorted;
    }

    private static void parseEntityMerging(Map<String, Object> dup,
                                           Map<String, Set<String>> mergeMap,
                                           Map<String, String> isTarget,
                                           List<Object> result,
                                           List<Entity> existing,
                                           Entity target,
                                           int numEntities,
                                           int numExisting) {
        Object duplicateIds = dup.getOrDefault("duplicate_ids", List.of());
        if (!(duplicateIds instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object duplicateIdObj : iterable) {
            if (!isNumeric(duplicateIdObj)) {
                continue;
            }
            int duplicateId = toInt(duplicateIdObj) - 1;
            if (numExisting <= duplicateId && duplicateId < numEntities) {
                result.set(duplicateId - numExisting, target);
            } else if (0 <= duplicateId && duplicateId < numExisting) {
                Entity source = existing.get(duplicateId);
                if (Objects.equals(target.getUuid(), source.getUuid())) {
                    continue;
                }
                if (mergeMap.containsKey(target.getUuid())) {
                    isTarget.put(source.getUuid(), target.getUuid());
                    mergeMap.get(target.getUuid()).add(source.getUuid());
                } else if (mergeMap.containsKey(source.getUuid())) {
                    isTarget.put(target.getUuid(), source.getUuid());
                    mergeMap.get(source.getUuid()).add(target.getUuid());
                } else {
                    String targetOfTarget = isTarget.getOrDefault(target.getUuid(), target.getUuid());
                    isTarget.put(source.getUuid(), targetOfTarget);
                    mergeMap.computeIfAbsent(targetOfTarget, ignored -> new LinkedHashSet<>()).add(source.getUuid());
                }
            }
        }
    }

    private static Set<String> findToRemove(Map<String, List<Entity>> mergeDict) {
        Set<String> toRemove = new LinkedHashSet<>();
        for (List<Entity> entities : mergeDict.values()) {
            for (Entity entity : entities) {
                toRemove.add(entity.getUuid());
            }
        }
        toRemove.removeAll(mergeDict.keySet());
        return toRemove;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapSingleMap(Map<String, Object> response) {
        if (response.size() == 1) {
            Object firstValue = response.values().iterator().next();
            if (firstValue instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        return response;
    }

    private static void applyCommonKwargs(com.openjiuwen.core.foundation.store.graph.BaseGraphObject obj,
                                          Map<String, Object> kwargs) {
        if (kwargs == null) {
            return;
        }
        if (kwargs.containsKey("user_id")) {
            obj.setUserId(asString(kwargs.get("user_id")));
        }
        if (kwargs.get("created_at") instanceof Number number) {
            obj.setCreatedAt(number.longValue());
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(asString(value));
    }

    private static boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        return value instanceof String s && s.matches("-?[0-9]+");
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(asString(value));
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record ParseAllRelationsResult(List<Relation> relations, List<Entity> entities) {
    }

    public record ResolveEntitiesResult(List<Object> resolvedEntities,
                                        List<EntityMergeArg> mergingArgs,
                                        Set<String> entityUuidsToRemove) {
    }

    public record EntityMergeArg(Entity target, List<Entity> sources) {
    }
}
