
package com.openjiuwen.core.sysop;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.sysop.CwdState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class CwdContextTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        CwdContext.reset();
    }

    @Test
    void initShouldPopulateAllLayers() {
        Path projectRoot = tempDir.resolve("project");
        Path workspace = tempDir.resolve("workspace");
        Path teamWorkspace = tempDir.resolve("team");
        CwdContext.initCwd(tempDir.toString(), projectRoot.toString(), workspace.toString(), teamWorkspace.toString());

        assertThat(CwdContext.getCwd()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
        assertThat(CwdContext.getOriginalCwd()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
        assertThat(CwdContext.getProjectRoot()).isEqualTo(projectRoot.toAbsolutePath().normalize().toString());
        assertThat(CwdContext.getWorkspace()).isEqualTo(workspace.toAbsolutePath().normalize().toString());
        assertThat(CwdContext.getTeamWorkspace()).isEqualTo(teamWorkspace.toAbsolutePath().normalize().toString());
    }

    @Test
    void settersShouldUpdateState() {
        CwdContext.initCwd(tempDir.toString());
        CwdContext.setCwd(tempDir.resolve("next").toString());
        CwdContext.setOriginalCwd(tempDir.resolve("origin").toString());
        CwdContext.setProjectRoot(tempDir.resolve("project").toString());
        CwdContext.setWorkspace(tempDir.resolve("workspace").toString());
        CwdContext.setTeamWorkspace(tempDir.resolve("team").toString());

        CwdState snapshot = CwdContext.snapshot();
        assertThat(snapshot.getCwd()).isEqualTo(tempDir.resolve("next").toAbsolutePath().normalize().toString());
        assertThat(snapshot.getOriginalCwd())
                .isEqualTo(tempDir.resolve("origin").toAbsolutePath().normalize().toString());
        assertThat(snapshot.getProjectRoot())
                .isEqualTo(tempDir.resolve("project").toAbsolutePath().normalize().toString());
        assertThat(snapshot.getWorkspace())
                .isEqualTo(tempDir.resolve("workspace").toAbsolutePath().normalize().toString());
        assertThat(snapshot.getTeamWorkspace())
                .isEqualTo(tempDir.resolve("team").toAbsolutePath().normalize().toString());
    }

    @Test
    void defaultsShouldFallBackToCurrentDirectory() {
        CwdContext.reset();
        String current = Path.of(".").toAbsolutePath().normalize().toString();

        assertThat(CwdContext.getCwd()).isEqualTo(current);
        assertThat(CwdContext.getOriginalCwd()).isEqualTo(current);
        assertThat(CwdContext.getProjectRoot()).isEqualTo(current);
        assertThat(CwdContext.getWorkspace()).isNull();
        assertThat(CwdContext.getTeamWorkspace()).isNull();
    }
}
