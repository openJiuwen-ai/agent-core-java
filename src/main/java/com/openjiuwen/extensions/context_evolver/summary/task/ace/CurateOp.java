/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builds ACE playbook delta operations from the reflection payload.
 * 
 * @since 0.1.7
 */
public class CurateOp extends BaseOp {
    /**
     * asyncExecute.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = context.getString("matts", "none");
        if (!"none".equals(matts) && !"sequential".equals(matts)) {
            context.set("delta", new Playbook.DeltaBatch("", List.of()));
            return CompletableFuture.completedFuture(null);
        }

        Playbook playbook = context.get("playbook") instanceof Playbook existing ? existing : new Playbook();
        Map<String, Object> reflection = context.getMap("reflection");
        if (reflection == null || reflection.isEmpty()) {
            context.set("delta", new Playbook.DeltaBatch("", List.of()));
            return CompletableFuture.completedFuture(null);
        }

        List<Playbook.DeltaOperation> operations = new ArrayList<>();
        Object candidatesValue = reflection.get("candidate_insights");
        if (candidatesValue instanceof Iterable<?> iterable) {
            for (Object rawCandidate : iterable) {
                if (!(rawCandidate instanceof Map<?, ?> rawMap)) {
                    continue;
                }

                Map<String, Object> candidate = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    candidate.put(String.valueOf(entry.getKey()), entry.getValue());
                }

                String content = AceUtils.compactWhitespace(SchemaUtils.stringValue(candidate.get("content"), ""));
                if (content.isBlank()) {
                    continue;
                }

                String section = SchemaUtils.stringValue(candidate.get("section"),
                        AceUtils.guessSection(context.getString("query", ""), content));
                String tag = normalizeTag(SchemaUtils.stringValue(candidate.get("tag"), "helpful"));
                Playbook.Bullet existingBullet = findMatchingBullet(playbook, content);

                if (existingBullet != null) {
                    Map<String, Integer> metadata = new LinkedHashMap<>();
                    metadata.put(tag, 1);
                    operations.add(new Playbook.DeltaOperation("TAG", section, null, existingBullet.getId(), metadata));
                } else {
                    Map<String, Integer> metadata = new LinkedHashMap<>();
                    Object metadataValue = candidate.get("metadata");
                    if (metadataValue instanceof Map<?, ?> rawMetadata) {
                        for (Map.Entry<?, ?> entry : rawMetadata.entrySet()) {
                            String key = String.valueOf(entry.getKey());
                            Object value = entry.getValue();
                            if (value instanceof Number number) {
                                metadata.put(key, number.intValue());
                            }
                        }
                    }
                    operations.add(new Playbook.DeltaOperation("ADD", section, content, null, metadata));
                }
            }
        }

        String reasoning = SchemaUtils.stringValue(reflection.get("reasoning"), "");
        context.set("delta", new Playbook.DeltaBatch(reasoning, operations));
        return CompletableFuture.completedFuture(null);
    }

    /**
     * findMatchingBullet.
     * 
     * @param playbook playbook
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static Playbook.Bullet findMatchingBullet(Playbook playbook, String content) {
        String normalizedContent = AceUtils.normalizeForMatch(content);
        for (Playbook.Bullet bullet : playbook.bullets()) {
            if (AceUtils.normalizeForMatch(bullet.getContent()).equals(normalizedContent)) {
                return bullet;
            }
        }
        return null;
    }

    /**
     * normalizeTag.
     * 
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeTag(String tag) {
        String upper = tag != null ? tag.toLowerCase(Locale.ROOT) : "helpful";
        return switch (upper) {
            case "harmful", "neutral" -> upper;
            default -> "helpful";
        };
    }
}
