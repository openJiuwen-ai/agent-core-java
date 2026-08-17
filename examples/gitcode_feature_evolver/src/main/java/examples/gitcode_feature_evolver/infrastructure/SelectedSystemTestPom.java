/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Creates a Controller-owned Maven POM that compiles only approved system-test roots. */
final class SelectedSystemTestPom {
    private static final long MAX_POM_BYTES = 2_000_000L;
    private static final String COMPILER_PLUGIN = "maven-compiler-plugin";

    private SelectedSystemTestPom() {
    }

    static Path create(Path dataDir, Path testWorktree, List<String> selectors) {
        Path root = testWorktree.toAbsolutePath().normalize();
        Path source = root.resolve("pom.xml").normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source)) {
            throw new SelectedPomException("System-test pom.xml is unavailable");
        }
        try {
            if (Files.size(source) > MAX_POM_BYTES) {
                throw new SelectedPomException("System-test pom.xml exceeds the size limit");
            }
            byte[] original = Files.readAllBytes(source);
            Document document = parse(original);
            replaceTestIncludes(document, selectors);
            byte[] selected = serialize(document);
            return persist(dataDir, original, selectors, selected);
        } catch (IOException | ParserConfigurationException | SAXException
                 | TransformerException ex) {
            throw new SelectedPomException(
                    "System-test pom.xml cannot be constrained safely", ex);
        }
    }

    private static Document parse(byte[] content)
            throws ParserConfigurationException, IOException, SAXException {
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
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    private static void replaceTestIncludes(Document document, List<String> selectors) {
        Element project = document.getDocumentElement();
        Element build = child(project, "build").orElseGet(
                () -> append(document, project, "build"));
        Element plugins = child(build, "plugins").orElseGet(
                () -> append(document, build, "plugins"));
        Element compiler = compilerPlugin(plugins).orElseGet(() -> {
            Element plugin = append(document, plugins, "plugin");
            append(document, plugin, "groupId").setTextContent("org.apache.maven.plugins");
            append(document, plugin, "artifactId").setTextContent(COMPILER_PLUGIN);
            return plugin;
        });
        Element configuration = child(compiler, "configuration").orElseGet(
                () -> append(document, compiler, "configuration"));
        child(configuration, "testIncludes").ifPresent(configuration::removeChild);
        Element includes = append(document, configuration, "testIncludes");
        for (String selector : selectors) {
            Element include = append(document, includes, "testInclude");
            include.setTextContent(selector.replace('.', '/') + ".java");
        }
    }

    private static Optional<Element> compilerPlugin(Element plugins) {
        NodeList children = plugins.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element plugin && "plugin".equals(localName(plugin))
                    && childText(plugin, "artifactId").filter(COMPILER_PLUGIN::equals)
                    .isPresent()) {
                return Optional.of(plugin);
            }
        }
        return Optional.empty();
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
        return child(parent, name).map(Element::getTextContent).map(String::strip);
    }

    private static Element append(Document document, Element parent, String name) {
        String namespace = parent.getNamespaceURI();
        Element child = namespace == null
                ? document.createElement(name) : document.createElementNS(namespace, name);
        parent.appendChild(child);
        return child;
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static byte[] serialize(Document document) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }

    private static Path persist(Path dataDir, byte[] original, List<String> selectors,
                                byte[] selected) throws IOException {
        Path directory = dataDir.toAbsolutePath().normalize().resolve("gate-contracts");
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new SelectedPomException("System-test Gate contract directory is unsafe");
        }
        MessageDigest digest = sha256();
        digest.update(original);
        for (String selector : selectors) {
            digest.update((byte) 0);
            digest.update(selector.getBytes(StandardCharsets.UTF_8));
        }
        String fingerprint = HexFormat.of().formatHex(digest.digest());
        Path target = directory.resolve("system-test-" + fingerprint + ".xml");
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(target)) {
            return target;
        }
        Path temporary = Files.createTempFile(directory, ".system-test-", ".tmp");
        try {
            Files.write(temporary, selected);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another concurrent Gate created the identical immutable contract.
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target)) {
            throw new SelectedPomException("System-test Gate contract could not be persisted");
        }
        return target;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** Stable build-contract error without exposing host paths or XML content. */
    static final class SelectedPomException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        SelectedPomException(String message) {
            super(message);
        }

        SelectedPomException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
