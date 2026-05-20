package com.openjiuwen.autoharness.infra;

import com.openjiuwen.core.sysop.cwd.CwdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EditScopeCompatibilityTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        CwdContext.reset();
    }

    @Test
    void normalizeRepoPathShouldReturnRepoRelativePathsInsideProjectRoot() {
        Path projectRoot = tempDir.resolve("repo");
        Path nested = projectRoot.resolve("openjiuwen/harness/cli/README.md");
        CwdContext.initCwd(projectRoot.toString(), projectRoot.toString(), null, null);

        assertThat(EditScope.normalizeRepoPath(nested.toString()))
                .isEqualTo("openjiuwen/harness/cli/README.md");
        assertThat(EditScope.normalizeRepoPath("./tests/unit_tests/sample.py"))
                .isEqualTo("tests/unit_tests/sample.py");
    }

    @Test
    void isAllowedRepoEditPathShouldMirrorPythonEditScopePrefixes() {
        CwdContext.initCwd(tempDir.toString());

        assertThat(EditScope.isAllowedRepoEditPath("openjiuwen/harness/cli/tool.py")).isTrue();
        assertThat(EditScope.isAllowedRepoEditPath("openjiuwen/core/runtime.py")).isTrue();
        assertThat(EditScope.isAllowedRepoEditPath("tests/unit_tests/auto_harness/test_schema.py")).isTrue();
        assertThat(EditScope.isAllowedRepoEditPath("examples/auto_harness/demo.py")).isTrue();
        assertThat(EditScope.isAllowedRepoEditPath("docs/en/guide.md")).isTrue();
        assertThat(EditScope.isAllowedRepoEditPath("openjiuwen/auto_harness/schema.py")).isFalse();
    }

    @Test
    void renderEditScopeShouldProvideStableGuidanceBlock() {
        String rendered = EditScope.renderEditScope("本轮实现阶段允许改动的路径");

        assertThat(rendered).contains("本轮实现阶段允许改动的路径:");
        assertThat(rendered).contains("`openjiuwen/harness/**`");
        assertThat(rendered).contains("`openjiuwen/core/**`");
        assertThat(rendered).contains("`tests/**`");
        assertThat(rendered).contains("`docs/en/`");
        assertThat(rendered).contains("`openjiuwen/auto_harness/**`");
    }
}
