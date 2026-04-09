/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace.update.ApplyDeltaOp}.
 *
 * <p>Applies ACE playbook delta operations, enforces the maximum playbook size, deletes removed
 * bullets from the vector store, and emits only the affected ACE memories for persistence.
 */
public class ApplyDeltaOp extends BaseOp {

    private final int maxBullets;

    public ApplyDeltaOp() {
        this(50);
    }

    public ApplyDeltaOp(int maxBullets) {
        this.maxBullets = maxBullets;
    }

    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        Object deltaValue = context.get("delta");
        Playbook.DeltaBatch delta = switch (deltaValue) {
            case Playbook.DeltaBatch batch -> batch;
            case java.util.Map<?, ?> rawMap -> Playbook.DeltaBatch.fromJson(toStringKeyMap(rawMap));
            default -> null;
        };
        Playbook playbook = context.get("playbook") instanceof Playbook existing ? existing : new Playbook();
        String userId = context.getString("user_id", "default");

        if (delta == null || delta.getOperations().isEmpty()) {
            context.set("memories", List.of());
            context.set("playbook", playbook);
            return CompletableFuture.completedFuture(null);
        }

        int addCount = 0;
        for (Playbook.DeltaOperation operation : delta.getOperations()) {
            if ("ADD".equalsIgnoreCase(operation.getType())) {
                addCount++;
            }
        }

        int currentCount = playbook.bullets().size();
        int removeCount = Math.max(0, addCount + currentCount - maxBullets);

        Set<String> affectedBulletIds = new LinkedHashSet<>();
        Set<String> removedBulletIds = new LinkedHashSet<>();

        if (removeCount > 0) {
            List<Playbook.Bullet> sortedBullets = new ArrayList<>(playbook.bullets());
            sortedBullets.sort(Playbook.lowScoreComparator());
            for (int index = 0; index < Math.min(removeCount, sortedBullets.size()); index++) {
                Playbook.Bullet bullet = sortedBullets.get(index);
                removedBulletIds.add(bullet.getId());
                playbook.removeBullet(bullet.getId());
            }
        }

        for (Playbook.DeltaOperation operation : delta.getOperations()) {
            String operationType = operation.getType() != null
                ? operation.getType().toUpperCase(Locale.ROOT)
                : "ADD";

            switch (operationType) {
                case "ADD" -> {
                    Playbook.Bullet bullet = playbook.addBullet(
                        operation.getSection(),
                        operation.getContent() != null ? operation.getContent() : "",
                        operation.getBulletId(),
                        operation.getMetadata()
                    );
                    affectedBulletIds.add(bullet.getId());
                }
                case "UPDATE" -> {
                    String bulletId = operation.getBulletId();
                    if (bulletId == null || bulletId.isBlank()) {
                        continue;
                    }
                    Playbook.Bullet updatedBullet = playbook.updateBullet(
                        bulletId,
                        operation.getContent(),
                        operation.getMetadata()
                    );
                    if (updatedBullet != null) {
                        affectedBulletIds.add(bulletId);
                    } else if (operation.getContent() != null && !operation.getContent().isBlank()) {
                        String section = operation.getSection();
                        if ((section == null || section.isBlank()) && bulletId.contains("-")) {
                            section = bulletId.substring(0, bulletId.lastIndexOf('-')).replace('_', ' ');
                        }
                        Playbook.Bullet bullet = playbook.addBullet(
                            section != null && !section.isBlank() ? section : "general",
                            operation.getContent(),
                            null,
                            operation.getMetadata()
                        );
                        affectedBulletIds.add(bullet.getId());
                    }
                }
                case "TAG" -> {
                    String bulletId = operation.getBulletId();
                    if (bulletId == null || bulletId.isBlank() || playbook.getBullet(bulletId) == null) {
                        continue;
                    }
                    for (var entry : operation.getMetadata().entrySet()) {
                        playbook.tagBullet(bulletId, entry.getKey(), entry.getValue());
                    }
                    affectedBulletIds.add(bulletId);
                }
                case "REMOVE" -> {
                    String bulletId = operation.getBulletId();
                    if (bulletId == null || bulletId.isBlank()) {
                        continue;
                    }
                    removedBulletIds.add(bulletId);
                    playbook.removeBullet(bulletId);
                    affectedBulletIds.remove(bulletId);
                }
                default -> {
                    // Ignore unknown operations.
                }
            }
        }

        List<CompletableFuture<Boolean>> deletions = new ArrayList<>();
        Object vectorStoreService = getVectorStore();
        if (vectorStoreService instanceof MemoryVectorStore vectorStore) {
            for (String bulletId : removedBulletIds) {
                deletions.add(vectorStore.asyncDelete("ace_" + userId + "_" + bulletId));
            }
        }

        CompletableFuture<?>[] deletionArray = deletions.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(deletionArray).thenRun(() -> {
            List<ACEMemory> memories = new ArrayList<>();
            for (String bulletId : affectedBulletIds) {
                Playbook.Bullet bullet = playbook.getBullet(bulletId);
                if (bullet != null) {
                    memories.add(toAceMemory(userId, bullet));
                }
            }
            context.set("memories", memories);
            context.set("playbook", playbook);
        });
    }

    private static ACEMemory toAceMemory(String userId, Playbook.Bullet bullet) {
        ACEMemory memory = new ACEMemory(bullet.getId(), bullet.getSection(), bullet.getContent());
        memory.setWorkspaceId(userId);
        memory.setHelpful(bullet.getHelpful());
        memory.setHarmful(bullet.getHarmful());
        memory.setNeutral(bullet.getNeutral());
        memory.setCreatedAt(parseInstant(bullet.getCreatedAt()));
        memory.setUpdatedAt(parseInstant(bullet.getUpdatedAt()));
        return memory;
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private static java.util.Map<String, Object> toStringKeyMap(java.util.Map<?, ?> rawMap) {
        java.util.Map<String, Object> converted = new java.util.LinkedHashMap<>();
        for (var entry : rawMap.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }
}
