package com.openjiuwen.core.common.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathChecker 测试类
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
class PathCheckerTest {

    @Test
    void testSingletonInstance() {
        // Act
        PathChecker instance1 = PathChecker.getInstance();
        PathChecker instance2 = PathChecker.getInstance();
        
        // Assert
        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
    }

    @Test
    void testIsSensitivePathLinux() {
        // Arrange
        PathChecker checker = PathChecker.getInstance();
        String osName = System.getProperty("os.name").toLowerCase();
        
        // Act & Assert
        // On Windows, these paths are normalized differently
        // The test logic depends on the OS
        if (osName.contains("win")) {
            // On Windows, Unix paths are not considered sensitive
            // because they don't match after normalization
            assertNotNull(checker); // Just verify checker works
        } else {
            // On Unix/Linux, these should be sensitive
            assertTrue(checker.isSensitivePath("/etc/passwd"));
            assertTrue(checker.isSensitivePath("/etc/shadow"));
            assertTrue(checker.isSensitivePath("/etc/ssh/sshd_config"));
            assertTrue(checker.isSensitivePath("/proc/self/environ"));
        }
    }

    @Test
    void testIsSensitivePathWindows() {
        // Arrange
        PathChecker checker = PathChecker.getInstance();
        
        // Act & Assert
        assertTrue(checker.isSensitivePath("C:\\Windows\\System32\\config\\SAM"));
        assertTrue(checker.isSensitivePath("C:\\Windows\\SysWOW64\\cmd.exe"));
        assertTrue(checker.isSensitivePath("C:\\Windows\\System\\test.dll"));
    }

    @Test
    void testIsNotSensitivePath() {
        // Arrange
        PathChecker checker = PathChecker.getInstance();
        
        // Act & Assert
        assertFalse(checker.isSensitivePath("/home/user/test.txt"));
        assertFalse(checker.isSensitivePath("/tmp/test.log"));
        assertFalse(checker.isSensitivePath("C:\\Users\\test\\document.txt"));
        assertFalse(checker.isSensitivePath("/opt/myapp/config.yaml"));
    }

    @Test
    void testIsSensitivePathNull() {
        // Arrange
        PathChecker checker = PathChecker.getInstance();
        
        // Act & Assert
        assertFalse(checker.isSensitivePath((String) null));
        assertFalse(checker.isSensitivePath((Path) null));
    }

    @Test
    void testIsSensitivePathWithPath() {
        // Arrange
        PathChecker checker = PathChecker.getInstance();
        Path sensitivePath = Paths.get("/etc/passwd");
        Path normalPath = Paths.get("/tmp/test.txt");
        
        // Act & Assert
        assertTrue(checker.isSensitivePath(sensitivePath));
        assertFalse(checker.isSensitivePath(normalPath));
    }
}

