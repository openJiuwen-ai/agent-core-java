/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Resolves an explicitly trusted Maven plugin version without executing Maven. */
final class MavenPluginVersionResolver {
    private static final String JUNIT_GROUP = "org.junit.jupiter";
    private static final String PLATFORM_GROUP = "org.junit.platform";
    private static final long MAX_POM_BYTES = 2_000_000L;
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]*");

    private MavenPluginVersionResolver() {
    }

    static String resolve(Path worktree, String artifactId) {
        Element project = project(worktree);
        String declared = pluginVersion(project, artifactId).orElseThrow(
                () -> new PluginVersionException(
                        "System-test POM must pin " + artifactId + " version"));
        return validatedVersion(project, declared, artifactId);
    }

    /** Resolve the launcher release selected dynamically by the JUnit provider. */
    static String resolveJUnitPlatformLauncher(Path worktree) {
        Element project = project(worktree);
        Optional<String> launcher = dependencyVersion(project, PLATFORM_GROUP,
                "junit-platform-launcher");
        if (launcher.isPresent()) {
            return validatedVersion(project, launcher.orElseThrow(),
                    "junit-platform-launcher");
        }
        String jupiter = dependencyVersion(project, JUNIT_GROUP, "junit-jupiter")
                .or(() -> dependencyVersion(project, JUNIT_GROUP, "junit-jupiter-engine"))
                .or(() -> dependencyVersion(project, JUNIT_GROUP, "junit-jupiter-api"))
                .orElseThrow(() -> new PluginVersionException(
                        "System-test POM must pin a JUnit Jupiter release"));
        String release = validatedVersion(project, jupiter, "JUnit Jupiter");
        int separator = release.indexOf('.');
        if (separator <= 0 || separator == release.length() - 1) {
            throw new PluginVersionException(
                    "System-test POM has an unsupported JUnit Jupiter release");
        }
        try {
            int jupiterMajor = Integer.parseInt(release.substring(0, separator));
            if (jupiterMajor < 5) {
                throw new PluginVersionException(
                        "System-test POM has an unsupported JUnit Jupiter release");
            }
            String platform = (jupiterMajor - 4) + release.substring(separator);
            if (!SAFE_VERSION.matcher(platform).matches()) {
                throw new PluginVersionException(
                        "System-test POM has an unsupported JUnit Platform release");
            }
            return platform;
        } catch (NumberFormatException ex) {
            throw new PluginVersionException(
                    "System-test POM has an unsupported JUnit Jupiter release", ex);
        }
    }

    private static Element project(Path worktree) {
        Path root = worktree.toAbsolutePath().normalize();
        Path pom = root.resolve("pom.xml").normalize();
        if (!pom.startsWith(root) || !Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(pom)) {
            throw new PluginVersionException("System-test pom.xml is unavailable");
        }
        try {
            if (Files.size(pom) > MAX_POM_BYTES) {
                throw new PluginVersionException("System-test pom.xml exceeds the size limit");
            }
            Document document = parse(pom);
            return document.getDocumentElement();
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new PluginVersionException("System-test pom.xml cannot be parsed safely", ex);
        }
    }

    private static String validatedVersion(Element project, String declared, String subject) {
        String resolved = resolveProperty(project, declared);
        if (!SAFE_VERSION.matcher(resolved).matches()) {
            throw new PluginVersionException(
                    "System-test POM has an unsupported " + subject + " version");
        }
        return resolved;
    }

    private static Document parse(Path pom)
            throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (InputStream input = Files.newInputStream(pom)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static Optional<String> pluginVersion(Element project, String artifactId) {
        Optional<Element> build = child(project, "build");
        Optional<String> direct = build.flatMap(value -> child(value, "plugins"))
                .flatMap(plugins -> findPluginVersion(plugins, artifactId));
        if (direct.isPresent()) {
            return direct;
        }
        return build.flatMap(value -> child(value, "pluginManagement"))
                .flatMap(value -> child(value, "plugins"))
                .flatMap(plugins -> findPluginVersion(plugins, artifactId));
    }

    private static Optional<String> findPluginVersion(Element plugins, String artifactId) {
        NodeList children = plugins.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element plugin && "plugin".equals(localName(plugin))
                    && childText(plugin, "artifactId").filter(artifactId::equals).isPresent()) {
                return childText(plugin, "version");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> dependencyVersion(Element project, String groupId,
                                                      String artifactId) {
        Optional<String> direct = child(project, "dependencies")
                .flatMap(dependencies -> findDependencyVersion(
                        dependencies, groupId, artifactId));
        if (direct.isPresent()) {
            return direct;
        }
        return child(project, "dependencyManagement")
                .flatMap(value -> child(value, "dependencies"))
                .flatMap(dependencies -> findDependencyVersion(
                        dependencies, groupId, artifactId));
    }

    private static Optional<String> findDependencyVersion(Element dependencies,
                                                           String groupId,
                                                           String artifactId) {
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element dependency && "dependency".equals(localName(dependency))
                    && childText(dependency, "groupId").filter(groupId::equals).isPresent()
                    && childText(dependency, "artifactId").filter(artifactId::equals).isPresent()) {
                return childText(dependency, "version");
            }
        }
        return Optional.empty();
    }

    private static String resolveProperty(Element project, String declared) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(declared);
        if (!matcher.matches()) {
            return declared;
        }
        String propertyName = matcher.group(1);
        return child(project, "properties")
                .flatMap(properties -> childText(properties, propertyName))
                .orElseThrow(() -> new PluginVersionException(
                        "System-test Maven plugin version property is unavailable"));
    }

    private static Optional<Element> child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(localName(element))) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> childText(Element parent, String name) {
        return child(parent, name).map(Element::getTextContent).map(String::strip)
                .filter(value -> !value.isEmpty());
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    /** Stable trusted-POM contract error without exposing XML or host paths. */
    static final class PluginVersionException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        PluginVersionException(String message) {
            super(message);
        }

        PluginVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
