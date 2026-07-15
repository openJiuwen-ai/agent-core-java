/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Playbook data structures for ACE algorithm.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace.playbook}.
 * 
 * @since 0.1.7
 */
public class Playbook {
    private final Map<String, Bullet> bullets = new LinkedHashMap<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<String>> sections = new LinkedHashMap<>();
    private int nextId = 0;

    /**
     * addBullet.
     * 
     * @param section section
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public Bullet addBullet(String section, String content) {
        return addBullet(section, content, null, null);
    }

    /**
     * addBullet.
     * 
     * @param section section
     * @param content content
     * @param bulletId bulletId
     * @param metadata metadata
     * @return the result
     * @since 0.1.7
     */
    public Bullet addBullet(String section, String content, String bulletId, Map<String, Integer> metadata) {
        String resolvedSection = normalizeSection(section);
        String resolvedBulletId = bulletId != null && !bulletId.isBlank() ? bulletId : generateId(resolvedSection);
        Bullet bullet = new Bullet(resolvedBulletId, resolvedSection, content != null ? content : "");
        bullet.applyMetadata(metadata);
        bullets.put(resolvedBulletId, bullet);
        sections.computeIfAbsent(resolvedSection, ignored -> new ArrayList<>()).add(resolvedBulletId);
        return bullet;
    }

    /**
     * updateBullet.
     * 
     * @param bulletId bulletId
     * @param content content
     * @param metadata metadata
     * @return the result
     * @since 0.1.7
     */
    public Bullet updateBullet(String bulletId, String content, Map<String, Integer> metadata) {
        Bullet bullet = bullets.get(bulletId);
        if (bullet == null) {
            return null;
        }
        if (content != null) {
            bullet.setContent(content);
        }
        if (metadata != null && !metadata.isEmpty()) {
            bullet.applyMetadata(metadata);
        }
        bullet.touch();
        return bullet;
    }

    /**
     * tagBullet.
     * 
     * @param bulletId bulletId
     * @param tag tag
     * @param increment increment
     * @return the result
     * @since 0.1.7
     */
    public Bullet tagBullet(String bulletId, String tag, int increment) {
        Bullet bullet = bullets.get(bulletId);
        if (bullet == null) {
            return null;
        }
        bullet.tag(tag, increment);
        return bullet;
    }

    /**
     * removeBullet.
     * 
     * @param bulletId bulletId
     * @since 0.1.7
     */
    public void removeBullet(String bulletId) {
        Bullet bullet = bullets.remove(bulletId);
        if (bullet == null) {
            return;
        }
        List<String> sectionBulletIds = sections.get(bullet.getSection());
        if (sectionBulletIds != null) {
            sectionBulletIds.removeIf(existingId -> Objects.equals(existingId, bulletId));
            if (sectionBulletIds.isEmpty()) {
                sections.remove(bullet.getSection());
            }
        }
    }

    /**
     * getBullet.
     * 
     * @param bulletId bulletId
     * @return the result
     * @since 0.1.7
     */
    public Bullet getBullet(String bulletId) {
        return bullets.get(bulletId);
    }

    /**
     * bullets.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Bullet> bullets() {
        return new ArrayList<>(bullets.values());
    }

    /**
     * bulletIds.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> bulletIds() {
        return new ArrayList<>(bullets.keySet());
    }

    /**
     * loadBullet.
     * 
     * @param bullet bullet
     * @since 0.1.7
     */
    public void loadBullet(Bullet bullet) {
        if (bullet == null) {
            return;
        }
        bullets.put(bullet.getId(), bullet);
        List<String> ids = sections.computeIfAbsent(bullet.getSection(), ignored -> new ArrayList<>());
        if (!ids.contains(bullet.getId())) {
            ids.add(bullet.getId());
        }
    }

    /**
     * setNextId.
     * 
     * @param nextId nextId
     * @since 0.1.7
     */
    public void setNextId(int nextId) {
        this.nextId = Math.max(0, nextId);
    }

