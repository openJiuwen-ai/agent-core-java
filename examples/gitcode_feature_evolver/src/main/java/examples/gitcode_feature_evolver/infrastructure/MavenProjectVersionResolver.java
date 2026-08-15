/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Resolves a trusted Maven project version without loading Maven plugins or external XML. */
final class MavenProjectVersionResolver {
    private static final long MAX_POM_BYTES = 2_000_000L;
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]*");

    private MavenProjectVersionResolver() {
    }

    static String resolve(Path sourceWorktree) {
        Path root = sourceWorktree.toAbsolutePath().normalize();
        Path pom = root.resolve("pom.xml").normalize();
        if (!pom.startsWith(root) || !Files.isRegularFile(pom) || Files.isSymbolicLink(pom)) {
            throw new ProjectVersionException("Frozen source pom.xml is unavailable");
        }
        try {
            if (Files.size(pom) > MAX_POM_BYTES) {
                throw new ProjectVersionException("Frozen source pom.xml exceeds the size limit");
            }
            Document document = parse(pom);
            String declared = declaredVersion(document.getDocumentElement())
                    .orElseThrow(() -> new ProjectVersionException(
                            "Frozen source pom.xml has no project version"));
            String resolved = resolveProperty(document.getDocumentElement(), declared);
            if (!SAFE_VERSION.matcher(resolved).matches()) {
                throw new ProjectVersionException(
                        "Frozen source pom.xml has an unsupported project version");
            }
            return resolved;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new ProjectVersionException("Frozen source pom.xml cannot be parsed safely", ex);
        }
    }

    private static Document parse(Path pom)
            throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new StrictErrorHandler());
        try (InputStream input = Files.newInputStream(pom)) {
            return builder.parse(input);
        }
    }

    private static Optional<String> declaredVersion(Element project) {
        Optional<String> version = directChildText(project, "version");
        if (version.isPresent()) {
            return version;
        }
        return directChild(project, "parent").flatMap(parent -> directChildText(parent, "version"));
    }

    private static String resolveProperty(Element project, String declared) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(declared);
        if (!matcher.matches()) {
            return declared;
        }
        String propertyName = matcher.group(1);
        return directChild(project, "properties")
                .flatMap(properties -> directChildText(properties, propertyName))
                .orElseThrow(() -> new ProjectVersionException(
                        "Frozen source project version property is unavailable"));
    }

    private static Optional<Element> directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(localName(element))) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> directChildText(Element parent, String name) {
        return directChild(parent, name).map(Element::getTextContent).map(String::strip)
                .filter(value -> !value.isEmpty());
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        if (local != null) {
            return local;
        }
        String qualified = element.getTagName();
        int separator = qualified.indexOf(':');
        return separator < 0 ? qualified : qualified.substring(separator + 1);
    }

    /** Stable build-contract error without exposing host paths or XML content. */
    static final class ProjectVersionException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        ProjectVersionException(String message) {
            super(message);
        }

        ProjectVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class StrictErrorHandler extends DefaultHandler {
        @Override
        public void warning(SAXParseException failure) throws SAXException {
            throw failure;
        }

        @Override
        public void error(SAXParseException failure) throws SAXException {
            throw failure;
        }

        @Override
        public void fatalError(SAXParseException failure) throws SAXException {
            throw failure;
        }
    }
}
