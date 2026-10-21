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

class SkillFixtureFactoryTest {

    @TempDir private Path tempDirectory;

    @Test
    void writeSkill_whenThreeSourcesRequested_createsDistinctFiles() throws IOException {
        SkillFixture classpathSkill =
                SkillFixtureFactory.writeSkill(
                        tempDirectory,
                        SkillFixtureSource.CLASSPATH,
                        "demo",
                        "classpath body",
                        false);
        SkillFixture filesystemSkill =
                SkillFixtureFactory.writeSkill(
                        tempDirectory,
                        SkillFixtureSource.FILESYSTEM,
                        "demo",
                        "filesystem body",
                        false);
        SkillFixture workspaceSkill =
                SkillFixtureFactory.writeSkill(
                        tempDirectory,
                        SkillFixtureSource.WORKSPACE,
                        "demo",
                        "workspace body",
                        true);

        assertThat(classpathSkill.source()).isEqualTo(SkillFixtureSource.CLASSPATH);
        assertThat(classpathSkill.skillFile()).exists();
        assertThat(Files.readString(classpathSkill.skillFile())).contains("classpath body");
        assertThat(filesystemSkill.source()).isEqualTo(SkillFixtureSource.FILESYSTEM);
        assertThat(filesystemSkill.skillFile()).exists();
        assertThat(Files.readString(filesystemSkill.skillFile())).contains("filesystem body");
        assertThat(workspaceSkill.source()).isEqualTo(SkillFixtureSource.WORKSPACE);
        assertThat(workspaceSkill.skillFile()).exists();
        assertThat(Files.readString(workspaceSkill.skillFile())).contains("workspace body");
        assertThat(Files.readString(workspaceSkill.skillFile())).contains("protected: true");
    }
}
