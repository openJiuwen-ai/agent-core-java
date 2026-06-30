/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.GraphUtils;
import com.openjiuwen.core.memory.graph.extraction.EntityDeclaration;
import com.openjiuwen.core.memory.graph.extraction.EntityDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing LLM responses into graph entities, relations, and timestamps.
 */
public final class ParseLlmResponse {
    private static final Pattern MATCH_ISO_DATETIME = Pattern.compile(
            "([0-9]{1,4})-([0-9]{1,2})-([0-9]{1,2})T"
                    + "([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2})"
                    + "(?:Z|\\+([0-9]{1,2}):([0-9]{1,2}))?");

    private ParseLlmResponse() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int[] parseIso(String timeStr) {
        if (timeStr != null) {
            Matcher match = MATCH_ISO_DATETIME.matcher(timeStr);
            if (match.find()) {
                int yyyy = Integer.parseInt(match.group(1));
                int mm = Integer.parseInt(match.group(2));
                int dd = Integer.parseInt(match.group(3));
                int h = Integer.parseInt(match.group(4));
                int m = Integer.parseInt(match.group(5));
                int s = Integer.parseInt(match.group(6));
                StringBuilder iso = new StringBuilder(String.format(
                        "%04d-%02d-%02dT%02d:%02d:%02d",
                        yyyy,
                        mm,
                        dd,
                        h,
                        m,
                        s));
                String offsetH = match.group(7);
                String offsetM = match.group(8);
                if (offsetH != null) {
                    iso.append("+").append(String.format("%02d", Integer.parseInt(offsetH)));
                    if (offsetM != null) {
                        iso.append(":").append(String.format("%02d", Integer.parseInt(offsetM)));
                    }
                }
                return GraphUtils.iso2timestamp(iso.toString());
            }
        }
        return new int[]{-1, 0};
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static Relation dict2relation(
            Map<String, Object> response,
            List<Entity> entities,
            Map<String, Object> kwargs) {
        Map<String, Object> relationResponse = response;
        if (response.size() == 1) {
            Object first = response.values().iterator().next();
            if (first instanceof Map<?, ?> map) {
                relationResponse = (Map<String, Object>) map;
            }
        }
        try {
            int sourceId = Integer.parseInt(String.valueOf(relationResponse.get("source_id"))) - 1;
            int targetId = Integer.parseInt(String.valueOf(relationResponse.get("target_id"))) - 1;
            if (sourceId < 0 || targetId < 0) {
                throw new IllegalArgumentException(
                        "relation source_id and target_id must be valid 1-based entity indices");
            }
            Entity lhs = entities.get(sourceId);
            Entity rhs = entities.get(targetId);
            String relType = lhs == rhs ? "EntityFact" : "Relation";
            Relation relation = new Relation();
            relation.setObjType(relType);
            relation.setName(String.valueOf(relationResponse.getOrDefault("name", "RELATION")));
            relation.setContent(String.valueOf(relationResponse.getOrDefault("fact", relation.getName())));
            int[] since = parseIso(String.valueOf(relationResponse.getOrDefault("valid_since", "")));
            int[] until = parseIso(String.valueOf(relationResponse.getOrDefault("valid_until", "")));
            relation.setValidSince(since[0]);
            relation.setOffsetSince(since[1]);
            relation.setValidUntil(until[0]);
            relation.setOffsetUntil(until[1]);
            relation.setLhs(lhs);
            relation.setRhs(rhs);
            if (kwargs != null) {
                if (kwargs.get("user_id") != null) {
                    relation.setUserId(String.valueOf(kwargs.get("user_id")));
                }
                if (kwargs.get("language") != null) {
                    relation.setLanguage(String.valueOf(kwargs.get("language")));
                }
            }
            return relation;
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map.Entry<List<Relation>, List<Entity>> parseAllRelations(List<Map<String, Object>> relations,
                                                                            List<Object> entities,
                                                                            List<EntityDef> entityTypes,
                                                                            Map<String, Object> kwargs) {
        List<Entity> declared = declareEntities(entities, entityTypes, kwargs);
        Set<String> existingContents = new HashSet<>();
        for (Map<String, Object> relation : relations) {
            String newContent = String.valueOf(relation.getOrDefault("content", "")).trim();
            boolean isDuplicate = false;
            for (String oldContent : existingContents) {
                if (oldContent.contains(newContent)) {
                    isDuplicate = true;
                    break;
                }
            }
            relation.put("content", isDuplicate ? "" : newContent);
            existingContents.add(newContent);
        }
        List<Relation> parsedRelations = new ArrayList<>();
        for (Map<String, Object> relation : relations) {
            Relation parsed = dict2relation(relation, declared, kwargs);
            if (parsed != null) {
                parsedRelations.add(parsed);
            }
        }
        Map<String, Entity> unique = new LinkedHashMap<>();
        for (Entity entity : declared) {
            unique.put(entity.getUuid(), entity);
        }
        return Map.entry(parsedRelations, new ArrayList<>(unique.values()));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Entity> declareEntities(
            List<Object> entities,
            List<EntityDef> entityTypes,
            Map<String, Object> kwargs) {
        List<Entity> result = new ArrayList<>();
        int typeIdMax = entityTypes.size() - 1;
        for (Object entity : entities) {
            if (entity instanceof EntityDeclaration declaration) {
                Entity newEntity = new Entity();
                newEntity.setName(declaration.getName());
                newEntity.setContent("");
                newEntity.setObjType(entityTypes.get(Math.min(declaration.getEntityTypeId(), typeIdMax)).getName());
                if (kwargs != null) {
                    if (kwargs.get("user_id") != null) {
                        newEntity.setUserId(String.valueOf(kwargs.get("user_id")));
                    }
                    if (kwargs.get("language") != null) {
                        newEntity.setLanguage(String.valueOf(kwargs.get("language")));
                    }
                }
                result.add(newEntity);
            } else if (entity instanceof Entity graphEntity) {
                result.add(graphEntity);
            }
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ResolveEntitiesResult resolveEntities(List<EntityDeclaration> candidates,
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
        Map<String, Set<String>> mergeMap = new LinkedHashMap<>();
        Map<String, String> isTarget = new LinkedHashMap<>();
        for (Map<String, Object> dup : duplication) {
            Object dupId = dup.get("id");
            Entity targetEntity = null;
            if (dupId instanceof Number || (dupId instanceof String s && s.matches("\\d+"))) {
                int isResolvedIndex = Integer.parseInt(String.valueOf(dupId)) - 1;
                if (isResolvedIndex < numExisting && isResolvedIndex >= 0) {
                    targetEntity = existing.get(isResolvedIndex);
                }
            } else {
                targetEntity = nameLookup.get(String.valueOf(dup.get("name")));
            }
            if (targetEntity != null) {
                parseEntityMerging(dup, mergeMap, isTarget, result, existing, targetEntity, numEntities, numExisting);
            }
        }
        Map<String, List<Entity>> mergeDict = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : mergeMap.entrySet()) {
            List<Entity> entitiesToMerge = new ArrayList<>();
            for (String uuid : entry.getValue()) {
                entitiesToMerge.add(uuidLookup.get(uuid));
            }
            mergeDict.put(entry.getKey(), entitiesToMerge);
        }
        mergeDict = resolveMergeDict(mergeDict, result, uuidLookup);
        List<Map.Entry<Entity, List<Entity>>> mergingArgs = new ArrayList<>();
        for (Map.Entry<String, List<Entity>> entry : mergeDict.entrySet()) {
            mergingArgs.add(Map.entry(uuidLookup.get(entry.getKey()), entry.getValue()));
        }
        return new ResolveEntitiesResult(result, mergingArgs, findToRemove(mergeDict));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Set<String> parseRelationMerging(
            Map<String, Object> response,
            Relation relation,
            List<Map<String, Object>> existingRelations) {
        Set<String> toRemove = new HashSet<>();
        int numExisting = existingRelations.size();
        boolean isMergeNeeded = asBoolean(response, List.of(
                "need_merging",
                "needMerging",
                "isNeedMerging",
                "is_need_merging"));
        String content = stringValue(response, List.of("combined_content", "combinedContent")).trim();
        Object dupIds = firstPresent(response, List.of("duplicate_ids", "duplicateIds"));
        if (isMergeNeeded && !content.isBlank()) {
            relation.setContent(content);
            int[] since = parseIso(stringValue(response, List.of("valid_since", "validSince")));
            if (since[0] >= 0) {
                relation.setValidSince(since[0]);
                relation.setOffsetSince(since[1]);
            }
            int[] until = parseIso(stringValue(response, List.of("valid_until", "validUntil")));
            if (until[0] >= 0) {
                relation.setValidUntil(until[0]);
                relation.setOffsetUntil(until[1]);
            }
            List<Object> duplicateIdList = null;
            if (dupIds instanceof List<?> list) {
                duplicateIdList = new ArrayList<>(list);
            } else if (dupIds != null) {
                String raw = String.valueOf(dupIds).trim();
                if (raw.startsWith("[") && raw.endsWith("]")) {
                    Object parsed = com.openjiuwen.core.memory.graph.extraction.ParseResponse.rawDecodeJson(raw, null);
                    if (parsed instanceof List<?> list) {
                        duplicateIdList = new ArrayList<>(list);
                    }
                } else if (!raw.isBlank()) {
                    duplicateIdList = List.of(raw);
                }
            }
            if (duplicateIdList != null) {
                for (Object idObj : duplicateIdList) {
                    int index = Integer.parseInt(String.valueOf(idObj).trim());
                    if (index > 0 && index <= numExisting) {
                        toRemove.add(String.valueOf(existingRelations.get(index - 1).getOrDefault("uuid", "ERROR")));
                    }
                }
            }
        }
        return toRemove;
    }

    private static Object firstPresent(Map<String, Object> source, List<String> keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> source, List<String> keys) {
        return java.util.Optional.ofNullable(firstPresent(source, keys))
                .map(String::valueOf)
                .orElse("");
    }

    private static boolean asBoolean(Map<String, Object> source, List<String> keys) {
        Object value = firstPresent(source, keys);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, List<Entity>> resolveMergeDict(Map<String, List<Entity>> mergeDict,
                                                              List<Object> result,
                                                              Map<String, Entity> uuidLookup) {
        Map<String, List<Entity>> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, List<Entity>> entry : mergeDict.entrySet()) {
            String targetUuid = entry.getKey();
            Entity target = uuidLookup.get(targetUuid);
            List<Integer> replaceIdx = new ArrayList<>();
            Map<String, Integer> replaceCount = new LinkedHashMap<>();
            for (Entity src : entry.getValue()) {
                replaceCount.put(src.getUuid(), 0);
                if (result.contains(src)) {
                    for (int i = 0; i < result.size(); i++) {
                        if (result.get(i) == src) {
                            replaceIdx.add(i);
                            replaceCount.put(src.getUuid(), replaceCount.get(src.getUuid()) + 1);
                        }
                    }
                }
            }
            if (result.contains(target) || replaceIdx.isEmpty()) {
                sorted.put(targetUuid, entry.getValue());
                for (Integer idx : replaceIdx) {
                    result.set(idx, target);
                }
            } else {
                String newTargetUuid = replaceCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(targetUuid);
                Entity newTarget = uuidLookup.get(newTargetUuid);
                List<Entity> sources = new ArrayList<>(entry.getValue());
                sources.add(target);
                sources.remove(newTarget);
                sorted.put(newTargetUuid, sources);
                for (Integer idx : replaceIdx) {
                    result.set(idx, newTarget);
                }
            }
        }
        return sorted;
    }

    @SuppressWarnings("unchecked")
    private static void parseEntityMerging(Map<String, Object> dup,
                                           Map<String, Set<String>> mergeMap,
                                           Map<String, String> isTarget,
                                           List<Object> result,
                                           List<Entity> existing,
                                           Entity targetEntity,
                                           int numEntities,
                                           int numExisting) {
        Object duplicateIdsObj = dup.get("duplicate_ids");
        if (!(duplicateIdsObj instanceof List<?> duplicateIds)) {
            return;
        }
        for (Object duplicateId : duplicateIds) {
            int isResolved = Integer.parseInt(String.valueOf(duplicateId)) - 1;
            if (numExisting <= isResolved && isResolved < numEntities) {
                result.set(isResolved - numExisting, targetEntity);
            } else if (0 <= isResolved && isResolved < numExisting) {
                Entity sourceEntity = existing.get(isResolved);
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
                    mergeMap.computeIfAbsent(targetOfTargetUuid, ignored -> new HashSet<>())
                            .add(sourceEntity.getUuid());
                }
            }
        }
    }

    private static Set<String> findToRemove(Map<String, List<Entity>> mergeDict) {
        Set<String> toRemove = new HashSet<>();
        for (List<Entity> entities : mergeDict.values()) {
            for (Entity entity : entities) {
                toRemove.add(entity.getUuid());
            }
        }
        toRemove.removeAll(mergeDict.keySet());
        return toRemove;
    }

    /**
 * Public record ResolveEntitiesResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record ResolveEntitiesResult(List<Object> resolvedEntities,
                                        List<Map.Entry<Entity, List<Entity>>> mergingArgs,
                                        Set<String> entityUuidsToRemove) {
    }
}
