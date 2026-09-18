/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.experience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * JSONL-backed experience archive with keyword search.
 * <p>
 * Mirrors Python's {@code ExperienceStore} in
 * {@code openjiuwen/auto_harness/experience/experience_store.py}.
 */
public class ExperienceStore {

    private static final Logger LOGGER = Logger.getLogger(ExperienceStore.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double DEDUP_WINDOW_SECS = 86_400.0;
    private static final double MAX_RECENCY_AGE_SECS = 30.0 * 86_400.0;

    private final Path dir;
    private final Path path;

    public ExperienceStore(String experienceDir) {
        this(Path.of(experienceDir));
    }

    public ExperienceStore(Path experienceDir) {
        this.dir = experienceDir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create experience directory: " + dir, e);
        }
        this.path = dir.resolve("experiences.jsonl");
    }

    public CompletableFuture<String> record(Experience experience) {
        return CompletableFuture.completedFuture(recordSync(experience));
    }

    public CompletableFuture<List<Experience>> search(String query) {
        return search(query, 5);
    }

    public CompletableFuture<List<Experience>> search(String query, int topK) {
        return CompletableFuture.completedFuture(searchSync(query, topK));
    }

    public CompletableFuture<List<Experience>> listRecent() {
        return listRecent(20);
    }

    public CompletableFuture<List<Experience>> listRecent(int limit) {
        return CompletableFuture.completedFuture(listRecentSync(limit));
    }

    public CompletableFuture<Experience> get(String experienceId) {
        return CompletableFuture.completedFuture(getSync(experienceId));
    }

    String recordSync(Experience experience) {
        Objects.requireNonNull(experience, "experience must not be null");
        if (isDuplicate(experience)) {
            LOGGER.fine(() -> "Experience rejected (dup): type=" + experience.getType()
                    + " topic=" + experience.getTopic());
            return "";
        }
        append(experience);
        LOGGER.info(() -> "Experience recorded: id=" + experience.getId()
                + " type=" + experience.getType()
                + " topic=" + experience.getTopic());
        return experience.getId();
    }

    List<Experience> searchSync(String query, int topK) {
        List<String> keywords = tokenize(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        double now = epochSeconds();
        List<ScoredExperience> scored = new ArrayList<>();
        for (Experience experience : loadAll()) {
            int hits = countHits(keywords, experience);
            if (hits == 0) {
                continue;
            }
            scored.add(new ScoredExperience(hits + recencyScore(experience.getTimestamp(), now), experience));
        }

        scored.sort(Comparator.comparingDouble(ScoredExperience::score).reversed());
        int toIndex = topK >= 0
                ? Math.min(topK, scored.size())
                : Math.max(0, scored.size() + topK);
        List<Experience> result = new ArrayList<>();
        for (ScoredExperience item : scored.subList(0, toIndex)) {
            result.add(item.experience());
        }
        return result;
    }

    List<Experience> listRecentSync(int limit) {
        List<Experience> all = loadAll();
        all.sort(Comparator.comparingDouble(Experience::getTimestamp).reversed());
        int toIndex = limit >= 0
                ? Math.min(limit, all.size())
                : Math.max(0, all.size() + limit);
        return new ArrayList<>(all.subList(0, toIndex));
    }

    Experience getSync(String experienceId) {
        for (Experience experience : loadAll()) {
            if (Objects.equals(experience.getId(), experienceId)) {
                return experience;
            }
        }
        return null;
    }

    List<Experience> loadAll() {
        List<Experience> experiences = new ArrayList<>();
        if (!Files.exists(path)) {
            return experiences;
        }
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    Map<String, Object> data = MAPPER.readValue(line, new TypeReference<LinkedHashMap<String, Object>>() {
                    });
                    experiences.add(fromJsonMap(data));
                } catch (JsonProcessingException e) {
                    LOGGER.warning(() -> "Skipping malformed experience line: " + preview(line));
                } catch (RuntimeException e) {
                    LOGGER.warning(() -> "Skipping malformed experience line: " + preview(line));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read experience JSONL: " + path, e);
        }
        return experiences;
    }

