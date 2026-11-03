/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.LogCaptureFixture;
import com.openjiuwen.core.singleagent.skills.repository.SkillContentValidator;
import com.openjiuwen.core.singleagent.skills.repository.SkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.WorkspaceSkillRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Tests protected Skill override policy.
 *
 * @since 0.1.17
 */
class SkillProtectedOverrideTest {
    @TempDir
    private Path tempDir;

    @Test
    @Tag("TC-SEC-04")
    void protectedClasspathSkill_notOverriddenWhenBlockingEnabled() {
        Skill protectedSkill = new Skill("demo", "Protected classpath version.", null);
        protectedSkill.setSource(SkillSource.CLASSPATH);
        protectedSkill.setId("demo");
        protectedSkill.setProtectedSkill(true);
        Skill lowerPriority = new Skill("demo", "Filesystem version.", null);
        lowerPriority.setSource(SkillSource.FILESYSTEM);
        SkillRepository lowerRepo = fixedRepository(SkillSource.FILESYSTEM, List.of(lowerPriority));
        SkillRepository protectedRepo =
                fixedRepository(SkillSource.CLASSPATH, List.of(protectedSkill));

        SkillManager manager = new SkillManager("test-op");
        manager.setProtectedOverrideBlocked(true);
        manager.setRepositories(List.of(protectedRepo, lowerRepo));
        manager.refreshIncrementally(List.of());

        assertThat(manager.get("demo").getDescription()).isEqualTo("Protected classpath version.");
        assertThat(manager.get("demo").isProtectedSkill()).isTrue();
    }

    @Test
    @Tag("TC-SEC-05")
    void protectedSkill_overriddenWhenBlockingDisabled() {
        Skill protectedSkill = new Skill("demo", "Protected classpath version.", null);
        protectedSkill.setSource(SkillSource.CLASSPATH);
        protectedSkill.setProtectedSkill(true);
        Skill higherPriority = new Skill("demo", "Workspace version.", null);
        higherPriority.setSource(SkillSource.WORKSPACE);
        SkillRepository protectedRepo =
                fixedRepository(SkillSource.CLASSPATH, List.of(protectedSkill));
        SkillRepository higherRepo =
                fixedRepository(SkillSource.WORKSPACE, List.of(higherPriority));

        SkillManager manager = new SkillManager("test-op");
        manager.setProtectedOverrideBlocked(false);
        manager.setRepositories(List.of(protectedRepo, higherRepo));
        manager.refreshIncrementally(List.of());

        assertThat(manager.get("demo").getDescription()).isEqualTo("Workspace version.");
        assertThat(manager.get("demo").getSource()).isEqualTo(SkillSource.WORKSPACE);
    }

    @Test
    @Tag("TC-SEC-02")
    void oversizedWorkspaceSkill_rejectedByValidatorWithoutBlockingOthers() throws IOException {
        Path wsRoot = tempDir.resolve("ws");
        Path oversizedDir = wsRoot.resolve("oversized_skill");
        Files.createDirectories(oversizedDir);
        byte[] oversizedContent =
                contentWithSecretMarker(SkillContentValidator.DEFAULT_WORKSPACE_MAX_BYTES + 1);
        Files.write(oversizedDir.resolve("SKILL.md"), oversizedContent);
        writeSkill(wsRoot.resolve("normal_skill"), "normal_skill", "Normal skill.");

        SkillContentValidator validator = new SkillContentValidator();
        SkillContentValidator.ValidationResult oversizedResult =
                validator.validate(oversizedDir.resolve("SKILL.md"), wsRoot);
        assertThat(oversizedResult.isAllowed()).isFalse();
        assertThat(oversizedResult.size()).isEqualTo((long) oversizedContent.length);
        assertThat(oversizedResult.hash()).isEqualTo(sha256(oversizedContent));
        assertThat(
                        validator
                                .validate(
                                        wsRoot.resolve("normal_skill").resolve("SKILL.md"), wsRoot)
                                .isAllowed())
                .isTrue();
        SkillRepository wsRepo = new WorkspaceSkillRepository(wsRoot, "user1");
        try (LogCaptureFixture audit = LogCaptureFixture.attach("agent")) {
            SkillManager manager = new SkillManager("test-op");
            manager.setRepositories(List.of(wsRepo));
            manager.refreshIncrementally(List.of());
            assertThat(manager.has("normal_skill")).isTrue();
            assertThat(manager.has("oversized_skill")).isFalse();
            assertThat(audit.messagesAt("WARNING").get(0))
                    .contains("action=load")
                    .contains("source=workspace")
                    .contains("skill_id=unknown")
                    .contains("size=" + oversizedContent.length)
                    .contains("hash=" + sha256(oversizedContent))
                    .doesNotContain("SECRET-BODY-MARK");
        }
    }

