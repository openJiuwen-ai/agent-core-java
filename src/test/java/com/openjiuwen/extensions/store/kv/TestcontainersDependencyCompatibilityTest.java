package com.openjiuwen.extensions.store.kv;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestcontainersDependencyCompatibilityTest {

    @Test
    void testcontainersAndArchiveClasspathAreCompatibleWithDockerSystemTests() {
        assertVersionAtLeast(
                DockerClientFactory.class.getPackage().getImplementationVersion(),
                "1.21.4",
                "Testcontainers must include Docker Desktop 29 detection fixes"
        );
        assertDoesNotThrow(
                () -> Class.forName("org.apache.commons.lang3.ArrayFill"),
                "commons-compress requires commons-lang3 ArrayFill when copying files to containers"
        );
    }

    private static void assertVersionAtLeast(String actualVersion, String minimumVersion, String message) {
        assertTrue(compareVersions(actualVersion, minimumVersion) >= 0,
                message + ": expected at least " + minimumVersion + " but was " + actualVersion);
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightPart = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }
}
