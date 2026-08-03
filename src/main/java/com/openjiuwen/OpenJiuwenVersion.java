package com.openjiuwen;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Mirrors Python's {@code openjiuwen} in {@code openjiuwen/__init__.py}.
 */
public final class OpenJiuwenVersion {
    private static final String DEFAULT_VERSION = "unknown";
    private static final String SOURCE_FALLBACK_VERSION = "0.1.14";
    private static final String POM_PROPERTIES_RESOURCE =
            "/META-INF/maven/com.openjiuwen/agent-core-java/pom.properties";
    private static final String VERSION = resolveVersion();

    private OpenJiuwenVersion() {
    }

    public static String version() {
        return VERSION;
    }

    private static String resolveVersion() {
        String packageVersion = packageVersion();
        if (packageVersion != null) {
            return packageVersion;
        }

        String pomVersion = pomVersion();
        if (pomVersion != null) {
            return pomVersion;
        }

        return SOURCE_FALLBACK_VERSION.isBlank() ? DEFAULT_VERSION : SOURCE_FALLBACK_VERSION;
    }

    private static String packageVersion() {
        Package sourcePackage = OpenJiuwenVersion.class.getPackage();
        if (sourcePackage == null) {
            return null;
        }
        return normalizeVersion(sourcePackage.getImplementationVersion());
    }

    private static String pomVersion() {
        try (InputStream stream = OpenJiuwenVersion.class.getResourceAsStream(POM_PROPERTIES_RESOURCE)) {
            if (stream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(stream);
            return normalizeVersion(properties.getProperty("version"));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String normalizeVersion(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return Objects.equals(trimmed, DEFAULT_VERSION) ? DEFAULT_VERSION : trimmed;
    }
}
