/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 1 scraper that normalizes page content into ordered blocks.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/skill_creator/skills/skill_omni_creation/scripts/stage_01_scrape.py}.
 */
public final class Stage01Scrape {

    static final Set<String> UTILITY_PATHS = Set.of(
            "/login", "/signin", "/signup", "/register", "/logout",
            "/privacy", "/terms", "/tos", "/cookies", "/legal",
            "/about", "/contact", "/faq", "/help", "/support", "/careers",
            "/cart", "/checkout", "/payment", "/subscribe",
            "/search", "/sitemap", "/404", "/403"
    );

    static final Set<String> AD_DOMAINS = Set.of(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagmanager.com", "google-analytics.com",
            "adnxs.com", "criteo.com", "criteo.net", "outbrain.com", "taboola.com",
            "moatads.com", "rubiconproject.com", "pubmatic.com", "openx.net",
            "scorecardresearch.com", "quantserve.com", "hotjar.com",
            "facebook.com", "connect.facebook.net", "cookielaw.org", "onetrust.com"
    );

    static final Set<String> AD_PATH_KEYWORDS = Set.of(
            "/ads/", "/ad/", "/banner/", "/banners/", "/tracking/", "/pixel/",
            "/beacon/", "/analytics/", "/telemetry/", "/sponsored/", "/promo/"
    );

    static final List<Pattern> PLATFORM_PATTERNS = List.of(
            Pattern.compile("youtube\\.com/watch"),
            Pattern.compile("youtu\\.be/"),
            Pattern.compile("bilibili\\.com/video"),
            Pattern.compile("vimeo\\.com/\\d+"),
            Pattern.compile("twitter\\.com/.+/status"),
            Pattern.compile("x\\.com/.+/status")
    );

    static final Set<String> NOISE_IDS = Set.of(
            "onetrust-consent-sdk", "onetrust-banner-sdk", "onetrust-pc-sdk",
            "cookie-law-info-bar", "gdpr-cookie-notice", "CybotCookiebotDialog"
    );

    static final Set<String> NOISE_TABPANEL_LABELS = Set.of(
            "discover", "community", "contact us", "windows insiders",
            "related resources", "more resources"
    );

    static final Set<String> NOISE_CLASSES = Set.of(
            "uhf", "c-uhfh", "c-footer", "c-nav", "breadcrumb", "feedback", "social", "c-heading-4", "ocr"
    );

    private static final Pattern YOUTUBE_EMBED = Pattern.compile("youtube\\.com/embed/([A-Za-z0-9_-]+)");
    private static final Pattern BILIBILI_BV = Pattern.compile("bilibili\\.com/video/(BV[A-Za-z0-9]+)");
    private static final Pattern BILIBILI_AV = Pattern.compile("aid=(\\d+)");
    private static final Pattern VIMEO = Pattern.compile("player\\.vimeo\\.com/video/(\\d+)");

    private Stage01Scrape() {
    }

    public interface BrowserPage {
        PageResponse gotoUrl(String url) throws Exception;

        default void waitForTimeout(long millis) throws Exception {
        }

        default void evaluateScrollToBottom() throws Exception {
        }

        default String content() throws Exception {
            return "";
        }

        default List<String> collectLinks(String selector) throws Exception {
            return List.of();
        }
    }

    public record PageResponse(int status) {
    }

    public record TabpanelInfo(String panelId, String tabLabel) {
    }

    public record ScrapeResult(String html, List<String> videoUrls, List<String> subpageLinks) {
    }

    public record PageBlocks(List<Map<String, Object>> blocks, List<String> videoUrls, String pageTitle) {
    }