    @Test
    @Tag("TC-SEC-02")
    void invalidEncodingSkillRejectedWithAudit()
            throws IOException {
        Path workspaceRoot = tempDir.resolve("ws");
        Path invalidSkillDirectory = workspaceRoot.resolve("invalid_encoding_skill");
        Files.createDirectories(invalidSkillDirectory);
        ByteArrayOutputStream invalidContent = new ByteArrayOutputStream();
        invalidContent.writeBytes("SECRET-BODY-MARK\n".getBytes(StandardCharsets.UTF_8));
        invalidContent.writeBytes(new byte[] {0x0A, (byte) 0xFF, (byte) 0xFE, 0x0A});
        byte[] invalidBytes = invalidContent.toByteArray();
        Files.write(invalidSkillDirectory.resolve("SKILL.md"), invalidBytes);
        writeSkill(workspaceRoot.resolve("normal_skill"), "normal_skill", "Normal skill.");

        SkillContentValidator validator = new SkillContentValidator();
        SkillContentValidator.ValidationResult invalidResult =
                validator.validate(invalidSkillDirectory.resolve("SKILL.md"), workspaceRoot);
        try (LogCaptureFixture audit = LogCaptureFixture.attach("agent")) {
            SkillRepository workspaceRepository =
                    new WorkspaceSkillRepository(workspaceRoot, "user1");
            SkillManager manager = new SkillManager("invalid-encoding-skill-test");
            manager.setRepositories(List.of(workspaceRepository));
            manager.refreshIncrementally(List.of());

            assertThat(invalidResult.isAllowed()).isFalse();
            assertThat(invalidResult.reason()).contains("invalid UTF-8 encoding");
            assertThat(invalidResult.size()).isEqualTo((long) invalidBytes.length);
            assertThat(invalidResult.hash()).isEqualTo(sha256(invalidBytes));
            assertThat(manager.has("invalid_encoding_skill")).isFalse();
            assertThat(manager.has("normal_skill")).isTrue();
            assertThat(audit.count("WARNING", "SKILL_REJECTED")).isEqualTo(1);
            assertThat(audit.messagesAt("WARNING").get(0))
                    .contains("invalid_encoding_skill")
                    .contains("invalid UTF-8 encoding")
                    .contains("size=" + invalidBytes.length)
                    .contains("hash=" + sha256(invalidBytes))
                    .doesNotContain("SECRET-BODY-MARK");
        }
    }

    @Test
    @Tag("TC-SEC-06")
    void workspacePathOutsideRoot_rejectedByValidator() throws IOException {
        Path wsRoot = tempDir.resolve("ws");
        Path outsideRoot = tempDir.resolve("outside");
        writeSkill(outsideRoot, "outside_skill", "Outside skill.");
        writeSkill(wsRoot.resolve("inside_skill"), "inside_skill", "Inside skill.");

        SkillContentValidator validator = new SkillContentValidator();
        assertThat(
                        validator
                                .validate(
                                        outsideRoot.resolve("outside_skill").resolve("SKILL.md"),
                                        wsRoot)
                                .isAllowed())
                .isFalse();
        assertThat(
                        validator
                                .validate(
                                        wsRoot.resolve("inside_skill").resolve("SKILL.md"), wsRoot)
                                .isAllowed())
                .isTrue();
    }

    @Test
    void validatorDefaults_matchPlanRecommendations() {
        SkillContentValidator validator = new SkillContentValidator();
        assertThat(validator.getWorkspaceMaxBytes()).isEqualTo(262144);
    }

    private static void writeSkill(Path directory, String name, String description)
            throws IOException {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("SKILL.md"),
                """
                ---
                name: %s
                description: %s
                ---
                Skill body.
                """
                        .formatted(name, description),
                StandardCharsets.UTF_8);
    }

    private static byte[] contentWithSecretMarker(int length) {
        byte[] marker = "SECRET-BODY-MARK\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[length];
        System.arraycopy(marker, 0, content, 0, Math.min(marker.length, content.length));
        return content;
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static SkillRepository fixedRepository(SkillSource source, List<Skill> skills) {
        return new SkillRepository() {
            @Override
            public SkillSource source() {
                return source;
            }

            @Override
            public List<Skill> loadAll() {
                return skills;
            }
        };
    }
}
