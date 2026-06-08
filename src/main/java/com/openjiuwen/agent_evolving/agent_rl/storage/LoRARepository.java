// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LoRA repository for model adaptation storage.
 *
 * <p>Mirrors Python's {@code LoRARepository} and {@code LoRAVersion} in
 * {@code openjiuwen/agent_evolving/agent_rl/storage/lora_repo.py}.
 */
public class LoRARepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern VERSION_RE = Pattern.compile("^v(\\d+)$");

    private final Path repoPath;

    public LoRARepository(String repoPath) {
        this.repoPath = repoPath != null ? Paths.get(repoPath) : Paths.get("lora_repo");
        try {
            Files.createDirectories(this.repoPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create LoRA repository: " + this.repoPath, exception);
        }
    }

    /**
     * Publish a LoRA artifact for a user and return the created version record.
     */
    public LoRAVersion publish(String userId, String loraPath) {
        return publish(userId, loraPath, Map.of(), "");
    }

    /**
     * Publish a LoRA artifact for a user and return the created version record.
     */
    public LoRAVersion publish(String userId, String loraPath, Map<String, Object> metadata, String baseModel) {
        String normalizedUserId = normalizeUserId(userId);
        Path userDir = repoPath.resolve(normalizedUserId);
        try {
            Files.createDirectories(userDir);
            int nextNum = listVersionDirs(userDir).stream()
                    .mapToInt(LoRARepository::versionNum)
                    .max()
                    .orElse(0) + 1;
            String version = "v" + nextNum;
            Path versionDir = userDir.resolve(version);
            Files.createDirectories(versionDir);

            copyLoraArtifact(Paths.get(loraPath), versionDir);

            Map<String, Object> safeMetadata = metadata != null ? metadata : Map.of();
            int trajectoryCount = numericValue(
                    safeMetadata.getOrDefault("trajectory_count", safeMetadata.getOrDefault("sample_count", 0))
            ).intValue();
            double rewardAvg = numericValue(
                    safeMetadata.getOrDefault("reward_avg", safeMetadata.getOrDefault("avg_score", 0.0))
            ).doubleValue();
            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("user_id", normalizedUserId);
            meta.put("version", version);
            meta.put("created_at", createdAt.toString());
            meta.put("trajectory_count", trajectoryCount);
            meta.put("reward_avg", rewardAvg);
            meta.put("base_model", baseModel != null ? baseModel : "");
            Files.writeString(
                    versionDir.resolve("metadata.json"),
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(meta)
            );

            updateLatest(userDir, version);
            return new LoRAVersion(
                    normalizedUserId,
                    version,
                    versionDir.toString(),
                    createdAt,
                    trajectoryCount,
                    rewardAvg,
                    baseModel != null ? baseModel : ""
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to publish LoRA artifact for user " + normalizedUserId, exception);
        }
    }

    /**
     * Return the user's newest LoRA version, or empty when none exists.
     */
    public Optional<LoRAVersion> getLatest(String userId) {
        String normalizedUserId = normalizeUserId(userId);
        Path userDir = repoPath.resolve(normalizedUserId);
        Path latest = userDir.resolve("latest");
        try {
            if (!Files.exists(latest) && !Files.isSymbolicLink(latest)) {
                return Optional.empty();
            }
            Path versionDir = Files.isSymbolicLink(latest)
                    ? latest.toRealPath()
                    : userDir.resolve(Files.readString(latest).trim());
            Path metaFile = versionDir.resolve("metadata.json");
            if (!Files.exists(metaFile)) {
                return Optional.empty();
            }
            return Optional.of(readVersion(metaFile, versionDir));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /**
     * Python-style nullable latest accessor for callers that prefer null checks.
     */
    public LoRAVersion latestOrNull(String userId) {
        return getLatest(userId).orElse(null);
    }

    /**
     * List all version records for one user.
     */
    public List<LoRAVersion> listVersions(String userId) {
        Path userDir = repoPath.resolve(normalizeUserId(userId));
        if (!Files.exists(userDir)) {
            return List.of();
        }
        List<LoRAVersion> versions = new ArrayList<>();
        for (Path versionDir : listVersionDirs(userDir)) {
            Path metaFile = versionDir.resolve("metadata.json");
            if (!Files.exists(metaFile)) {
                continue;
            }
            try {
                versions.add(readVersion(metaFile, versionDir));
            } catch (IOException ignored) {
                // Python skips version directories without readable metadata.
            }
        }
        return versions;
    }

    /**
     * Backward-compatible adapter save helper.
     *
     * <p>String and Path values are treated as LoRA artifact paths. Other values
     * are persisted as a small text payload under a new version directory.
     */
    public void saveAdapter(String adapterName, Object adapter) {
        if (adapter instanceof Path path) {
            publish(adapterName, path.toString());
            return;
        }
        if (adapter instanceof String path && Files.exists(Paths.get(path))) {
            publish(adapterName, path);
            return;
        }
        try {
            Path scratch = Files.createTempDirectory("lora-adapter-");
            Files.writeString(scratch.resolve("adapter.txt"), String.valueOf(adapter));
            publish(adapterName, scratch.toString());
            deleteRecursively(scratch);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save adapter " + adapterName, exception);
        }
    }

    /**
     * Backward-compatible adapter load helper.
     */
    public Object loadAdapter(String adapterName) {
        return getLatest(adapterName).<Object>map(LoRAVersion::path).orElse(null);
    }

    /**
     * List users with at least one adapter version.
     */
    public List<String> listAdapters() {
        if (!Files.exists(repoPath)) {
            return new ArrayList<>();
        }
        try (var stream = Files.list(repoPath)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> !listVersionDirs(path).isEmpty())
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            return new ArrayList<>();
        }
    }

    /**
     * Get repository path.
     */
    public Path getRepoPath() {
        return repoPath;
    }

    private static String normalizeUserId(String userId) {
        String normalized = userId != null ? userId.trim() : "";
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("user_id is required");
        }
        return normalized;
    }

    private static void copyLoraArtifact(Path source, Path versionDir) throws IOException {
        if (Files.isDirectory(source)) {
            try (var stream = Files.list(source)) {
                for (Path child : stream.toList()) {
                    if (Files.isDirectory(child)) {
                        throw new IOException("Nested LoRA artifact directories are not copied: " + child);
                    }
                    Files.copy(
                            child,
                            versionDir.resolve(child.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES
                    );
                }
            }
            return;
        }
        Files.copy(
                source,
                versionDir.resolve(source.getFileName()),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        );
    }

    private static void updateLatest(Path userDir, String version) throws IOException {
        Path latest = userDir.resolve("latest");
        Path tmpLatest = userDir.resolve(".latest_tmp");
        Files.deleteIfExists(tmpLatest);
        if (Files.exists(latest) && Files.isDirectory(latest) && !Files.isSymbolicLink(latest)) {
            throw new IllegalStateException("Cannot update latest symlink because " + latest + " is a directory");
        }
        try {
            Files.createSymbolicLink(tmpLatest, Paths.get(version));
        } catch (IOException | RuntimeException exception) {
            Files.writeString(tmpLatest, version);
        }
        try {
            Files.move(tmpLatest, latest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tmpLatest, latest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<Path> listVersionDirs(Path userDir) {
        if (!Files.exists(userDir)) {
            return List.of();
        }
        try (var stream = Files.list(userDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> VERSION_RE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(LoRARepository::versionNum))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static int versionNum(Path versionDir) {
        Matcher matcher = VERSION_RE.matcher(versionDir.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version directory name: " + versionDir.getFileName());
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static LoRAVersion readVersion(Path metaFile, Path versionDir) throws IOException {
        Map<String, Object> meta = OBJECT_MAPPER.readValue(Files.readString(metaFile), MAP_TYPE);
        return new LoRAVersion(
                String.valueOf(meta.get("user_id")),
                String.valueOf(meta.get("version")),
                versionDir.toString(),
                OffsetDateTime.parse(String.valueOf(meta.get("created_at"))),
                numericValue(meta.get("trajectory_count")).intValue(),
                numericValue(meta.get("reward_avg")).doubleValue(),
                String.valueOf(meta.getOrDefault("base_model", ""))
        );
    }

    private static Number numericValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return 0;
        }
        String text = String.valueOf(value);
        if (text.contains(".")) {
            return Double.parseDouble(text);
        }
        return Integer.parseInt(text);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Immutable LoRA version metadata.
     */
    public record LoRAVersion(
            String userId,
            String version,
            String path,
            OffsetDateTime createdAt,
            int trajectoryCount,
            double rewardAvg,
            String baseModel
    ) {
    }
}
