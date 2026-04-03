import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.dev_tools.skill_creator.SkillCreator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Java version of the Python examples/skill_create example.
 */
public final class SkillCreateExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_CONVERSATION_ID = "013";
    private static final String DEFAULT_PDF_URL = "http://viewer.media.bitpipe.com/1253203751_753/1284482743_310/11_Best_Practices_for_Peer_Code_Review.pdf";

    private SkillCreateExample() {
    }

    public static void main(String[] args) throws Exception {
        Path filesBaseDir = resolvePathConfig("FILES_BASE_DIR", Path.of("examples", "skill_create", "data"));
        Path outputDir = resolvePathConfig("OUTPUT_DIR", Path.of("examples", "skill_create", "output"));
        String pdfUrl = resolvePdfUrl(args);

        Files.createDirectories(filesBaseDir);
        Files.createDirectories(outputDir);

        Path pdfPath = downloadPdf(pdfUrl, filesBaseDir);
        Path markdownPath = writeMarkdownFromPdf(pdfPath, pdfUrl);
        configureSkillCreatorProperties(filesBaseDir);

        SkillCreator skillCreator = new SkillCreator();
        try {
            skillCreator.createAgent().join();
            Object result = skillCreator.generate(
                    "Create a skill based on the file " + markdownPath + ".",
                    outputDir
            ).join();

            System.out.println("PDF downloaded to:");
            System.out.println(pdfPath.toAbsolutePath().normalize());
            System.out.println("Markdown saved to:");
            System.out.println(markdownPath.toAbsolutePath().normalize());
            System.out.println("SkillCreator result:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } finally {
            Runner.release(DEFAULT_CONVERSATION_ID);
            Runner.stop();
        }
    }

    private static void configureSkillCreatorProperties(Path filesBaseDir) {
        Map<String, String> config = SharedExampleApiConfigLoader.load();
        System.setProperty("API_BASE", config.getOrDefault("API_BASE", ""));
        System.setProperty("API_KEY", config.getOrDefault("API_KEY", ""));
        System.setProperty("MODEL_PROVIDER", config.getOrDefault("MODEL_PROVIDER", ""));
        System.setProperty("MODEL_NAME", config.getOrDefault("MODEL_NAME", ""));
        System.setProperty("LLM_SSL_VERIFY", config.getOrDefault("LLM_SSL_VERIFY", "false"));
        System.setProperty("FILES_BASE_DIR", filesBaseDir.toAbsolutePath().normalize().toString());
        System.setProperty(
                "SKILLS_DIR",
                Path.of("src", "main", "resources", "openjiuwen", "dev_tools", "skill_creator", "skills")
                        .toAbsolutePath()
                        .normalize()
                        .toString()
        );
    }

    private static Path downloadPdf(String pdfUrl, Path filesBaseDir) throws IOException, InterruptedException {
        URI uri = URI.create(pdfUrl);
        String fileName = deriveFileName(uri);
        Path pdfPath = filesBaseDir.resolve(fileName);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to download PDF. HTTP status: " + response.statusCode());
        }

        Files.write(pdfPath, response.body());
        return pdfPath;
    }

    private static Path writeMarkdownFromPdf(Path pdfPath, String pdfUrl) throws IOException {
        PDFParser parser = new PDFParser();
        List<Document> documents = parser.parse(pdfPath.toString(), "skill_create_pdf_source", null, Map.of());
        if (documents.isEmpty() || documents.get(0).getText().isBlank()) {
            throw new IOException("Failed to extract text from PDF: " + pdfPath);
        }

        String title = stripPdfExtension(pdfPath.getFileName().toString());
        String markdown = "# " + title + "\n\n"
                + "Source URL: " + pdfUrl + "\n\n"
                + "Source PDF: " + pdfPath.toAbsolutePath().normalize() + "\n\n"
                + documents.get(0).getText().trim() + "\n";

        Path markdownPath = pdfPath.resolveSibling(title + ".md");
        Files.writeString(markdownPath, markdown, StandardCharsets.UTF_8);
        return markdownPath;
    }

    private static String resolvePdfUrl(String[] args) {
        if (args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return args[0];
        }
        return resolveStringConfig("SKILL_CREATE_PDF_URL", DEFAULT_PDF_URL);
    }

    private static Path resolvePathConfig(String key, Path defaultPath) {
        String configured = resolveStringConfig(key, "");
        Path path = configured.isBlank() ? defaultPath : Path.of(configured);
        return path.toAbsolutePath().normalize();
    }

    private static String resolveStringConfig(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return defaultValue;
    }

    private static String deriveFileName(URI uri) {
        String path = uri.getPath();
        String candidate = "";
        if (path != null && !path.isBlank()) {
            Path uriPath = Path.of(path);
            Path fileName = uriPath.getFileName();
            if (fileName != null) {
                candidate = fileName.toString();
            }
        }
        if (candidate.isBlank()) {
            candidate = "skill_source.pdf";
        }
        if (!candidate.toLowerCase().endsWith(".pdf")) {
            candidate = candidate + ".pdf";
        }
        return candidate.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String stripPdfExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}