    public static boolean isUtilityUrl(String url) {
        try {
            String path = new URI(url).getPath().toLowerCase().replaceAll("/+$", "");
            for (String utilityPath : UTILITY_PATHS) {
                if (path.equals(utilityPath) || path.startsWith(utilityPath + "/")) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isAdUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = safe(uri.getHost()).toLowerCase().replaceFirst("^www\\.", "");
            String[] parts = host.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                if (AD_DOMAINS.contains(String.join(".", java.util.Arrays.copyOfRange(parts, i, parts.length)))) {
                    return true;
                }
            }
            String path = safe(uri.getPath()).toLowerCase();
            return AD_PATH_KEYWORDS.stream().anyMatch(path::contains);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isPlatformUrl(String url) {
        return PLATFORM_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(url).find());
    }

    public static String elText(Element element) {
        return element == null ? "" : element.text().trim();
    }

    public static boolean isContentImg(Element img) {
        String src = firstNonBlank(img.attr("src"), img.attr("data-src"), img.attr("data-lazy-src"));
        return !src.isBlank() && !src.startsWith("data:") && !src.endsWith(".svg");
    }

    public static String bestImgUrl(Element img, String pageUrl) {
        String srcset = img.attr("srcset");
        if (!srcset.isBlank()) {
            String[] entries = srcset.split(",");
            for (int i = entries.length - 1; i >= 0; i--) {
                String candidate = entries[i].trim().split("\\s+")[0];
                if (!candidate.isBlank()) {
                    return resolveUrl(pageUrl, candidate);
                }
            }
        }
        for (String attr : List.of("src", "data-src", "data-lazy-src")) {
            String value = img.attr(attr);
            if (!value.isBlank() && !value.startsWith("data:")) {
                return resolveUrl(pageUrl, value);
            }
        }
        return "";
    }

    public static Map<String, String> buildTabpanelLabels(Element root) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Element tab : root.select("[role=tab]")) {
            String label = elText(tab);
            if (label.isBlank()) {
                continue;
            }
            String controls = tab.attr("aria-controls");
            if (!controls.isBlank()) {
                labels.put(controls, label);
            }
        }
        for (Element panel : root.select("[role=tabpanel]")) {
            String panelId = panel.id();
            if (panelId.isBlank() || labels.containsKey(panelId)) {
                continue;
            }
            String labelledBy = panel.attr("aria-labelledby");
            if (!labelledBy.isBlank()) {
                Element tab = root.getElementById(labelledBy);
                if (tab != null) {
                    labels.put(panelId, elText(tab));
                }
            }
        }
        return labels;
    }

    public static TabpanelInfo tabpanelInfo(Element element, Element root, Map<String, String> labels) {
        Element parent = element.parent();
        while (parent != null && parent != root) {
            if ("tabpanel".equals(parent.attr("role"))) {
                String panelId = parent.id();
                return new TabpanelInfo(panelId, labels.getOrDefault(panelId, ""));
            }
            parent = parent.parent();
        }
        return new TabpanelInfo("", "");
    }

    public static List<Map<String, Object>> buildBlocks(Document document, String pageUrl, String source) {
        Element root = firstNonNull(
                document.selectFirst("main"),
                document.selectFirst("[role=main]"),
                document.selectFirst("article"),
                document.body(),
                document
        );
        Map<String, String> tabpanelLabels = buildTabpanelLabels(root);
        Set<String> injectedPanels = new LinkedHashSet<>();
        Set<String> seenText = new LinkedHashSet<>();
        List<Map<String, Object>> blocks = new ArrayList<>();

        for (Element element : root.select("h1,h2,h3,h4,p,li,img")) {
            TabpanelInfo info = tabpanelInfo(element, root, tabpanelLabels);
            if (!info.tabLabel().isBlank() && NOISE_TABPANEL_LABELS.contains(info.tabLabel().toLowerCase())) {
                continue;
            }
            if (!info.panelId().isBlank() && !injectedPanels.contains(info.panelId())) {
                injectedPanels.add(info.panelId());
                if (!info.tabLabel().isBlank()) {
                    blocks.add(headingBlock(2, info.tabLabel(), source));
                }
            }
            String classes = String.join(" ", element.classNames());
            if (NOISE_CLASSES.stream().anyMatch(classes::contains)) {
                continue;
            }

            switch (element.tagName()) {
                case "h1", "h2", "h3", "h4" -> {
                    String text = elText(element);
                    if (!text.isBlank() && seenText.add(text)) {
                        blocks.add(headingBlock(Integer.parseInt(element.tagName().substring(1)), text, source));
                    }
                }
                case "img" -> {
                    if (!isContentImg(element)) {
                        continue;
                    }
                    String url = bestImgUrl(element, pageUrl);
                    if (url.isBlank()) {
                        continue;
                    }
                    String alt = element.attr("alt").trim();
                    if (alt.isBlank()) {
                        alt = resolveRemoteReference(element, document);
                    }
                    blocks.add(imageBlock(url, alt, source));
                }
                default -> {
                    String text = elText(element);
                    if (text.length() > 15 && seenText.add(text)) {
                        blocks.add(textBlock(text.substring(0, Math.min(text.length(), 400)), source));
                    }
                }
            }
        }
        return blocks;
    }

    public static List<Map<String, Object>> parsePageHtml(String html, String pageUrl, String source) {
        Document document = Jsoup.parse(html);
        for (String noiseId : NOISE_IDS) {
            Element element = document.getElementById(noiseId);
            if (element != null) {
                element.remove();
            }
        }
        return buildBlocks(document, pageUrl, source);
    }

    public static List<String> detectVideoUrlsFromHtml(String html) {
        LinkedHashSet<String> videos = new LinkedHashSet<>();
        collectMatches(videos, YOUTUBE_EMBED, html, id -> "https://www.youtube.com/watch?v=" + id);
        collectMatches(videos, BILIBILI_BV, html, id -> "https://www.bilibili.com/video/" + id);
        collectMatches(videos, BILIBILI_AV, html, id -> "https://www.bilibili.com/video/av" + id);
        collectMatches(videos, VIMEO, html, id -> "https://vimeo.com/" + id);
        return new ArrayList<>(videos);
    }

    public static ScrapeResult scrapeOnePage(BrowserPage page, String pageUrl) {
        return scrapeOnePage(page, pageUrl, false);
    }

    public static ScrapeResult scrapeOnePage(BrowserPage page, String pageUrl, boolean dismissCookie) {
        try {
            PageResponse response = page.gotoUrl(pageUrl);
            if (response != null && response.status() >= 400) {
                return new ScrapeResult("", List.of(), List.of());
            }
            page.waitForTimeout(1500L);
            if (dismissCookie) {
                page.waitForTimeout(0L);
            }
            page.evaluateScrollToBottom();
            page.waitForTimeout(1500L);
            String html = page.content();
            List<String> links = page.collectLinks("main a[href], article a[href], [role='main'] a[href], .content a[href]");
            if (links == null || links.isEmpty()) {
                links = page.collectLinks("a[href]");
            }
            String baseDomain = safeHost(pageUrl);
            LinkedHashSet<String> subpages = new LinkedHashSet<>();
            for (String link : links) {
                if (safeHost(link).equals(baseDomain) && !link.equals(pageUrl) && !isUtilityUrl(link) && !isAdUrl(link)) {
                    subpages.add(link);
                }
            }
            return new ScrapeResult(html, detectVideoUrlsFromHtml(html), new ArrayList<>(subpages));
        } catch (Exception ignored) {
            return new ScrapeResult("", List.of(), List.of());
        }
    }

    public static PageBlocks scrapePage(String url, int maxSubpages) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", SkillOmniCommon.STEALTH_UA)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String html = response.statusCode() >= 400 ? "" : response.body();
        Document document = Jsoup.parse(html, url);
        List<Map<String, Object>> blocks = parsePageHtml(html, url, "main");
        List<String> videos = detectVideoUrlsFromHtml(html);
        if (isPlatformUrl(url) && !videos.contains(url)) {
            videos.add(0, url);
        }

        int processed = 0;
        for (Element link : document.select("main a[href], article a[href], [role=main] a[href], .content a[href]")) {
            if (processed >= maxSubpages) {
                break;
            }
            String target = link.absUrl("href");
            if (target.isBlank() || !safeHost(target).equals(safeHost(url)) || isUtilityUrl(target) || isAdUrl(target)) {
                continue;
            }
            try {
                HttpResponse<String> subResponse = client.send(
                        HttpRequest.newBuilder().uri(URI.create(target)).header("User-Agent", SkillOmniCommon.STEALTH_UA).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (subResponse.statusCode() < 400) {
                    blocks.addAll(parsePageHtml(subResponse.body(), target, "subpage"));
                    videos.addAll(detectVideoUrlsFromHtml(subResponse.body()));
                    processed++;
                }
            } catch (Exception ignored) {
                // Skip broken subpages and preserve the best-effort scraping behavior.
            }
        }

        return new PageBlocks(deduplicateImages(blocks), new ArrayList<>(new LinkedHashSet<>(videos)), document.title());
    }

    public static PageBlocks scrapePageViaLlm(SkillOmniCommon.ChatClient client, String url)
            throws IOException, InterruptedException {
        String content = client.chat(
                SkillOmniCommon.SCRAPE_FALLBACK_PROMPT,
                "Fetch and parse this page: " + url,
                0.0,
                6000,
                Map.of("plugins", List.of(Map.of("id", "web", "max_results", 1)))
        );
        Map<String, Object> payload = SkillOmniCommon.fromJson(SkillOmniCommon.stripJsonFence(content));
        List<Map<String, Object>> rawBlocks = castBlocks(payload.get("blocks"));
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Map<String, Object> block : rawBlocks) {
            Map<String, Object> copy = new LinkedHashMap<>(block);
            copy.putIfAbsent("source", "main");
            copy.putIfAbsent("path", null);
            blocks.add(copy);
        }
        return new PageBlocks(
                blocks,
                castStringList(payload.getOrDefault("video_urls", List.of())),
                String.valueOf(payload.getOrDefault("title", ""))
        );
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("url is required");
        }
        String url = args[0];
        String slug = args.length > 1 ? args[1] : SkillOmniCommon.urlToSlug(url);
        Path out = args.length > 2 ? Path.of(args[2]) : SkillOmniCommon.workPath(slug, "stage_01_scrape.json");
        PageBlocks result = scrapePage(url, 5);
        SkillOmniCommon.writeJson(out, Map.of(
                "url", url,
                "slug", slug,
                "title", result.pageTitle(),
                "blocks", result.blocks(),
                "video_urls", result.videoUrls()
        ));
    }

    private static List<Map<String, Object>> deduplicateImages(List<Map<String, Object>> blocks) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (Map<String, Object> block : blocks) {
            if ("image".equals(block.get("type"))) {
                String url = String.valueOf(block.get("url"));
                if (!seenUrls.add(url)) {
                    continue;
                }
            }
            result.add(block);
        }
        return result;
    }

    private static void collectMatches(LinkedHashSet<String> videos, Pattern pattern, String html,
                                       java.util.function.Function<String, String> mapper) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            videos.add(mapper.apply(matcher.group(1)));
        }
    }

    private static String resolveRemoteReference(Element img, Document document) {
        for (String attr : List.of("aria-describedby", "aria-labelledby")) {
            String ref = img.attr(attr).trim();
            if (!ref.isBlank()) {
                Element target = document.getElementById(ref);
                if (target != null) {
                    return elText(target);
                }
            }
        }
        Element figure = img.parent();
        while (figure != null && !"figure".equals(figure.tagName())) {
            figure = figure.parent();
        }
        if (figure != null) {
            Element caption = figure.selectFirst("figcaption");
            if (caption != null) {
                return elText(caption);
            }
        }
        return img.attr("title").trim();
    }

    private static Map<String, Object> headingBlock(int level, String text, String source) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "heading");
        block.put("level", level);
        block.put("text", text);
        block.put("source", source);
        return block;
    }

    private static Map<String, Object> textBlock(String text, String source) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text);
        block.put("source", source);
        return block;
    }

    private static Map<String, Object> imageBlock(String url, String alt, String source) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "image");
        block.put("url", url);
        block.put("alt", alt);
        block.put("source", source);
        block.put("path", null);
        return block;
    }

    private static String resolveUrl(String baseUrl, String path) {
        try {
            return new URI(baseUrl).resolve(path).toString();
        } catch (URISyntaxException exception) {
            return path;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String safeHost(String url) {
        try {
            return safe(new URI(url).getHost());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castBlocks(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object value) {
        return value == null ? List.of() : (List<String>) value;
    }
}