    /**
     * asPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String asPrompt() {
        List<String> lines = new ArrayList<>();
        sections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            lines.add("## " + entry.getKey());
            for (String bulletId : entry.getValue()) {
                Bullet bullet = bullets.get(bulletId);
                if (bullet == null) {
                    continue;
                }
                lines.add("- [" + bullet.getId() + "] " + bullet.getContent() + " (helpful=" + bullet.getHelpful()
                        + ", harmful=" + bullet.getHarmful() + ", neutral=" + bullet.getNeutral() + ")");
            }
        });
        return String.join("\n", lines);
    }

    /**
     * stats.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> stats() {
        Map<String, Integer> tags = new LinkedHashMap<>();
        tags.put("helpful", bullets.values().stream().mapToInt(Bullet::getHelpful).sum());
        tags.put("harmful", bullets.values().stream().mapToInt(Bullet::getHarmful).sum());
        tags.put("neutral", bullets.values().stream().mapToInt(Bullet::getNeutral).sum());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections", sections.size());
        result.put("bullets", bullets.size());
        result.put("tags", tags);
        return result;
    }

    /**
     * makePlaybookExcerpt.
     * 
     * @param bulletIds bulletIds
     * @return the result
     * @since 0.1.7
     */
    public String makePlaybookExcerpt(List<String> bulletIds) {
        List<String> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (bulletIds == null) {
            return "";
        }
        for (String bulletId : bulletIds) {
            if (!seen.add(bulletId)) {
                continue;
            }
            Bullet bullet = getBullet(bulletId);
            if (bullet != null) {
                lines.add("[" + bullet.getId() + "] " + bullet.getContent());
            }
        }
        return String.join("\n", lines);
    }

    /**
     * lowScoreComparator.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Comparator<Bullet> lowScoreComparator() {
        return Comparator.comparingInt((Bullet bullet) -> bullet.getHelpful() - bullet.getHarmful())
                .thenComparing(Bullet::getUpdatedAt).thenComparing(Bullet::getId);
    }

    /**
     * generateId.
     * 
     * @param section section
     * @return the result
     * @since 0.1.7
     */
    private String generateId(String section) {
        this.nextId += 1;
        String normalizedSection = normalizeSection(section);
        String prefix = normalizedSection.split("\\s+")[0].toLowerCase(Locale.ROOT);
        return prefix + "-" + String.format(Locale.ROOT, "%05d", nextId);
    }

    /**
     * normalizeSection.
     * 
     * @param section section
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeSection(String section) {
        return section != null && !section.isBlank() ? section : "general";
    }

    /**
     * Single playbook entry.
     * 
     * @since 0.1.7
     */
    public static class Bullet {
        private final String id;
        private final String section;
        private String content;
        private int helpful;
        private int harmful;
        private int neutral;
        private final String createdAt;
        private String updatedAt;

        /**
         * Bullet.
         * 
         * @param id id
         * @param section section
         * @param content content
         * @since 0.1.7
         */
        public Bullet(String id, String section, String content) {
            this(id, normalizeSection(section), content != null ? content : "", 0, 0, 0, Instant.now().toString(),
                    Instant.now().toString());
        }

        /**
         * Bullet.
         * 
         * @param id id
         * @param section section
         * @param content content
         * @param helpful helpful
         * @param harmful harmful
         * @param neutral neutral
         * @param createdAt createdAt
         * @param updatedAt updatedAt
         * @since 0.1.7
         */
        public Bullet(String id, String section, String content, int helpful, int harmful, int neutral,
                String createdAt, String updatedAt) {
            this.id = id;
            this.section = normalizeSection(section);
            this.content = content != null ? content : "";
            this.helpful = helpful;
            this.harmful = harmful;
            this.neutral = neutral;
            this.createdAt = createdAt != null && !createdAt.isBlank() ? createdAt : Instant.now().toString();
            this.updatedAt = updatedAt != null && !updatedAt.isBlank() ? updatedAt : this.createdAt;
        }

        /**
         * applyMetadata.
         * 
         * @param metadata metadata
         * @since 0.1.7
         */
        public void applyMetadata(Map<String, Integer> metadata) {
            if (metadata == null || metadata.isEmpty()) {
                return;
            }
            for (Map.Entry<String, Integer> entry : metadata.entrySet()) {
                String key = entry.getKey();
                int value = entry.getValue() != null ? entry.getValue() : 0;
                switch (key) {
                    case "helpful" -> helpful = value;
                    case "harmful" -> harmful = value;
                    case "neutral" -> neutral = value;
                    default -> {
                        // Ignore unsupported metadata to match the Python loader behavior.
                    }
                }
            }
        }

        /**
         * tag.
         * 
         * @param tag tag
         * @param increment increment
         * @since 0.1.7
         */
        public void tag(String tag, int increment) {
            switch (tag) {
                case "helpful" -> helpful += increment;
                case "harmful" -> harmful += increment;
                case "neutral" -> neutral += increment;
                default -> throw new IllegalArgumentException("Unsupported tag: " + tag);
            }
            touch();
        }

        /**
         * touch.
         * 
         * @since 0.1.7
         */
        public void touch() {
            updatedAt = Instant.now().toString();
        }

        /**
         * getId.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getId() {
            return id;
        }

        /**
         * getSection.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getSection() {
            return section;
        }

        /**
         * getContent.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getContent() {
            return content;
        }

        /**
         * setContent.
         * 
         * @param content content
         * @since 0.1.7
         */
        public void setContent(String content) {
            this.content = content != null ? content : "";
        }