    private void append(Experience experience) {
        try {
            Files.writeString(
                    path,
                    MAPPER.writeValueAsString(toJsonMap(experience)) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("Cannot append experience JSONL: " + path, e);
        }
    }

    private boolean isDuplicate(Experience experience) {
        double cutoff = epochSeconds() - DEDUP_WINDOW_SECS;
        for (Experience existing : loadAll()) {
            if (Objects.equals(existing.getTopic(), experience.getTopic())
                    && existing.getType() == experience.getType()
                    && existing.getTimestamp() >= cutoff) {
                return true;
            }
        }
        return false;
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String word : text.toLowerCase().split("\\s+")) {
            if (word.length() >= 2) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    public static int countHits(List<String> keywords, Experience experience) {
        String blob = (nullToEmpty(experience.getTopic()) + " "
                + nullToEmpty(experience.getSummary()) + " "
                + nullToEmpty(experience.getDetails())).toLowerCase();
        int hits = 0;
        for (String keyword : keywords) {
            if (blob.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }

    public static double recencyScore(double timestamp, double now) {
        double age = Math.max(now - timestamp, 0.0);
        if (age >= MAX_RECENCY_AGE_SECS) {
            return 0.0;
        }
        return 1.0 - (age / MAX_RECENCY_AGE_SECS);
    }

    private static Map<String, Object> toJsonMap(Experience experience) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", experience.getType() == null ? null : experience.getType().value());
        data.put("topic", experience.getTopic());
        data.put("summary", experience.getSummary());
        data.put("outcome", experience.getOutcome());
        data.put("details", experience.getDetails());
        data.put("pr_url", experience.getPrUrl());
        data.put("files_changed", listOrEmpty(experience.getFilesChanged()));
        data.put("signal", experience.getSignal());
        data.put("strategy", experience.getStrategy());
        data.put("causal_chain", experience.getCausalChain());
        data.put("signal_frequency", experience.getSignalFrequency());
        data.put("id", experience.getId());
        data.put("timestamp", experience.getTimestamp());
        return data;
    }

    private static Experience fromJsonMap(Map<String, Object> data) {
        if (!data.containsKey("type")) {
            throw new IllegalArgumentException("missing type");
        }
        Experience experience = new Experience();
        experience.setType(parseExperienceType(data.get("type")));
        experience.setTopic(stringValue(data.getOrDefault("topic", experience.getTopic())));
        experience.setSummary(stringValue(data.getOrDefault("summary", experience.getSummary())));
        experience.setOutcome(stringValue(data.getOrDefault("outcome", experience.getOutcome())));
        experience.setDetails(stringValue(data.getOrDefault("details", experience.getDetails())));
        experience.setPrUrl(stringValue(data.getOrDefault("pr_url", experience.getPrUrl())));
        experience.setFilesChanged(stringList(data.get("files_changed")));
        experience.setSignal(stringValue(data.getOrDefault("signal", experience.getSignal())));
        experience.setStrategy(stringValue(data.getOrDefault("strategy", experience.getStrategy())));
        experience.setCausalChain(stringValue(data.getOrDefault("causal_chain", experience.getCausalChain())));
        experience.setSignalFrequency(intValue(data.getOrDefault("signal_frequency", experience.getSignalFrequency())));
        experience.setId(stringValue(data.getOrDefault("id", experience.getId())));
        experience.setTimestamp(doubleValue(data.getOrDefault("timestamp", experience.getTimestamp())));
        return experience;
    }

    private static ExperienceType parseExperienceType(Object value) {
        String raw = stringValue(value);
        for (ExperienceType type : ExperienceType.values()) {
            if (type.value().equals(raw) || type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown experience type: " + raw);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(stringValue(item));
        }
        return result;
    }

    private static List<String> listOrEmpty(List<String> value) {
        return value == null ? List.of() : value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double epochSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    private static String preview(String line) {
        return line.length() <= 120 ? line : line.substring(0, 120);
    }

    private record ScoredExperience(double score, Experience experience) {
    }
}
