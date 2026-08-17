package com.openjiuwen.harness.security.patterns;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathMatcherTest {

    @Nested
    class Wildcard {
        @Test
        void exactString_matchesFully() {
            assertThat(WildcardMatcher.match("cat", "cat")).isTrue();
            assertThat(WildcardMatcher.match("cat", "concat")).isFalse();
        }

        @Test
        void trailingSpaceStar_matchesOptionalSuffix() {
            assertThat(WildcardMatcher.match("ls *", "ls -la")).isTrue();
            assertThat(WildcardMatcher.match("ls *", "ls")).isTrue();
        }

        @Test
        void starDoesNotMatchShellMetachars() {
            assertThat(WildcardMatcher.match("git status *", "git status; rm -rf /")).isFalse();
        }
    }

    @Nested
    class PathMatching {
        @Test
        void exactPath_matches() {
            assertThat(PathMatcher.matchPath("/etc/hosts", "/etc/hosts")).isTrue();
        }

        @Test
        void parentPrefix_matchesViaParentWalk() {
            assertThat(PathMatcher.matchPath("/etc", "/etc/hosts")).isTrue();
            assertThat(PathMatcher.matchPath("/data/public", "/data/public/file.txt")).isTrue();
        }

        @Test
        void unrelatedPath_doesNotMatch() {
            assertThat(PathMatcher.matchPath("/etc", "/var/log")).isFalse();
        }
    }

    @Nested
    class Glob {
        @Test
        void doubleStar_crossesDirectories() {
            assertThat(GlobMatcher.match("/data/**", "/data/a/b")).isTrue();
        }

        @Test
        void singleStar_doesNotCrossSlash() {
            assertThat(GlobMatcher.match("/data/*", "/data/a/b")).isFalse();
            assertThat(GlobMatcher.match("/data/*", "/data/foo")).isTrue();
        }
    }

    @Nested
    class Contains {
        @Test
        void childUnderParent_true() {
            assertThat(PathMatcher.containsPath("/etc", "/etc/hosts")).isTrue();
        }

        @Test
        void childOutsideParent_false() {
            assertThat(PathMatcher.containsPath("/etc", "/var/log")).isFalse();
        }
    }
}
