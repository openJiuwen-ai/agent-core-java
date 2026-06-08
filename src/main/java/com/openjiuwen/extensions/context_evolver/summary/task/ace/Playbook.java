/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Mirrors Python's {@code Playbook} module in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/playbook.py}.
 */
public class Playbook {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Map<String, Bullet> bullets = new LinkedHashMap<>();
    private Map<String, List<String>> sections = new LinkedHashMap<>();
    private int nextId = 0;

    public Bullet addBullet(String section, String content) {
        return addBullet(section, content, null, null);
    }

    public Bullet addBullet(String section, String content, String bulletId, Map<String, Integer> metadata) {
        String resolvedId = bulletId != null && !bulletId.isEmpty() ? bulletId : generateId(section);
        Map<String, Integer> resolvedMetadata = metadata != null ? metadata : Map.of();
        Bullet bullet = new Bullet(resolvedId, section, content);
        bullet.applyMetadata(resolvedMetadata);
        bullets.put(resolvedId, bullet);
        sections.computeIfAbsent(bullet.getSection(), ignored -> new ArrayList<>()).add(resolvedId);
        return bullet;
    }

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
        bullet.setUpdatedAt(utcNow());
        return bullet;
    }

    public Bullet tagBullet(String bulletId, String tag, int increment) {
        Bullet bullet = bullets.get(bulletId);
        if (bullet == null) {
            return null;
        }
        bullet.tag(tag, increment);
        return bullet;
    }

    public void removeBullet(String bulletId) {
        Bullet bullet = bullets.remove(bulletId);
        if (bullet == null) {
            return;
        }
        List<String> sectionList = sections.get(bullet.getSection());
        if (sectionList != null) {
            List<String> filtered = new ArrayList<>();
            for (String existingId : sectionList) {
                if (!Objects.equals(existingId, bulletId)) {
                    filtered.add(existingId);
                }
            }
            if (filtered.isEmpty()) {
                sections.remove(bullet.getSection());
            } else {
                sections.put(bullet.getSection(), filtered);
            }
        }
    }

    public Bullet getBullet(String bulletId) {
        return bullets.get(bulletId);
    }

    public List<Bullet> bullets() {
        return new ArrayList<>(bullets.values());
    }

    public List<String> bulletIds() {
        return new ArrayList<>(bullets.keySet());
    }

    public void loadBullet(Bullet bullet) {
        bullets.put(bullet.getId(), bullet);
        sections.computeIfAbsent(bullet.getSection(), ignored -> new ArrayList<>()).add(bullet.getId());
    }

    public void setNextId(int nextId) {
        this.nextId = nextId;
    }

    public Map<String, Object> toDict() {
        Map<String, Object> bulletPayload = new LinkedHashMap<>();
        for (Map.Entry<String, Bullet> entry : bullets.entrySet()) {
            bulletPayload.put(entry.getKey(), entry.getValue().toDict());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bullets", bulletPayload);
        payload.put("sections", sections);
        payload.put("next_id", nextId);
        return payload;
    }

    public static Playbook fromDict(Map<String, Object> payload) {
        Playbook instance = new Playbook();
        Object bulletsPayload = payload.getOrDefault("bullets", Map.of());
        if (bulletsPayload instanceof Map<?, ?> rawBullets) {
            for (Map.Entry<?, ?> entry : rawBullets.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> rawBulletValue) {
                    Map<String, Object> bulletMap = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> bulletEntry : rawBulletValue.entrySet()) {
                        bulletMap.put(String.valueOf(bulletEntry.getKey()), bulletEntry.getValue());
                    }
                    instance.bullets.put(String.valueOf(entry.getKey()), Bullet.fromDict(bulletMap));
                }
            }
        }
        Object sectionsPayload = payload.getOrDefault("sections", Map.of());
        if (sectionsPayload instanceof Map<?, ?> rawSections) {
            Map<String, List<String>> rebuilt = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawSections.entrySet()) {
                List<String> ids = new ArrayList<>();
                if (entry.getValue() instanceof Iterable<?> iterable) {
                    for (Object id : iterable) {
                        ids.add(String.valueOf(id));
                    }
                }
                rebuilt.put(String.valueOf(entry.getKey()), ids);
            }
            instance.sections = rebuilt;
        }
        instance.nextId = ((Number) payload.getOrDefault("next_id", 0)).intValue();
        return instance;
    }

    public String dumps() {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toDict());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize playbook.", ex);
        }
    }

    public static Playbook loads(String data) {
        try {
            Object payload = OBJECT_MAPPER.readValue(data, Object.class);
            if (!(payload instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("Playbook serialization must be a JSON object.");
            }
            Map<String, Object> typedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                typedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return fromDict(typedMap);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize playbook.", ex);
        }
    }

    public void applyDelta(DeltaBatch delta) {
        for (DeltaOperation operation : delta.getOperations()) {
            applyOperation(operation);
        }
    }

    public String asPrompt() {
        List<String> parts = new ArrayList<>();
        sections.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                parts.add("## " + entry.getKey());
                for (String bulletId : entry.getValue()) {
                    Bullet bullet = Objects.requireNonNull(bullets.get(bulletId));
                    String counters = "(helpful=" + bullet.getHelpful()
                        + ", harmful=" + bullet.getHarmful()
                        + ", neutral=" + bullet.getNeutral() + ")";
                    parts.add("- [" + bullet.getId() + "] " + bullet.getContent() + " " + counters);
                }
            });
        return String.join("\n", parts);
    }

    public Map<String, Object> stats() {
        Map<String, Integer> tags = new LinkedHashMap<>();
        tags.put("helpful", bullets.values().stream().mapToInt(Bullet::getHelpful).sum());
        tags.put("harmful", bullets.values().stream().mapToInt(Bullet::getHarmful).sum());
        tags.put("neutral", bullets.values().stream().mapToInt(Bullet::getNeutral).sum());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sections", sections.size());
        stats.put("bullets", bullets.size());
        stats.put("tags", tags);
        return stats;
    }

    public String makePlaybookExcerpt(List<String> bulletIds) {
        List<String> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
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

    private void applyOperation(DeltaOperation operation) {
        String opType = operation.getType().toUpperCase(Locale.ROOT);
        switch (opType) {
            case "ADD" -> addBullet(
                operation.getSection(),
                operation.getContent() != null ? operation.getContent() : "",
                operation.getBulletId(),
                operation.getMetadata()
            );
            case "UPDATE" -> {
                if (operation.getBulletId() != null) {
                    updateBullet(operation.getBulletId(), operation.getContent(), operation.getMetadata());
                }
            }
            case "TAG" -> {
                if (operation.getBulletId() != null) {
                    for (Map.Entry<String, Integer> entry : operation.getMetadata().entrySet()) {
                        tagBullet(operation.getBulletId(), entry.getKey(), entry.getValue());
                    }
                }
            }
            case "REMOVE" -> {
                if (operation.getBulletId() != null) {
                    removeBullet(operation.getBulletId());
                }
            }
            default -> {
                // Python silently ignores unsupported operation types.
            }
        }
    }

    private String generateId(String section) {
        nextId += 1;
        String sectionPrefix;
        if (section != null && !section.trim().isEmpty()) {
            sectionPrefix = WHITESPACE.split(section.trim())[0].toLowerCase(Locale.ROOT);
        } else {
            sectionPrefix = "general";
        }
        return sectionPrefix + "-" + String.format(Locale.ROOT, "%05d", nextId);
    }

    private static String normalizeSection(String section) {
        return section != null && !section.trim().isEmpty() ? section : "general";
    }

    private static String utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static class DeltaOperation {
        private String type;
        private String section;
        private String content;
        private String bulletId;
        private Map<String, Integer> metadata;

        public DeltaOperation(String type, String section, String content, String bulletId, Map<String, Integer> metadata) {
            this.type = type;
            this.section = section;
            this.content = content;
            this.bulletId = bulletId;
            this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        }

        public static DeltaOperation fromJson(Map<String, Object> payload) {
            Map<String, Integer> metadata = new LinkedHashMap<>();
            Object rawMetadata = payload.get("metadata");
            if (rawMetadata instanceof Map<?, ?> metadataMap) {
                for (Map.Entry<?, ?> entry : metadataMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null) {
                        metadata.put(String.valueOf(entry.getKey()), Integer.parseInt(String.valueOf(value)));
                    }
                }
            }
            Object content = payload.get("content");
            Object bulletId = payload.get("bullet_id");
            return new DeltaOperation(
                String.valueOf(payload.get("type")),
                String.valueOf(payload.getOrDefault("section", "")),
                content != null ? String.valueOf(content) : null,
                bulletId != null ? String.valueOf(bulletId) : null,
                metadata
            );
        }

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
                data.put("metadata", metadata);
            }
            return data;
        }

        public String getType() {
            return type;
        }

        public String getSection() {
            return section;
        }

        public String getContent() {
            return content;
        }

        public String getBulletId() {
            return bulletId;
        }

        public Map<String, Integer> getMetadata() {
            return new LinkedHashMap<>(metadata);
        }
    }

    public static class DeltaBatch {
        private String reasoning;
        private List<DeltaOperation> operations;

        public DeltaBatch(String reasoning, List<DeltaOperation> operations) {
            this.reasoning = reasoning;
            this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
        }

        public static DeltaBatch fromJson(Map<String, Object> payload) {
            Object opsPayload = payload.get("operations");
            List<DeltaOperation> operations = new ArrayList<>();
            if (opsPayload instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?> rawMap) {
                        Map<String, Object> map = OBJECT_MAPPER.convertValue(rawMap, MAP_TYPE);
                        operations.add(DeltaOperation.fromJson(map));
                    }
                }
            }
            return new DeltaBatch(String.valueOf(payload.getOrDefault("reasoning", "")), operations);
        }

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

        public String getReasoning() {
            return reasoning;
        }

        public List<DeltaOperation> getOperations() {
            return new ArrayList<>(operations);
        }
    }

    public static class Bullet {
        private String id;
        private String section;
        private String content;
        private int helpful;
        private int harmful;
        private int neutral;
        private String createdAt;
        private String updatedAt;

        public Bullet(String id, String section, String content) {
            this(id, section, content, 0, 0, 0, utcNow(), utcNow());
        }

        public Bullet(
            String id,
            String section,
            String content,
            int helpful,
            int harmful,
            int neutral,
            String createdAt,
            String updatedAt
        ) {
            this.id = id;
            this.section = normalizeSection(section);
            this.content = content != null ? content : "";
            this.helpful = helpful;
            this.harmful = harmful;
            this.neutral = neutral;
            this.createdAt = createdAt != null ? createdAt : utcNow();
            this.updatedAt = updatedAt != null ? updatedAt : utcNow();
        }

        public static Bullet fromDict(Map<String, Object> payload) {
            return new Bullet(
                String.valueOf(payload.get("id")),
                String.valueOf(payload.get("section")),
                String.valueOf(payload.get("content")),
                ((Number) payload.getOrDefault("helpful", 0)).intValue(),
                ((Number) payload.getOrDefault("harmful", 0)).intValue(),
                ((Number) payload.getOrDefault("neutral", 0)).intValue(),
                payload.get("created_at") != null ? String.valueOf(payload.get("created_at")) : utcNow(),
                payload.get("updated_at") != null ? String.valueOf(payload.get("updated_at")) : utcNow()
            );
        }

        public Map<String, Object> toDict() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("section", section);
            data.put("content", content);
            data.put("helpful", helpful);
            data.put("harmful", harmful);
            data.put("neutral", neutral);
            data.put("created_at", createdAt);
            data.put("updated_at", updatedAt);
            return data;
        }

        public void applyMetadata(Map<String, Integer> metadata) {
            for (Map.Entry<String, Integer> entry : metadata.entrySet()) {
                int value = entry.getValue();
                switch (entry.getKey()) {
                    case "helpful" -> helpful = value;
                    case "harmful" -> harmful = value;
                    case "neutral" -> neutral = value;
                    default -> {
                        // Python only uses integer metadata for known counters in this module's flow.
                    }
                }
            }
        }

        public void tag(String tag, int increment) {
            switch (tag) {
                case "helpful" -> helpful += increment;
                case "harmful" -> harmful += increment;
                case "neutral" -> neutral += increment;
                default -> throw new IllegalArgumentException("Unsupported tag: " + tag);
            }
            updatedAt = utcNow();
        }

        public String getId() {
            return id;
        }

        public String getSection() {
            return section;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content != null ? content : "";
        }

        public int getHelpful() {
            return helpful;
        }

        public int getHarmful() {
            return harmful;
        }

        public int getNeutral() {
            return neutral;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class BulletTag {
        private String id;
        private String tag;

        public BulletTag(String id, String tag) {
            this.id = id;
            this.tag = tag;
        }

        public String getId() {
            return id;
        }

        public String getTag() {
            return tag;
        }
    }
}
