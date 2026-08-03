/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Clone, scan, and copy community skill sources.
 * <p>
 * Mirrors Python's {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
 */
public final class SkillSourceManager {

    private static final Logger LOGGER = Logger.getLogger(SkillSourceManager.class.getName());
    private static final int DEFAULT_MAX_AGE_HOURS = 24 * 7;
    private static final Pattern UNSAFE_REPO_CHARS = Pattern.compile("[^a-zA-Z0-9._-]+");
    private static final Map<String, String> GITHUB_ZIP_URLS = Map.of(
            "https://github.com/anthropics/skills.git",
            "https://github.com/anthropics/skills/archive/refs/heads/main.zip",
            "https://github.com/JimLiu/baoyu-skills.git",
            "https://github.com/JimLiu/baoyu-skills/archive/refs/heads/main.zip"
    );

    private SkillSourceManager() {
    }

    /**
     * Mirrors Python's {@code SkillMatch} in
     * {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
     */
    public record SkillMatch(String name, String description, String repoUrl, Path skillDir) {
    }

    /**
     * Mirrors Python's git subprocess executor in
     * {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
     */
    public interface GitCommandExecutor {
        GitCommandResult execute(List<String> args, String cwd, Map<String, String> env)
                throws IOException, InterruptedException;
    }

    /**
     * Mirrors Python's git command result tuple in
     * {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
     */
    public record GitCommandResult(int returnCode, String output) {
    }

    public static List<String> ensureSkillSources(AutoHarnessConfig config) {
        return ensureSkillSources(config, null, new ProcessGitCommandExecutor());
    }

    public static List<String> ensureSkillSources(
            AutoHarnessConfig config,
            Consumer<String> emit,
            GitCommandExecutor executor
    ) {
        Path cacheDir = Path.of(config.getResolvedCommunitySkillCacheDir());
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOGGER.warning("[SkillSourceManager] failed to create cache dir: " + e.getMessage());
            return List.of();
        }

        String username = config.resolveGitcodeUsername();
        String token = config.resolveGitcodeToken();
        Map<String, String> gitEnv = !isBlank(username) && !isBlank(token)
                ? GitAuth.buildGitAuthEnv(username, token)
                : Map.of();
        List<String> clonedDirs = new ArrayList<>();

