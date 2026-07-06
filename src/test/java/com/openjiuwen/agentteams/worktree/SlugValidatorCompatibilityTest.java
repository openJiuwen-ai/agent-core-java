package com.openjiuwen.agentteams.worktree;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugValidatorCompatibilityTest {

    @Test
    void shouldValidateSimpleSlug() {
        SlugValidator.validateSlug("feature-auth");
    }

    @Test
    void shouldValidateSlugWithDots() {
        SlugValidator.validateSlug("feature.auth.v1");
    }

    @Test
    void shouldValidateSlugWithUnderscores() {
        SlugValidator.validateSlug("feature_auth_v1");
    }

    @Test
    void shouldValidateSlugWithSlashes() {
        SlugValidator.validateSlug("user/feature-auth");
    }

    @Test
    void shouldRejectLongSlug() {
        String longSlug = "a".repeat(SlugValidator.MAX_SLUG_LENGTH + 1);
        assertThatThrownBy(() -> SlugValidator.validateSlug(longSlug))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void shouldRejectEmptySlug() {
        assertThatThrownBy(() -> SlugValidator.validateSlug(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThatThrownBy(() -> SlugValidator.validateSlug("../etc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void shouldRejectDotSegment() {
        assertThatThrownBy(() -> SlugValidator.validateSlug("."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void shouldRejectInvalidCharacters() {
        assertThatThrownBy(() -> SlugValidator.validateSlug("bad chars"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void shouldGenerateBranchName() {
        String branch = SlugValidator.worktreeBranchName("feature-auth");
        assertThat(branch).isEqualTo("worktree-feature-auth");
    }

    @Test
    void shouldReplaceSlashesInBranchName() {
        String branch = SlugValidator.worktreeBranchName("user/feature");
        assertThat(branch).isEqualTo("worktree-user+feature");
    }
}
