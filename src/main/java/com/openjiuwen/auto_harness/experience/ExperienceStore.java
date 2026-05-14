package com.openjiuwen.auto_harness.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.schema.Experience;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mirrors Python's {@code ExperienceStore} in {@code openjiuwen.auto_harness.experience.experience_store}.
 */
public class ExperienceStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double DEDUP_WINDOW_SECS = 86400.0;

    private final Path dir;
    private final Path path;

    public ExperienceStore(String experienceDir) {
        try {
            this.dir = Path.of(experienceDir);
            Files.createDirectories(dir);
            this.path = dir.resolve("experiences.jsonl");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize experience store", e);
        }
    }

    public String record(Experience experience) {
        if (isDuplicate(experience)) {
            return "";
        }
        append(experience);
        return experience.getId();
    }

    public List<Experience> search(String query) {
        return search(query, 5);
    }

    public List<Experience> search(String query, int topK) {
        List<String> keywords = tokenize(query);
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<ScoredExperience> scored = new ArrayList<>();
        for (Experience experience : loadAll()) {
            int hits = countHits(keywords, experience);
            if (hits == 0) {
                continue;
            }
            scored.add(new ScoredExperience(hits, experience));
        }
        scored.sort(Comparator.comparingDouble(ScoredExperience::score).reversed());
        return scored.stream().limit(topK).map(ScoredExperience::experience).toList();
    }

    public List<Experience> listRecent(int limit) {
        List<Experience> experiences = loadAll();
        experiences.sort(Comparator.comparingDouble(Experience::getTimestamp).reversed());
        return experiences.stream().limit(limit).toList();
    }

    public Experience get(String experienceId) {
        for (Experience experience : loadAll()) {
            if (experience.getId().equals(experienceId)) {
                return experience;
            }
        }
        return null;
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] raw = text.toLowerCase(Locale.ROOT).split("[^a-z0-9_\\-]+");
        List<String> tokens = new ArrayList<>();
        for (String token : raw) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    static int countHits(List<String> keywords, Experience experience) {
        String haystack = String.join(" ",
                nullSafe(experience.getTopic()),
                nullSafe(experience.getSummary()),
                nullSafe(experience.getDetails())
        ).toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    private boolean isDuplicate(Experience candidate) {
        for (Experience existing : loadAll()) {
            if (existing.getType() == candidate.getType()
                    && nullSafe(existing.getTopic()).equals(nullSafe(candidate.getTopic()))
                    && Math.abs(existing.getTimestamp() - candidate.getTimestamp()) <= DEDUP_WINDOW_SECS) {
                return true;
            }
        }
        return false;
    }

    private void append(Experience experience) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(experience);
            Files.writeString(path, json + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append experience", e);
        }
    }

    private List<Experience> loadAll() {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            List<Experience> experiences = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                experiences.add(OBJECT_MAPPER.readValue(line, Experience.class));
            }
            return experiences;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load experiences", e);
        }
    }

    private static String nullSafe(String text) {
        return text != null ? text : "";
    }

    private record ScoredExperience(int score, Experience experience) {}
}
