package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code WebFetchWebpageTool} in {@code openjiuwen.harness.tools.web_tools}.
 */
public class WebFetchWebpageTool extends AbstractHarnessTool {

    private static final int DEFAULT_MAX_CHARS = 20_000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 45;
    private static final Pattern CHARSET_HEADER = Pattern.compile("charset=([^\\s;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_META = Pattern.compile(
            "<meta[^>]+charset=[\"']?\\s*([A-Za-z0-9._-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> MOJIBAKE_MARKERS = List.of("mojibake", "Ã", "Â", "â", "ï¿½");

    private final String language;

    public WebFetchWebpageTool() {
        this("cn");
    }

    public WebFetchWebpageTool(String language) {
        super(toolCard("fetch_webpage", "fetch_webpage", "Fetch webpage text content from a URL."), null);
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String url = normalizeUrl(String.valueOf(inputs.getOrDefault("url", "")));
        int maxChars = parseInt(inputs.get("max_chars"), DEFAULT_MAX_CHARS);
        int timeoutSeconds = Math.max(5, parseInt(inputs.get("timeout_seconds"), DEFAULT_TIMEOUT_SECONDS));
        if (url.isBlank()) {
            return new ToolOutput(false, null, "[ERROR]: url cannot be empty.");
        }
        try {
            FetchedPage page = fetchWebpage(url, timeoutSeconds);
            String clippedContent = clipText(page.content(), maxChars);
            String text = render(page, clippedContent);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", page.url());
            data.put("status_code", page.statusCode());
            data.put("title", page.title());
            data.put("content", clippedContent);
            data.put("language", language);
            data.put("text", text);
            return new ToolOutput(true, data, null);
        } catch (Exception e) {
            return new ToolOutput(false, null, "[ERROR]: failed to fetch webpage: " + e.getMessage());
        }
    }

    protected FetchedPage fetchWebpage(String url, int timeoutSeconds) {
        FetchResponse response = httpGet(url, timeoutSeconds);
        if (List.of(401, 403, 429).contains(response.statusCode())) {
            response = httpGet("https://r.jina.ai/" + url, timeoutSeconds);
        }
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP status: " + response.statusCode());
        }

        String text = decodeResponseText(
                response.content(),
                response.headers(),
                response.encoding(),
                response.apparentEncoding()
        );
        String contentType = header(response.headers(), "Content-Type").toLowerCase(Locale.ROOT);
        String title = "";
        if (contentType.contains("html")) {
            ExtractedContent extracted = extractMainTextFromHtml(text);
            title = extracted.title();
            text = extracted.content();
        } else {
            text = text.replaceAll("\\s+", " ").trim();
        }
        return new FetchedPage(response.url(), response.statusCode(), title, text);
    }

