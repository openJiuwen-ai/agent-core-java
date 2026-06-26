/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Apply ACE delta operations to the playbook and upsert affected bullets.
 * <p>
 * Mirrors Python's {@code ApplyDeltaOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 * </p>
 */
public class ApplyDeltaOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final int maxBullets;

    public ApplyDeltaOp() {
        this(50);
    }

    public ApplyDeltaOp(int maxBullets) {
        super(Map.of("max_bullets", maxBullets));
        this.maxBullets = maxBullets;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        Playbook.DeltaBatch delta = deltaBatch(context.get("delta"));
        Playbook playbook = ReflectOp.playbook(context.get("playbook", new Playbook()));
        String userId = String.valueOf(context.get("user_id", "default"));

        if (delta == null || delta.getOperations().isEmpty()) {
            LOGGER.info("No delta operations to apply");
            context.set("memories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        Object vectorStoreObject = getVectorStore();
        if (!(vectorStoreObject instanceof MemoryVectorStore vectorStore)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Vector store not configured in ServiceContext"));
        }

        Object embeddingModelObject = getEmbeddingModel();
        if (!(embeddingModelObject instanceof Embedding embeddingModel)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Embedding model not configured in ServiceContext"));
        }

        List<Playbook.DeltaOperation> operations = delta.getOperations();
        long addCount = operations.stream()
                .filter(operation -> "ADD".equals(operation.getType().toUpperCase(Locale.ROOT)))
                .count();
        int currentCount = ((Number) playbook.stats().get("bullets")).intValue();
        int removeCount = Math.max(0, (int) addCount + currentCount - maxBullets);

        Set<String> affectedBulletIds = new LinkedHashSet<>();
        Set<String> removedBulletIds = new LinkedHashSet<>();

        if (removeCount > 0) {
            List<Playbook.Bullet> sortedBullets = new ArrayList<>(playbook.bullets());
            sortedBullets.sort(Comparator
                    .comparingInt((Playbook.Bullet bullet) -> bullet.getHelpful() - bullet.getHarmful())
                    .thenComparing(Playbook.Bullet::getUpdatedAt));
            for (Playbook.Bullet bullet : sortedBullets.subList(0, Math.min(removeCount, sortedBullets.size()))) {
                removedBulletIds.add(bullet.getId());
                playbook.removeBullet(bullet.getId());
                LOGGER.info("Removed low-scoring bullet: %s", bullet.getId());
            }
        }

        for (Playbook.DeltaOperation operation : operations) {
            applyOperation(playbook, operation, affectedBulletIds, removedBulletIds);
        }

        List<ACEMemory> memories = new ArrayList<>();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (String bulletId : removedBulletIds) {
            String nodeId = "ace_" + userId + "_" + bulletId;
            future = future.thenCompose(ignored -> vectorStore.asyncDelete(nodeId)
                    .handle((deleted, error) -> {
                        if (error != null) {
                            LOGGER.warning("Failed to delete bullet %s: %s", bulletId, error);
                        } else if (Boolean.TRUE.equals(deleted)) {
                            LOGGER.debug("Deleted bullet %s from vector store", bulletId);
                        }
                        return null;
                    }));
        }

        for (String bulletId : affectedBulletIds) {
            Playbook.Bullet bullet = playbook.getBullet(bulletId);
            if (bullet == null) {
                continue;
            }
            ACEMemory aceMemory = toAceMemory(bullet, userId);
            VectorNode vectorNode = aceMemory.toVectorNode();
            future = future.thenCompose(ignored -> embeddingModel.embedQuery(aceMemory.getContent())
                    .thenCompose(embedding -> {
                        vectorNode.setEmbedding(embedding);
                        return vectorStore.asyncUpsert(vectorNode);
                    })
                    .thenRun(() -> memories.add(aceMemory)));
        }

        return future.thenRun(() -> {
            context.set("memories", memories);
            LOGGER.info(
                    "Applied %s operations: %s bullets updated, %s bullets removed",
                    operations.size(),
                    affectedBulletIds.size(),
                    removedBulletIds.size()
            );
        });
    }

    private static Playbook.DeltaBatch deltaBatch(Object value) {
        return value instanceof Playbook.DeltaBatch deltaBatch ? deltaBatch : null;
    }

    private static void applyOperation(Playbook playbook,
                                       Playbook.DeltaOperation operation,
                                       Set<String> affectedBulletIds,
                                       Set<String> removedBulletIds) {
        String opType = operation.getType().toUpperCase(Locale.ROOT);
        switch (opType) {
            case "ADD" -> {
                Playbook.Bullet bullet = playbook.addBullet(
                        operation.getSection(),
                        operation.getContent() != null ? operation.getContent() : "",
                        operation.getBulletId(),
                        operation.getMetadata()
                );
                if (bullet != null) {
                    affectedBulletIds.add(bullet.getId());
                }
            }
            case "UPDATE" -> applyUpdate(playbook, operation, affectedBulletIds);
            case "TAG" -> applyTag(playbook, operation, affectedBulletIds);
            case "REMOVE" -> {
                if (operation.getBulletId() != null) {
                    removedBulletIds.add(operation.getBulletId());
                    playbook.removeBullet(operation.getBulletId());
                }
            }
            default -> {
                // Python ignores unknown operation types in the playbook layer.
            }
        }
    }

    private static void applyUpdate(Playbook playbook,
                                    Playbook.DeltaOperation operation,
                                    Set<String> affectedBulletIds) {
        if (operation.getBulletId() == null) {
            return;
        }
        Playbook.Bullet updatedBullet = playbook.updateBullet(
                operation.getBulletId(),
                operation.getContent(),
                operation.getMetadata()
        );
        if (updatedBullet != null) {
            affectedBulletIds.add(operation.getBulletId());
            return;
        }
        if (operation.getContent() != null && !operation.getContent().isEmpty()) {
            LOGGER.info(
                    "UPDATE operation converted to ADD: bullet %s not found, creating new bullet",
                    operation.getBulletId()
            );
            String section = operation.getSection();
            if ((section == null || section.isEmpty()) && operation.getBulletId() != null) {
                String[] parts = operation.getBulletId().split("-(?=[^-]*$)", 2);
                if (parts.length > 1) {
                    section = parts[0].replace('_', ' ');
                }
            }
            Playbook.Bullet bullet = playbook.addBullet(
                    section != null && !section.isEmpty() ? section : "general",
                    operation.getContent(),
                    null,
                    operation.getMetadata()
            );
            if (bullet != null) {
                affectedBulletIds.add(bullet.getId());
            }
        } else {
            LOGGER.warning(
                    "UPDATE operation failed: bullet %s not found and no content provided",
                    operation.getBulletId()
            );
        }
    }

    private static void applyTag(Playbook playbook,
                                 Playbook.DeltaOperation operation,
                                 Set<String> affectedBulletIds) {
        if (operation.getBulletId() == null) {
            return;
        }
        if (playbook.getBullet(operation.getBulletId()) == null) {
            LOGGER.warning("TAG operation failed: bullet %s not found", operation.getBulletId());
            return;
        }
        affectedBulletIds.add(operation.getBulletId());
        for (Map.Entry<String, Integer> entry : operation.getMetadata().entrySet()) {
            playbook.tagBullet(operation.getBulletId(), entry.getKey(), entry.getValue());
        }
    }

    private static ACEMemory toAceMemory(Playbook.Bullet bullet, String userId) {
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
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return Instant.now();
            }
        }
    }
}
