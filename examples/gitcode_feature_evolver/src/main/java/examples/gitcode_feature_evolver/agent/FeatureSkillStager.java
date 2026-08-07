/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Stages trusted Feature DevFlow and coding-standard Skills outside mutable Worktrees.
 *
 * @since 0.1.12
 */
public final class FeatureSkillStager {
    private FeatureSkillStager() {
    }

    /**
     * Rebuild the trusted Skill root from explicit source directories.
     *
     * @param stagingRoot external runtime staging root
     * @param featureSkill Feature DevFlow Skill source
     * @param codingStandard coding-standard Skill source
     * @return normalized staging root
     */
    public static Path stage(Path stagingRoot, Path featureSkill, Path codingStandard) {
        Path root = normalize(stagingRoot, "stagingRoot");
        try {
            Files.createDirectories(root);
            copySkill(root, "gitcode-feature-devflow", normalize(featureSkill, "featureSkill"));
            copySkill(root, "coding-standard", normalize(codingStandard, "codingStandard"));
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to stage trusted feature Skills", ex);
        }
    }

    private static void copySkill(Path root, String name, Path source) throws IOException {
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(source.resolve("SKILL.md"))) {
            throw new IOException("Invalid trusted Skill source or target");
        }
        deleteTree(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                rejectSymbolicLink(directory);
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                rejectSymbolicLink(file);
                Path destination = target.resolve(source.relativize(file)).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Trusted Skill copy escaped its destination");
                }
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Symbolic links are not allowed in trusted Skills");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name + " must not be null").toAbsolutePath().normalize();
    }
}
