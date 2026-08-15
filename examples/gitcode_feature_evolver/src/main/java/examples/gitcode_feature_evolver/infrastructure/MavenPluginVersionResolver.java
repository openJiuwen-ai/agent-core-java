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
    private static final long MAX_POM_BYTES = 2_000_000L;
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]*");

    private MavenPluginVersionResolver() {
    }

    static String resolve(Path worktree, String artifactId) {
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
            Element project = document.getDocumentElement();
            String declared = pluginVersion(project, artifactId).orElseThrow(
                    () -> new PluginVersionException(
                            "System-test POM must pin " + artifactId + " version"));
            String resolved = resolveProperty(project, declared);
            if (!SAFE_VERSION.matcher(resolved).matches()) {
                throw new PluginVersionException(
                        "System-test POM has an unsupported " + artifactId + " version");
            }
            return resolved;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new PluginVersionException("System-test pom.xml cannot be parsed safely", ex);
        }
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