    protected FetchResponse httpGet(String url, int timeoutSeconds) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(timeoutSeconds * 1_000);
            connection.setReadTimeout(timeoutSeconds * 1_000);
            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] content = stream == null ? new byte[0] : stream.readAllBytes();
            Map<String, String> headers = new LinkedHashMap<>();
            String contentType = connection.getContentType();
            if (contentType != null) {
                headers.put("Content-Type", contentType);
            }
            return new FetchResponse(
                    connection.getURL().toString(),
                    statusCode,
                    headers,
                    content,
                    connection.getContentEncoding(),
                    null
            );
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    static String decodeResponseText(
            byte[] raw,
            Map<String, String> headers,
            String responseEncoding,
            String apparentEncoding
    ) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        List<String> candidates = new java.util.ArrayList<>();
        String declared = extractDeclaredCharset(raw, headers);
        if (!declared.isBlank() && !List.of("iso-8859-1", "latin-1", "latin1").contains(declared.toLowerCase(Locale.ROOT))) {
            candidates.add(declared);
        }
        candidates.add("utf-8-sig");
        candidates.add("utf-8");
        candidates.add(apparentEncoding);
        candidates.add(responseEncoding);
        candidates.add("gbk");
        candidates.add("gb18030");
        candidates.add("big5");
        candidates.add("shift_jis");
        candidates.add("cp1252");
        candidates.add("iso-8859-1");

        double bestScore = Double.NEGATIVE_INFINITY;
        String bestText = null;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String candidate : candidates) {
            String encoding = candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT);
            if (encoding.isBlank() || !seen.add(encoding)) {
                continue;
            }
            if ("utf-8-sig".equals(encoding)) {
                encoding = "utf-8";
            }
            try {
                String text = new String(raw, Charset.forName(encoding));
                double score = scoreDecodedText(text);
                if (score > bestScore) {
                    bestScore = score;
                    bestText = text;
                }
            } catch (RuntimeException ignored) {
                // Try the next candidate.
            }
        }
        if (bestText != null) {
            return bestText.replace("\uFEFF", "");
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    static ExtractedContent extractMainTextFromHtml(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        Element titleElement = document.selectFirst("title");
        String title = titleElement == null ? "" : titleElement.text().trim();

        document.select("script,style,noscript,svg,canvas,iframe").remove();
        document.select("nav,header,footer,aside,form,button,[role=navigation],.nav,.navbar,.header,.footer,.sidebar,.aside,.recommend,.related,.share,.breadcrumb,.menu,.toolbar").remove();

        String bestText = "";
        for (String selector : List.of(
                "main",
                "[role=main]",
                "article",
                ".article",
                ".article-content",
                ".article-body",
                ".post",
                ".post-content",
                ".entry-content",
                ".content",
                ".detail",
                ".news",
                "#content",
                "#main"
        )) {
            Element node = document.selectFirst(selector);
            if (node != null && node.text().length() > bestText.length()) {
                bestText = node.text();
            }
        }
        if (bestText.isBlank()) {
            bestText = document.body() == null ? document.text() : document.body().text();
        }
        return new ExtractedContent(title, bestText.replaceAll("\\s{2,}", " ").trim());
    }

    static String clipText(String value, int maxChars) {
        String text = value == null ? "" : value;
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }

    private static String render(FetchedPage page, String content) {
        StringBuilder builder = new StringBuilder()
                .append("URL: ").append(page.url())
                .append("\nStatus: ").append(page.statusCode());
        if (!page.title().isBlank()) {
            builder.append("\nTitle: ").append(page.title());
        }
        builder.append("\nContent:\n").append(content.isBlank() ? "[empty]" : content);
        return builder.toString();
    }

    private static String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return "";
        }
        String decoded = decodeDdgRedirect(value);
        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
            return decoded;
        }
        return "https://" + decoded;
    }

    private static String decodeDdgRedirect(String href) {
        int idx = href.indexOf("uddg=");
        if (idx < 0) {
            return href;
        }
        String encoded = href.substring(idx + 5);
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        try {
            return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return href;
        }
    }

    private static String extractDeclaredCharset(byte[] raw, Map<String, String> headers) {
        String contentType = header(headers, "Content-Type");
        Matcher headerMatcher = CHARSET_HEADER.matcher(contentType);
        if (headerMatcher.find()) {
            return headerMatcher.group(1).trim().replace("\"", "").replace("'", "");
        }
        String head = new String(raw, 0, Math.min(raw.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher metaMatcher = CHARSET_META.matcher(head);
        return metaMatcher.find() ? metaMatcher.group(1).trim() : "";
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    private static double scoreDecodedText(String value) {
        if (value == null || value.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = 0.0;
        for (char ch : value.toCharArray()) {
            if (ch == '\uFFFD') {
                score -= 8.0;
            }
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                score += 0.15;
            } else if (Character.isLetterOrDigit(ch)) {
                score += 0.02;
            } else if (Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                score -= 5.0;
            }
        }
        for (String marker : MOJIBAKE_MARKERS) {
            score -= countOccurrences(value, marker) * 3.0;
        }
        return score;
    }

    private static int countOccurrences(String value, String marker) {
        int count = 0;
        int offset = 0;
        while (!marker.isEmpty() && (offset = value.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private static int parseInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", getCard().toString());
    }

    protected record FetchResponse(
            String url,
            int statusCode,
            Map<String, String> headers,
            byte[] content,
            String encoding,
            String apparentEncoding
    ) {
    }

    protected record FetchedPage(String url, int statusCode, String title, String content) {
    }

    record ExtractedContent(String title, String content) {
    }
}
