/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.skills.SkillFixtureFactory.SkillFixture;
import com.openjiuwen.core.singleagent.skills.SkillFixtureFactory.SkillFixtureSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests Skill fixture creation.
 *
 * @since 0.1.17
 */
class SkillFixtureFactoryTest {
    @TempDir
    private Path tempDirectory;

    @Test
    void writeSkill_whenThreeSourcesRequested_createsDistinctFiles() throws IOException {
        assertSkillFixture(
                SkillFixtureFactory.writeSkill(
                        tempDirectory,
                        SkillFixtureSource.CLASSPATH,
                        "demo",
                        "classpath body",
                        false),
                SkillFixtureSource.CLASSPATH,
                "classpath body",
                false);
        assertSkillFixture(
                SkillFixtureFactory.writeSkill(
                        tempDirectory,
                        SkillFixtureSource.FILESYSTEM,
                        "demo",
                        "filesystem body",
                        false),
                SkillFixtureSource.FILESYSTEM,
                "filesystem body",
                false);
        assertSkillFixture(
                SkillFixtureFactory.writeSkill(
                        tempDirectory,
                        SkillFixtureSource.WORKSPACE,
                        "demo",
                        "workspace body",
                        true),
                SkillFixtureSource.WORKSPACE,
                "workspace body",
                true);
    }

    private void assertSkillFixture(
            SkillFixture fixture,
            SkillFixtureSource source,
            String body,
            boolean isProtected)
            throws IOException {
        assertThat(fixture.source()).isEqualTo(source);
        assertThat(fixture.skillFile()).exists();
        assertThat(Files.readString(fixture.skillFile())).contains(body);
        if (isProtected) {
            assertThat(Files.readString(fixture.skillFile())).contains("protected: true");
        }
    }
}