        /**
         * getHelpful.
         * 
         * @return the result
         * @since 0.1.7
         */
        public int getHelpful() {
            return helpful;
        }

        /**
         * getHarmful.
         * 
         * @return the result
         * @since 0.1.7
         */
        public int getHarmful() {
            return harmful;
        }

        /**
         * getNeutral.
         * 
         * @return the result
         * @since 0.1.7
         */
        public int getNeutral() {
            return neutral;
        }

        /**
         * getCreatedAt.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getCreatedAt() {
            return createdAt;
        }

        /**
         * getUpdatedAt.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getUpdatedAt() {
            return updatedAt;
        }
    }

    /**
     * Delta operation for playbook updates.
     * 
     * @since 0.1.7
     */
    public static class DeltaOperation {
        private final String type;
        private final String section;
        private final String content;
        private final String bulletId;
        private final Map<String, Integer> metadata;

        /**
         * DeltaOperation.
         * 
         * @param type type
         * @param section section
         * @param content content
         * @param bulletId bulletId
         * @param metadata metadata
         * @since 0.1.7
         */
        public DeltaOperation(String type, String section, String content, String bulletId,
                Map<String, Integer> metadata) {
            this.type = type != null ? type : "ADD";
            this.section = section != null ? section : "";
            this.content = content;
            this.bulletId = bulletId;
            this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        }

        /**
         * fromJson.
         * 
         * @param payload payload
         * @return the result
         * @since 0.1.7
         */
        @SuppressWarnings("unchecked")
        public static DeltaOperation fromJson(Map<String, Object> payload) {
            Map<String, Integer> metadata = new LinkedHashMap<>();
            Object metadataValue = payload.get("metadata");
            if (metadataValue instanceof Map<?, ?> rawMetadata) {
                for (Map.Entry<?, ?> entry : rawMetadata.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if (value instanceof Number number) {
                        metadata.put(key, number.intValue());
                    } else if (value != null) {
                        try {
                            metadata.put(key, Integer.parseInt(String.valueOf(value)));
                        } catch (NumberFormatException ignored) {
                            // Skip invalid metadata values.
                        }
                    } else {
                        // no-op
                    }
                }
            }

            return new DeltaOperation(String.valueOf(payload.getOrDefault("type", "ADD")),
                    String.valueOf(payload.getOrDefault("section", "")),
                    payload.get("content") != null ? String.valueOf(payload.get("content")) : null,
                    payload.get("bullet_id") != null ? String.valueOf(payload.get("bullet_id")) : null, metadata);
        }

        /**
         * toJson.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> toJson() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", type);
            data.put("section", section);
            if (content != null) {
                data.put("content", content);
            }
            if (bulletId != null) {
                data.put("bullet_id", bulletId);
            }
            if (!metadata.isEmpty()) {
                data.put("metadata", new LinkedHashMap<>(metadata));
            }
            return data;
        }

        /**
         * getType.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getType() {
            return type;
        }

        /**
         * getSection.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getSection() {
            return section;
        }

        /**
         * getContent.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getContent() {
            return content;
        }

        /**
         * getBulletId.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getBulletId() {
            return bulletId;
        }

        /**
         * getMetadata.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Integer> getMetadata() {
            return new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Bundle of curator reasoning and operations.
     * 
     * @since 0.1.7
     */
    public static class DeltaBatch {
        private final String reasoning;
        private final List<DeltaOperation> operations;

        /**
         * DeltaBatch.
         * 
         * @param reasoning reasoning
         * @param operations operations
         * @since 0.1.7
         */
        public DeltaBatch(String reasoning, List<DeltaOperation> operations) {
            this.reasoning = reasoning != null ? reasoning : "";
            this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
        }

        /**
         * fromJson.
         * 
         * @param payload payload
         * @return the result
         * @since 0.1.7
         */
        public static DeltaBatch fromJson(Map<String, Object> payload) {
            List<DeltaOperation> operations = new ArrayList<>();
            Object operationsValue = payload.get("operations");
            if (operationsValue instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?> rawMap) {
                        Map<String, Object> operationPayload = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                            operationPayload.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        operations.add(DeltaOperation.fromJson(operationPayload));
                    }
                }
            }
            return new DeltaBatch(String.valueOf(payload.getOrDefault("reasoning", "")), operations);
        }

        /**
         * toJson.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> toJson() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reasoning", reasoning);
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (DeltaOperation operation : operations) {
                serialized.add(operation.toJson());
            }
            data.put("operations", serialized);
            return data;
        }

        /**
         * getReasoning.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getReasoning() {
            return reasoning;
        }

        /**
         * getOperations.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<DeltaOperation> getOperations() {
            return new ArrayList<>(operations);
        }
    }
}
