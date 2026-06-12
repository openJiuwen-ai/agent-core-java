/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the evolution store facade.
 *
 * <p>Mirrors Python's {@code EvolutionStore} in
 * {@code openjiuwen/agent_evolving/checkpointing/evolution_store.py}.</p>
 */
class EvolutionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesAndDeduplicatesBaseDirs() throws Exception {
        Path rootA = Files.createDirectory(tempDir.resolve("a"));
        Path rootB = Files.createDirectory(tempDir.resolve("b"));

        EvolutionStore store = new EvolutionStore(rootA + "; " + rootB + ", " + rootA);

        assertEquals(2, store.getBaseDirs().size());
        assertEquals(store.getBaseDirs().get(0), store.getBaseDir());
    }

    @Test
    void strictReadRequiresSkillDefinition() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path skillDir = Files.createDirectory(root.resolve("skill-a"));
        Files.writeString(skillDir.resolve("README.md"), "# fallback", StandardCharsets.UTF_8);
        EvolutionStore store = new EvolutionStore(root.toString());

        assertTrue(store.skillExists("skill-a"));
        assertFalse(store.skillDefinitionExists("skill-a"));
        assertEquals("# fallback", store.readSkillContent("skill-a").toCompletableFuture().join());

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> store.readSkillContent("skill-a", true).toCompletableFuture().join()
        );
        assertTrue(failure.getCause() instanceof BaseError);
        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_SKILL_DEFINITION_NOT_FOUND, ((BaseError) failure.getCause()).getStatus());
    }

    @Test
    void appendLoadFilterAndDeleteFollowPythonFacadeSemantics() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nContent\n");
        EvolutionStore store = new EvolutionStore(root.toString());

        store.appendRecord("skill-a", record("ev_desc", EvolutionTarget.DESCRIPTION, "Instructions", "short desc"))
                .toCompletableFuture()
                .join();
        store.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "Troubleshooting", "body fix"))
                .toCompletableFuture()
                .join();

        EvolutionLog descOnly = store.loadEvolutionLog("skill-a", EvolutionTarget.DESCRIPTION)
                .toCompletableFuture()
                .join();
        String skillMarkdown = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);

        assertEquals(List.of("skill-a"), store.listSkillNames());
        assertEquals(List.of("ev_desc"), descOnly.getEntries().stream().map(EvolutionRecord::getId).toList());
        assertTrue(Files.exists(root.resolve("skill-a").resolve("evolution").resolve("troubleshooting.md")));
        assertTrue(skillMarkdown.contains("evolution-index-start"));

        int deleted = store.deleteRecords("skill-a", List.of("ev_desc", "ev_body")).toCompletableFuture().join();
        String cleanedMarkdown = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);

        assertEquals(2, deleted);
        assertFalse(Files.exists(root.resolve("skill-a").resolve("evolution")));
        assertFalse(cleanedMarkdown.contains("evolution-index-start"));
    }

    @Test
    void skillIdAndSharingPackageUsePristineSkillContent() throws Exception {
        Path publisherRoot = Files.createDirectory(tempDir.resolve("publisher"));
        Path installerRoot = Files.createDirectory(tempDir.resolve("installer"));
        prepareSkill(publisherRoot, "skill-a", "# Skill A\n\nContent\n");
        Files.createDirectories(publisherRoot.resolve("skill-a").resolve("scripts"));
        Files.writeString(publisherRoot.resolve("skill-a").resolve("scripts").resolve("helper.py"), "print('hi')", StandardCharsets.UTF_8);
        EvolutionStore publisher = new EvolutionStore(publisherRoot.toString());
        EvolutionStore installer = new EvolutionStore(installerRoot.toString());

        String skillId = publisher.ensureSkillId("skill-a").toCompletableFuture().join();
        publisher.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "Troubleshooting", "body fix"))
                .toCompletableFuture()
                .join();
        String pristine = publisher.readPristineSkillContent("skill-a").toCompletableFuture().join();
        byte[] packageBytes = publisher.packSkillForSharing("skill-a").toCompletableFuture().join();
        Path installed = installer.installSkillPackage(packageBytes, "skill-a").toCompletableFuture().join();

        assertTrue(skillId.startsWith("sk_"));
        assertFalse(pristine.contains("evolution-index-start"));
        assertNotNull(installed);
        assertTrue(Files.isRegularFile(installed.resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(installed.resolve("scripts").resolve("helper.py")));
        assertEquals(skillId, installer.readSkillId("skill-a").toCompletableFuture().join());
        assertFalse(Files.readString(installed.resolve("SKILL.md"), StandardCharsets.UTF_8).contains("evolution-index-start"));
    }

    private static void prepareSkill(Path root, String name, String content) throws Exception {
        Path skillDir = Files.createDirectories(root.resolve(name));
        Files.writeString(skillDir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
    }

    private static EvolutionRecord record(
            String id,
            EvolutionTarget target,
            String section,
            String content
    ) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section(section)
                .action(Protocols.APPEND_MODE)
                .content(content)
                .target(target)
                .build();
        return EvolutionRecord.builder()
                .id(id)
                .source("execution_failure")
                .timestamp("2026-01-01T00:00:00+00:00")
                .context("ctx")
                .change(patch)
                .score(0.8d)
                .usageStats(new UsageStats())
                .build();
    }
}
