/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.agent;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Stages the two trusted Skills under one immutable-for-Agent root.
 *
 * @since 0.1.12
 */
public final class TrustedSkillStager {
    private TrustedSkillStager() {
    }

    /**
     * Rebuild the trusted Skill root from configured sources outside mutable Worktrees.
     *
     * @param stagingRoot external runtime staging root
     * @param codingStandardSource coding-standard Skill directory
     * @param issueWorkerSource Issue worker Skill directory
     * @return normalized single Skill root
     */
    public static Path stage(Path stagingRoot, Path codingStandardSource, Path issueWorkerSource) {
        Path root = stagingRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            copySkill(root, "coding-standard", codingStandardSource);
            copySkill(root, "gitcode-issue-evolver-worker", issueWorkerSource);
            return root;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to stage trusted Skills", ex);
        }
    }

    private static void copySkill(Path root, String name, Path source) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Invalid trusted Skill target");
        }
        deleteTree(target);
        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Files.createDirectories(target.resolve(normalizedSource.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path destination = target.resolve(normalizedSource.relativize(file)).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Trusted Skill copy escaped its destination");
                }
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
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
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
