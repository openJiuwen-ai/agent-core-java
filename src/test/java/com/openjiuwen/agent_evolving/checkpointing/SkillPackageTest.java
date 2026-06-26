/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for skill package utilities.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.skill_package} in
 * {@code openjiuwen/agent_evolving/checkpointing/skill_package.py}.</p>
 */
class SkillPackageTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureSkillIdInContentAddsFrontmatterField() {
        String content = "---\nname: demo\ndescription: d\n---\n\n# Body\n";

        SkillPackage.SkillIdContent ensured = SkillPackage.ensureSkillIdInContent(content);

        assertTrue(ensured.skillId().startsWith("sk_"));
        assertTrue(ensured.content().contains("skill_id: " + ensured.skillId()));
        assertEquals(ensured.skillId(), SkillPackage.readSkillIdFromContent(ensured.content()));
    }

    @Test
    void ensureSkillIdInContentPreservesExistingId() {
        String content = "---\nskill_id: sk_existing\nname: demo\n---\n\n# Body\n";

        SkillPackage.SkillIdContent ensured = SkillPackage.ensureSkillIdInContent(content);

        assertEquals(content, ensured.content());
        assertEquals("sk_existing", ensured.skillId());
    }

    @Test
    void listPackableFilesUsesPythonExclusionRules() throws Exception {
        Path skillDir = Files.createDirectory(tempDir.resolve("skill-a"));
        write(skillDir.resolve("SKILL.md"), "# Skill\n");
        write(skillDir.resolve("scripts").resolve("helper.py"), "print('hi')\n");
        write(skillDir.resolve("evolution").resolve("local.md"), "local\n");
        write(skillDir.resolve("archive").resolve("old.md"), "old\n");
        write(skillDir.resolve("__pycache__").resolve("x.pyc"), "cache\n");
        write(skillDir.resolve(".git").resolve("config"), "git\n");
        write(skillDir.resolve("evolutions.json"), "{}\n");
        write(skillDir.resolve(".hidden"), "hidden\n");
        write(skillDir.resolve("nested").resolve("evolution").resolve("keep.txt"), "keep\n");

        List<String> relative = SkillPackage.listPackableFiles(skillDir).stream()
                .map(path -> skillDir.toAbsolutePath().normalize().relativize(path).toString().replace('\\', '/'))
                .toList();

        assertEquals(List.of("SKILL.md", "nested/evolution/keep.txt", "scripts/helper.py"), relative);
    }

    @Test
    void packSkillDirectoryCanOverrideSkillMarkdownAndUnpack() throws Exception {
        Path skillDir = Files.createDirectory(tempDir.resolve("skill-a"));
        write(skillDir.resolve("SKILL.md"), "# Skill\n\n<!-- evolution-index-start -->local<!-- evolution-index-end -->\n");
        write(skillDir.resolve("scripts").resolve("helper.py"), "print('hi')\n");
        Path destination = Files.createDirectory(tempDir.resolve("installed"));

        byte[] packageBytes = SkillPackage.packSkillDirectory(skillDir, "SKILL.md", "# Skill\n");
        SkillPackage.unpackSkillPackage(packageBytes, destination);

        String installedSkill = Files.readString(destination.resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertEquals("# Skill\n", installedSkill);
        assertTrue(Files.isRegularFile(destination.resolve("scripts").resolve("helper.py")));
        assertFalse(Files.exists(destination.resolve("evolutions.json")));
    }

    private static void write(Path path, String content) throws Exception {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
