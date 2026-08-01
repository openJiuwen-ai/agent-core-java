package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingBackendDecouplingTest {

    @Test
    void productionSourcesDoNotReferenceLogbackImplementation() {
        Path mainSources = Path.of("src/main/java");
        List<Path> logbackReferences = findLogbackReferences(mainSources);

        assertTrue(logbackReferences.isEmpty(),
                "SDK production code must depend on slf4j-api only and not reference ch.qos.logback.* "
                        + "Matching paths: " + logbackReferences);
    }

    @Test
    void testSourcesDoNotReferenceLogbackImplementationOutsideThisGuard() {
        Path testSources = Path.of("src/test/java");
        List<Path> logbackReferences = findLogbackReferences(testSources).stream()
                .filter(path -> !"LoggingBackendDecouplingTest.java".equals(path.getFileName().toString()))
                .toList();

        assertTrue(logbackReferences.isEmpty(),
                "SDK tests must not require a concrete Logback backend. Matching paths: " + logbackReferences);
    }

    @Disabled("remote env do not support node")
    @Test
    void projectPomDoesNotExposeLogbackAsSdkDependency() {
        List<String> exposedLogbackDependencies = findExposedLogbackDependencies();

        assertTrue(exposedLogbackDependencies.isEmpty(),
                "agent-core-java must not expose logback-classic as a compile/runtime dependency. "
                        + "Matching dependencies: " + exposedLogbackDependencies);
    }

    private static List<Path> findLogbackReferences(Path mainSources) {
        try (Stream<Path> files = Files.walk(mainSources)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(LoggingBackendDecouplingTest::containsLogbackReference)
                    .toList();
        } catch (IOException exception) {
            throw new AssertionError("Failed to walk " + mainSources, exception);
        }
    }

    private static List<String> findExposedLogbackDependencies() {
        Path pom = Path.of("pom.xml");

        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilderFactory.setNamespaceAware(true);

            Document document;
            try (InputStream inputStream = Files.newInputStream(pom)) {
                document = documentBuilderFactory.newDocumentBuilder().parse(inputStream);
            }

            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList dependencies = (NodeList) xpath.evaluate(
                    "/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']",
                    document,
                    XPathConstants.NODESET);

            List<String> exposedDependencies = new ArrayList<>();
            for (int index = 0; index < dependencies.getLength(); index++) {
                Node dependency = dependencies.item(index);
                String artifactId = text(xpath, dependency, "*[local-name()='artifactId']");
                String scope = normalizeScope(text(xpath, dependency, "*[local-name()='scope']"));

                if ("logback-classic".equals(artifactId) && ("compile".equals(scope) || "runtime".equals(scope))) {
                    exposedDependencies.add("logback-classic(scope=" + scope + ")");
                }
            }

            return exposedDependencies;
        } catch (IOException
                 | ParserConfigurationException
                 | SAXException
                 | XPathExpressionException exception) {
            throw new AssertionError("Failed to parse " + pom, exception);
        }
    }

    private static String text(XPath xpath, Node node, String expression) throws XPathExpressionException {
        return xpath.evaluate(expression, node).trim();
    }

    private static String normalizeScope(String scope) {
        if (scope.isBlank()) {
            return "compile";
        }

        return scope;
    }

    private static boolean containsLogbackReference(Path path) {
        try {
            return Files.readString(path).contains("ch.qos.logback");
        } catch (IOException exception) {
            throw new AssertionError("Failed to read " + path, exception);
        }
    }
}
