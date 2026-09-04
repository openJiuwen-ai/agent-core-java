package com.openjiuwen.harness.security.patterns;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

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

        /**
         * On Windows (case-insensitive NTFS), glob matching must be case-insensitive
         * so that case variants cannot bypass a deny rule. On Linux this test is
         * skipped because the filesystem is case-sensitive — case variants are
         * genuinely distinct paths.
         */
        @Test
        @EnabledOnOs(OS.WINDOWS)
        void globCaseInsensitive_windows() {
            assertThat(GlobMatcher.match("D:/tmp/*.txt", "d:/tmp/cookies.txt")).isTrue();
            assertThat(GlobMatcher.match("D:/tmp/*.txt", "D:/TMP/COOKIES.TXT")).isTrue();
            assertThat(GlobMatcher.match("D:/tmp/*.txt", "d:/Tmp/Cookies.Txt")).isTrue();
        }

        /**
         * On Linux (case-sensitive filesystem), glob matching must be case-sensitive.
         * Case variants are genuinely distinct paths and must NOT match.
         */
        @Test
        @EnabledOnOs(OS.LINUX)
        void globCaseSensitive_linux() {
            assertThat(GlobMatcher.match("/tmp/*.txt", "/tmp/cookies.txt")).isTrue();
            assertThat(GlobMatcher.match("/tmp/*.txt", "/TMP/cookies.txt")).isFalse();
            assertThat(GlobMatcher.match("/tmp/*.txt", "/tmp/COOKIES.TXT")).isFalse();
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
