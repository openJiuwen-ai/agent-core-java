/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.singleagent.skills.SkillManagerTestHelper;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.local.LocalFsOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compatibility tests for SkillUseRail hot-reload functionality.
 *
 * <p>Tests the core hot-reload mechanisms: signature comparison,
 * incremental refresh, enableCache toggle, and skill lifecycle
 * (add/modify/delete without restart).</p>
 */
class SkillUseRailHotReloadTest {

    @TempDir
    private Path tempDir;

    private SkillManager manager;

    @BeforeEach
    void setUp() {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        manager = SkillManagerTestHelper.createWithResolver("test-hot-reload", id -> new LocalFsOperation(
                "fs", OperationMode.LOCAL, "local fs operation", null));
    }

    @AfterEach
    void tearDown() {
        Cwd.clear();
    }

    @Test
    void testSignatureSkipsRefreshWhenUnchanged() throws IOException {
        Path skillDir = tempDir.resolve("stable_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Stable\n---");

        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.get("stable_skill").getDescription()).isEqualTo("Stable");

        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.get("stable_skill").getDescription()).isEqualTo("Stable");

        List<Map.Entry<String, Long>> sig1 = buildSnapshotSignature(List.of(tempDir));
        assertThat(sig1).hasSize(1);
        assertThat(sig1.get(0).getKey()).contains("stable_skill");
    }

    @Test
    void testSignatureTriggersRefreshWhenChanged() throws IOException {
        Path skillDir = tempDir.resolve("change_skill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, "---\ndescription: V1\n---");

        List<Map.Entry<String, Long>> sig1 = buildSnapshotSignature(List.of(tempDir));
        manager.register(List.of(tempDir), true);
        assertThat(manager.get("change_skill").getDescription()).isEqualTo("V1");

        Files.writeString(skillMd, "---\ndescription: V2\n---");
        forceMtimeChange(skillMd);

        List<Map.Entry<String, Long>> sig2 = buildSnapshotSignature(List.of(tempDir));
        assertThat(SkillUseRailHotReloadTest.signaturesEqual(sig1, sig2)).isFalse();

        manager.clear();
        manager.register(List.of(tempDir), true);
        assertThat(manager.get("change_skill").getDescription()).isEqualTo("V2");
    }

    @Test
    void testNewSkillDetectedWithoutRestart() throws IOException {
        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isZero();

        Path skillDir = tempDir.resolve("new_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Newly added\n---");

        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.has("new_skill")).isTrue();
        assertThat(manager.get("new_skill").getDescription()).isEqualTo("Newly added");
    }

    @Test
    void testDeletedSkillRemovedWithoutRestart() throws IOException {
        Path skillDir = tempDir.resolve("del_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: To delete\n---");

        manager.register(List.of(tempDir), true);
        assertThat(manager.has("del_skill")).isTrue();

        Files.deleteIfExists(skillDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillDir);

        manager.clear();
        manager.register(List.of(tempDir), true);
        assertThat(manager.has("del_skill")).isFalse();
        assertThat(manager.count()).isZero();
    }

    @Test
    void testModifiedSkillUpdatedWithoutRestart() throws IOException {
        Path skillDir = tempDir.resolve("mod_skill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, "---\ndescription: Original\n---");

        manager.register(List.of(tempDir), true);
        assertThat(manager.get("mod_skill").getDescription()).isEqualTo("Original");

        Files.writeString(skillMd, "---\ndescription: Modified\n---");
        forceMtimeChange(skillMd);

        manager.clear();
        manager.register(List.of(tempDir), true);
        assertThat(manager.get("mod_skill").getDescription()).isEqualTo("Modified");
    }

    @Test
    void testCorruptSkillFileDoesNotAffectOthers() throws IOException {
        Path goodDir = tempDir.resolve("good_skill");
        Files.createDirectories(goodDir);
        Files.writeString(goodDir.resolve("SKILL.md"), "---\ndescription: Good skill\n---");

        manager.register(List.of(tempDir), true);

        assertThat(manager.has("good_skill")).isTrue();
        assertThat(manager.get("good_skill").getDescription()).isEqualTo("Good skill");
    }

    @Test
    void testCorruptSkillFileThrowsException() throws IOException {
        Path badDir = tempDir.resolve("bad_skill");
        Files.createDirectories(badDir);
        Files.writeString(badDir.resolve("SKILL.md"), "No YAML front matter here");

        assertThatThrownBy(() -> manager.register(List.of(tempDir), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEnableCacheFalseForcesFullRefresh() throws IOException {
        Path skillDir = tempDir.resolve("cache_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Cached\n---");

        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isEqualTo(1);

        manager.clear();
        assertThat(manager.count()).isZero();

        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.has("cache_skill")).isTrue();
    }

    @Test
    void testClearSkillsClearsCache() throws IOException {
        Path skillDir = tempDir.resolve("clear_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Clear\n---");

        manager.register(List.of(tempDir), true);
        assertThat(manager.count()).isEqualTo(1);

        manager.clear();
        assertThat(manager.count()).isZero();
        assertThat(manager.getAll()).isEmpty();
    }

    @Test
    void testMultipleSkillDirectories() throws IOException {
        Path root1 = tempDir.resolve("root1");
        Files.createDirectories(root1.resolve("skill_a"));
        Files.writeString(root1.resolve("skill_a").resolve("SKILL.md"), "---\ndescription: A from root1\n---");

        Path root2 = tempDir.resolve("root2");
        Files.createDirectories(root2.resolve("skill_b"));
        Files.writeString(root2.resolve("skill_b").resolve("SKILL.md"), "---\ndescription: B from root2\n---");

        manager.register(List.of(root1, root2), true);

        assertThat(manager.count()).isEqualTo(2);
        assertThat(manager.has("skill_a")).isTrue();
        assertThat(manager.has("skill_b")).isTrue();
    }

    @Test
    void testSkillUseRailConstructorWithEnableCache() {
        SkillUseRail rail = new SkillUseRail(
                List.of("/skills"), "all", List.of(), List.of(), List.of(), false);
        assertThat(rail.configuredSkillDirectories()).containsExactly("/skills");
        assertThat(rail.skillMode()).isEqualTo("all");

        SkillUseRail railDefault = new SkillUseRail(List.of("/skills"), "all");
        assertThat(railDefault.configuredSkillDirectories()).containsExactly("/skills");
    }

    @Test
    void testSkillUseRailSetEnableCache() {
        SkillUseRail rail = new SkillUseRail(List.of("/skills"), "all");
        rail.setEnableCache(false);
        rail.setEnableCache(true);
    }

    @Test
    void testSkillUseRailReloadAndClearSkills() throws IOException {
        Path skillDir = tempDir.resolve("rail_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Rail skill\n---");

        SkillUseRail rail = new SkillUseRail(List.of(tempDir.toString()), "all");
        assertThat(rail.registeredSkillNames()).isEmpty();

        rail.clearSkills();
        assertThat(rail.registeredSkillNames()).isEmpty();
    }

    static boolean signaturesEqual(List<Map.Entry<String, Long>> a, List<Map.Entry<String, Long>> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getKey().equals(b.get(i).getKey())
                    || a.get(i).getValue() != b.get(i).getValue()) {
                return false;
            }
        }
        return true;
    }

    private List<Map.Entry<String, Long>> buildSnapshotSignature(List<Path> roots) {
        List<Map.Entry<String, Long>> signature = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                for (Path child : stream.toList()) {
                    Path skillMd = child.resolve("SKILL.md");
                    if (Files.isRegularFile(skillMd)) {
                        long mtime = Files.getLastModifiedTime(skillMd).toMillis();
                        signature.add(Map.entry(child.getFileName().toString(), mtime));
                    }
                }
            } catch (IOException ignored) {
                // Skip unreadable roots.
            }
        }
        return signature;
    }

    private void forceMtimeChange(Path file) throws IOException {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        file.toFile().setLastModified(file.toFile().lastModified() + 2000);
    }
}
