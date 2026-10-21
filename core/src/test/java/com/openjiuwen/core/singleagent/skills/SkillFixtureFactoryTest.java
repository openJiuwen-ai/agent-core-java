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
        assertFixture(SkillFixtureSource.CLASSPATH, "classpath body");
        assertFixture(SkillFixtureSource.FILESYSTEM, "filesystem body");
        assertFixture(SkillFixtureSource.WORKSPACE, "workspace body");
    }

    private void assertFixture(SkillFixtureSource source, String body) throws IOException {
        SkillFixture fixture =
                SkillFixtureFactory.writeSkill(
                        tempDirectory, source, "demo", body, source == SkillFixtureSource.WORKSPACE);
        assertThat(fixture.source()).isEqualTo(source);
        assertThat(fixture.skillFile()).exists();
        assertThat(Files.readString(fixture.skillFile())).contains(body);
    }
}