        for (String repoUrl : config.getCommunitySkillRepos()) {
            String repoName = repoNameFromUrl(repoUrl);
            Path target = cacheDir.resolve(repoName);

            if (isGithubRepo(repoUrl)) {
                handleGithubZipRepo(repoUrl, target, emit, clonedDirs);
                continue;
            }

            handleGitRepo(repoUrl, target, emit, executor, gitEnv, clonedDirs);
        }
        return clonedDirs;
    }

    public static Map<String, SkillMatch> scanSkills(AutoHarnessConfig config) {
        Path cacheDir = Path.of(config.getResolvedCommunitySkillCacheDir());
        if (!Files.isDirectory(cacheDir)) {
            return Map.of();
        }

        Map<String, SkillMatch> skillMap = new LinkedHashMap<>();
        for (String repoUrl : config.getCommunitySkillRepos()) {
            Path repoDir = cacheDir.resolve(repoNameFromUrl(repoUrl));
            if (!Files.isDirectory(repoDir)) {
                continue;
            }
            Path skillsSubdir = repoDir.resolve("skills");
            Path scanDir = Files.isDirectory(skillsSubdir) ? skillsSubdir : repoDir;
            for (Path item : sortedChildren(scanDir)) {
                String name = item.getFileName().toString();
                if (!Files.isDirectory(item) || name.startsWith(".") || name.startsWith("_")) {
                    continue;
                }
                Path skillMd = item.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    continue;
                }
                skillMap.put(name, new SkillMatch(name, loadSkillDescription(skillMd), repoUrl, item));
            }
        }
        return skillMap;
    }

    public static Optional<Path> copySkillToExtension(
            String skillName,
            Path extensionRoot,
            AutoHarnessConfig config
    ) {
        SkillMatch match = scanSkills(config).get(skillName);
        if (match == null) {
            LOGGER.warning("[SkillSourceManager] community skill '" + skillName + "' not found in cache");
            return Optional.empty();
        }

        Path destSkillsDir = extensionRoot.resolve("skills");
        Path destSkillDir = destSkillsDir.resolve(skillName);
        try {
            Files.createDirectories(destSkillsDir);
            deleteTree(destSkillDir);
            copyTree(match.skillDir(), destSkillDir);
            patchSkillFrontmatter(destSkillDir.resolve("SKILL.md"), skillName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy community skill '" + skillName + "'", e);
        }

        LOGGER.info("[SkillSourceManager] copied community skill '" + skillName + "' from "
                + match.skillDir() + " to " + destSkillDir);
        return Optional.of(destSkillDir);
    }

    public static List<String> communitySkillCacheSkillDirs(AutoHarnessConfig config) {
        Path cacheDir = Path.of(config.getResolvedCommunitySkillCacheDir());
        if (!Files.isDirectory(cacheDir)) {
            return List.of();
        }

        List<String> dirs = new ArrayList<>();
        for (String repoUrl : config.getCommunitySkillRepos()) {
            Path repoDir = cacheDir.resolve(repoNameFromUrl(repoUrl));
            if (!Files.isDirectory(repoDir)) {
                continue;
            }
            Path skillsSubdir = repoDir.resolve("skills");
            dirs.add(Files.isDirectory(skillsSubdir) ? skillsSubdir.toString() : repoDir.toString());
        }
        return dirs;
    }

    public static String formatCommunitySkillList(AutoHarnessConfig config) {
        Map<String, SkillMatch> skillsMap = scanSkills(config);
        if (skillsMap.isEmpty()) {
            return "可复用社区 Skill 列表: 无（缓存目录为空或未克隆）";
        }

        List<String> lines = new ArrayList<>();
        lines.add("可复用社区 Skill 列表（优先复用，不要自行设计 SKILL.md）:");
        skillsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String description = entry.getValue().description();
                    if (isBlank(description)) {
                        description = "(无描述)";
                    }
                    if (description.length() > 120) {
                        description = description.substring(0, 117) + "...";
                    }
                    lines.add("- " + entry.getKey() + ": " + description);
                });
        return String.join("\n", lines);
    }

    static boolean isGithubRepo(String repoUrl) {
        return GITHUB_ZIP_URLS.containsKey(repoUrl);
    }

    static String githubZipUrl(String repoUrl) {
        return GITHUB_ZIP_URLS.getOrDefault(repoUrl, "");
    }

    static boolean downloadGithubZip(String zipUrl, Path target) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(zipUrl)).GET().build();
            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                LOGGER.warning("[SkillSourceManager] download failed for " + zipUrl + ": status " + response.statusCode());
                return false;
            }
            extractGithubZip(response.body(), target);
            LOGGER.info("[SkillSourceManager] downloaded and extracted " + zipUrl + " to " + target);
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warning("[SkillSourceManager] download/extract failed for " + zipUrl + ": " + e);
            return false;
        }
    }

    static boolean checkDownloadTimestamp(Path repoDir) {
        return checkDownloadTimestamp(repoDir, DEFAULT_MAX_AGE_HOURS);
    }

    static boolean checkDownloadTimestamp(Path repoDir, int maxAgeHours) {
        if (Files.isDirectory(repoDir.resolve(".git"))) {
            return checkPullTimestamp(repoDir, maxAgeHours);
        }
        try {
            Instant modified = Files.getLastModifiedTime(repoDir).toInstant();
            return Duration.between(modified, Instant.now()).toHours() < maxAgeHours;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean checkPullTimestamp(Path repoDir) {
        return checkPullTimestamp(repoDir, DEFAULT_MAX_AGE_HOURS);
    }

    static boolean checkPullTimestamp(Path repoDir, int maxAgeHours) {
        Path fetchHead = repoDir.resolve(".git").resolve("FETCH_HEAD");
        if (!Files.isRegularFile(fetchHead)) {
            return false;
        }
        try {
            Instant modified = Files.getLastModifiedTime(fetchHead).toInstant();
            return Duration.between(modified, Instant.now()).toHours() < maxAgeHours;
        } catch (IOException e) {
            return false;
        }
    }

    static String repoNameFromUrl(String url) {
        String clean = nullToEmpty(url).replaceFirst("/+$", "");
        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        String[] parts = clean.split("/");
        String slug;
        if (parts.length >= 2) {
            slug = parts[parts.length - 2] + "-" + parts[parts.length - 1];
        } else {
            slug = parts.length == 0 ? "" : parts[0];
        }
        slug = UNSAFE_REPO_CHARS.matcher(slug).replaceAll("-");
        return slug.isBlank() ? "community-skills" : slug;
    }

    static String loadSkillDescription(Path skillMd) {
        try {
            String text = Files.readString(skillMd, StandardCharsets.UTF_8);
            Optional<FrontmatterBlock> block = parseFrontmatter(text);
            if (block.isEmpty()) {
                return "";
            }
            Object loaded = new Yaml().load(block.get().yaml());
            if (!(loaded instanceof Map<?, ?> map)) {
                return "";
            }
            Object description = map.get("description");
            return description == null ? "" : String.valueOf(description);
        } catch (Exception e) {
            return "";
        }
    }

    static void patchSkillFrontmatter(Path skillMdPath, String skillName) {
        if (!Files.isRegularFile(skillMdPath)) {
            return;
        }

        String text;
        try {
            text = Files.readString(skillMdPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.severe("[SkillSourceManager] failed to read SKILL.md: " + skillMdPath + ": " + e);
            return;
        }

        Optional<FrontmatterBlock> block = parseFrontmatter(text);
        if (block.isPresent()) {
            Map<String, Object> data;
            try {
                Object loaded = new Yaml().load(block.get().yaml());
                data = loaded instanceof Map<?, ?> map ? toLinkedMap(map) : new LinkedHashMap<>();
            } catch (Exception e) {
                LOGGER.warning("[SkillSourceManager] YAML parse error in " + skillMdPath + ": " + e + ", using empty dict");
                data = new LinkedHashMap<>();
            }

            boolean needsPatch = false;
            if (!data.containsKey("name") || isBlank(String.valueOf(data.get("name")))) {
                data.put("name", skillName);
                needsPatch = true;
            }
            if (!data.containsKey("description") || isBlank(String.valueOf(data.get("description")))) {
                data.put("description", "Community skill: " + skillName);
                needsPatch = true;
            }
            if (needsPatch) {
                String patched = "---\n" + dumpYaml(data) + "---\n" + block.get().body().stripLeading();
                writeSkillMarkdown(skillMdPath, patched);
                LOGGER.info("[SkillSourceManager] patched frontmatter for '" + skillName + "'");
            }
            return;
        }

        String newContent = "---\nname: " + skillName + "\ndescription: Community skill: " + skillName + "\n---\n\n" + text;
        writeSkillMarkdown(skillMdPath, newContent);
    }

    private static void handleGithubZipRepo(
            String repoUrl,
            Path target,
            Consumer<String> emit,
            List<String> clonedDirs
    ) {
        try {
            if (Files.isDirectory(target)) {
                if (checkDownloadTimestamp(target)) {
                    LOGGER.info("[SkillSourceManager] skipping download for " + repoUrl + " (downloaded within 7d)");
                    clonedDirs.add(target.toString());
                    return;
                }
                LOGGER.info("[SkillSourceManager] removing old directory for " + repoUrl);
                deleteTree(target);
            }
            emit(emit, "正在下载社区 skill 源仓: " + repoUrl);
            if (downloadGithubZip(githubZipUrl(repoUrl), target)) {
                emit(emit, "已下载社区 skill 源仓: " + repoUrl);
                clonedDirs.add(target.toString());
            } else {
                emit(emit, "下载社区 skill 源仓失败: " + repoUrl);
            }
        } catch (IOException e) {
            LOGGER.warning("[SkillSourceManager] download handling failed for " + repoUrl + ": " + e);
        }
    }

    private static void handleGitRepo(
            String repoUrl,
            Path target,
            Consumer<String> emit,
            GitCommandExecutor executor,
            Map<String, String> gitEnv,
            List<String> clonedDirs
    ) {
        try {
            if (Files.isDirectory(target) && Files.isDirectory(target.resolve(".git"))) {
                if (checkPullTimestamp(target)) {
                    LOGGER.info("[SkillSourceManager] skipping pull for " + repoUrl + " (pulled within 48h)");
                    clonedDirs.add(target.toString());
                    return;
                }
                emit(emit, "正在更新社区 skill 源仓: " + repoUrl);
                GitCommandResult result = executor.execute(List.of("pull", "--depth", "1"), target.toString(), gitEnv);
                if (result.returnCode() != 0) {
                    LOGGER.warning("[SkillSourceManager] pull failed for " + repoUrl
                            + " (continuing with cached version): " + result.output());
                } else {
                    LOGGER.info("[SkillSourceManager] pulled updates for " + repoUrl);
                }
                clonedDirs.add(target.toString());
                return;
            }

            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            emit(emit, "正在克隆社区 skill 源仓: " + repoUrl);
            LOGGER.info("[SkillSourceManager] cloning " + repoUrl + " to " + target);
            GitCommandResult result = executor.execute(
                    List.of("clone", "--depth", "1", repoUrl, target.toString()),
                    parent == null ? "." : parent.toString(),
                    gitEnv
            );
            if (result.returnCode() != 0) {
                LOGGER.warning("[SkillSourceManager] clone failed for " + repoUrl + ": " + result.output());
                emit(emit, "克隆社区 skill 源仓失败: " + repoUrl);
                return;
            }
            LOGGER.info("[SkillSourceManager] cloned " + repoUrl + " to " + target);
            emit(emit, "已克隆社区 skill 源仓: " + repoUrl);
            clonedDirs.add(target.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warning("[SkillSourceManager] git operation failed for " + repoUrl + ": " + e);
        }
    }

    private static void extractGithubZip(byte[] zipData, Path target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            String prefix = "";
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (prefix.isEmpty()) {
                    int slash = name.indexOf('/');
                    prefix = slash >= 0 ? name.substring(0, slash + 1) : "";
                }
                if (!prefix.isEmpty() && !name.startsWith(prefix)) {
                    continue;
                }
                String relative = prefix.isEmpty() ? name : name.substring(prefix.length());
                if (relative.isEmpty()) {
                    continue;
                }
                Path dest = target.resolve(relative).normalize();
                if (entry.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(dest);
                } else {
                    Path parent = dest.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static Optional<FrontmatterBlock> parseFrontmatter(String text) {
        if (!text.startsWith("---")) {
            return Optional.empty();
        }
        int start = text.startsWith("---\r\n") ? 5 : 4;
        int end = text.indexOf("\n---", start);
        if (end < 0) {
            return Optional.empty();
        }
        int bodyStart = end + 4;
        if (bodyStart < text.length() && text.charAt(bodyStart) == '\r') {
            bodyStart++;
        }
        if (bodyStart < text.length() && text.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        return Optional.of(new FrontmatterBlock(text.substring(start, end), text.substring(bodyStart)));
    }

    private static Map<String, Object> toLinkedMap(Map<?, ?> source) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            data.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return data;
    }

    private static String dumpYaml(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(data);
    }

    private static void writeSkillMarkdown(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warning("[SkillSourceManager] failed to patch frontmatter for " + path + ": " + e);
        }
    }

    private static List<Path> sortedChildren(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void copyTree(Path source, Path dest) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(
                        file,
                        dest.resolve(source.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void emit(Consumer<String> emit, String message) {
        if (emit != null) {
            emit.accept(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Mirrors Python's parsed SKILL.md frontmatter in
     * {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
     */
    private record FrontmatterBlock(String yaml, String body) {
    }

    /**
     * Mirrors Python's subprocess-backed git invocation in
     * {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
     */
    private static final class ProcessGitCommandExecutor implements GitCommandExecutor {
        @Override
        public GitCommandResult execute(List<String> args, String cwd, Map<String, String> env)
                throws IOException, InterruptedException {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(args);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(cwd));
            builder.environment().putAll(env);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (!first) {
                        output.append('\n');
                    }
                    output.append(line);
                    first = false;
                }
                return new GitCommandResult(process.waitFor(), output.toString().strip());
            }
        }
    }
}
