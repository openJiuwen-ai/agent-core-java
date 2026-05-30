/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.output_parser;

import com.openjiuwen.core.foundation.llm.output_parsers.MarkdownContent;
import com.openjiuwen.core.foundation.llm.output_parsers.MarkdownElement;
import com.openjiuwen.core.foundation.llm.output_parsers.MarkdownElementType;
import com.openjiuwen.core.foundation.llm.output_parsers.MarkdownOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for markdown output parser.
 * <p>
 * Mirrors Python's {@code test_markdown_output_parser.py} from
 * {@code tests/unit_tests/core/foundation/output_parser/test_markdown_output_parser.py}.
 * Tests parsing structured output from markdown-formatted responses.
 */
class TestMarkdownOutputParser {

    private final MarkdownOutputParser parser = new MarkdownOutputParser();

    private MarkdownContent parse(Object input) {
        return (MarkdownContent) parser.parse(input);
    }

    private List<MarkdownContent> streamParse(List<?> chunks) {
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add((MarkdownContent) iterator.next());
        }
        return parsedObjects;
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Pattern matching basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testPatternClassExists() {
        assertNotNull(Pattern.class);
    }

    @Test
    @Tag("level0")
    void testMatcherClassExists() {
        assertNotNull(Matcher.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Markdown code block detection)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testCodeBlockPattern() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```(\\w+)?\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        assertTrue(matcher.find());
    }

    @Test
    @Tag("level1")
    void testJsonCodeBlockLanguage() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```(\\w+)");
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            assertEquals("json", matcher.group(1));
        }
    }

    @Test
    @Tag("level1")
    void testCodeBlockContentExtraction() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```\\w*\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            assertTrue(matcher.group(1).contains("{\"key\": \"value\"}"));
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Multiple code blocks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testMultipleCodeBlocks() {
        String markdown = "```python\nprint('hello')\n```\n\n```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```\\w+\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    @Tag("level2")
    void testNoCodeBlockInPlainText() {
        String plainText = "This is just plain text without any code blocks.";
        Pattern pattern = Pattern.compile("```");
        Matcher matcher = pattern.matcher(plainText);
        assertFalse(matcher.find());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Markdown header parsing)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testHeaderPattern() {
        String markdown = "# Header 1\n## Header 2\n### Header 3";
        Pattern pattern = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    @Tag("level3")
    void testHeaderLevelDetection() {
        String header = "## This is a level 2 header";
        Pattern pattern = Pattern.compile("^#{1,6}");
        Matcher matcher = pattern.matcher(header);
        if (matcher.find()) {
            assertEquals(2, matcher.group().length());
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (List parsing)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testBulletListPattern() {
        String markdown = "- Item 1\n- Item 2\n- Item 3";
        Pattern pattern = Pattern.compile("^-\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    @Tag("level4")
    void testNumberedListPattern() {
        String markdown = "1. First\n2. Second\n3. Third";
        Pattern pattern = Pattern.compile("^\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    @Tag("level1")
    void testParseSimpleMarkdown() {
        String markdownText = """
                # Main Title

                This is a paragraph with some **bold** text.

                ## Subtitle

                Here's a code block:
                ```python
                def hello():
                    print("Hello, World!")
                ```

                And a link: [OpenAI](https://openai.com)
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertEquals(2, result.getHeaders().size());
        assertEquals("Main Title", result.getHeaders().get(0).get("title"));
        assertEquals("1", result.getHeaders().get(0).get("level"));
        assertEquals("Subtitle", result.getHeaders().get(1).get("title"));
        assertEquals("2", result.getHeaders().get(1).get("level"));
        assertEquals(1, result.getCodeBlocks().size());
        assertEquals("python", result.getCodeBlocks().get(0).get("language"));
        assertTrue(String.valueOf(result.getCodeBlocks().get(0).get("code")).contains("def hello():"));
        assertEquals(1, result.getLinks().size());
        assertEquals("OpenAI", result.getLinks().get(0).get("text"));
        assertEquals("https://openai.com", result.getLinks().get(0).get("url"));
    }

    @Test
    @Tag("level1")
    void testParseAssistantMessageMarkdown() {
        AssistantMessage aiMessage = new AssistantMessage("""
                ## Analysis Results

                The data shows:
                - Item 1: Important finding
                - Item 2: Another insight

                ![Chart](https://example.com/chart.png)
                """);
        MarkdownContent result = parse(aiMessage);

        assertNotNull(result);
        assertEquals(1, result.getHeaders().size());
        assertEquals("Analysis Results", result.getHeaders().get(0).get("title"));
        assertEquals(1, result.getImages().size());
        assertEquals("Chart", result.getImages().get(0).get("alt"));
        assertEquals("https://example.com/chart.png", result.getImages().get(0).get("url"));
        assertEquals(1, result.getLists().size());
        assertTrue(result.getLists().get(0).contains("Item 1: Important finding"));
    }

    @Test
    @Tag("level1")
    void testParseCodeBlocks() {
        String markdownText = """
                Here are some code examples:

                ```javascript
                console.log("Hello");
                ```

                ```sql
                SELECT * FROM users;
                ```

                And inline code: `print("test")` in the text.
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertEquals(3, result.getCodeBlocks().size());
        Map<String, Object> jsBlock = result.getCodeBlocks().stream()
                .filter(block -> "javascript".equals(block.get("language")))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(jsBlock.get("code")).contains("console.log"));
        Map<String, Object> sqlBlock = result.getCodeBlocks().stream()
                .filter(block -> "sql".equals(block.get("language")))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(sqlBlock.get("code")).contains("SELECT"));
        Map<String, Object> inlineBlock = result.getCodeBlocks().stream()
                .filter(block -> "inline".equals(block.get("language")))
                .findFirst()
                .orElseThrow();
        assertEquals("print(\"test\")", inlineBlock.get("code"));
    }

    @Test
    @Tag("level1")
    void testParseTables() {
        String markdownText = """
                Here's a data table:

                | Name | Age | City |
                |------|-----|------|
                | Alice | 30 | NYC |
                | Bob | 25 | LA |

                And another table:

                | Product | Price |
                |---------|-------|
                | Apple | $1.00 |
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertEquals(2, result.getTables().size());
        assertTrue(result.getTables().get(0).contains("Alice"));
        assertTrue(result.getTables().get(0).contains("Bob"));
        assertTrue(result.getTables().get(1).contains("Apple"));
    }

    @Test
    @Tag("level1")
    void testParseLists() {
        String markdownText = """
                Shopping list:
                - Milk
                - Bread
                - Eggs

                Todo items:
                1. Review code
                2. Write tests
                3. Deploy

                Another list:
                * Item A
                * Item B
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertEquals(3, result.getLists().size());
        assertTrue(result.getLists().get(0).contains("Milk"));
        assertTrue(result.getLists().get(0).contains("Bread"));
        assertTrue(result.getLists().get(1).contains("1. Review code"));
        assertTrue(result.getLists().get(1).contains("2. Write tests"));
        assertTrue(result.getLists().get(2).contains("Item A"));
    }

    @Test
    @Tag("level1")
    void testParseLinksAndImages() {
        String markdownText = """
                Check out these resources:

                [GitHub](https://github.com)
                [Documentation](https://docs.example.com)

                Here are some images:
                ![Logo](https://example.com/logo.png)
                ![Screenshot](https://example.com/screen.jpg)
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertEquals(2, result.getLinks().size());
        assertEquals("GitHub", result.getLinks().get(0).get("text"));
        assertEquals("https://github.com", result.getLinks().get(0).get("url"));
        assertEquals(2, result.getImages().size());
        assertEquals("Logo", result.getImages().get(0).get("alt"));
        assertEquals("https://example.com/logo.png", result.getImages().get(0).get("url"));
    }

    @Test
    @Tag("level1")
    void testParseEmptyContent() {
        assertNull(parse(""));
        assertNull(parse(null));
    }

    @Test
    @Tag("level1")
    void testParsePlainText() {
        String plainText = "This is just plain text without any markdown formatting.";
        MarkdownContent result = parse(plainText);

        assertNotNull(result);
        assertEquals(plainText, result.getRawContent());
        assertEquals(0, result.getHeaders().size());
        assertEquals(0, result.getCodeBlocks().size());
        assertEquals(0, result.getLinks().size());
    }

    @Test
    @Tag("level2")
    void testStreamParseMarkdownChunks() {
        List<String> chunks = List.of(
                "# Title\n\n",
                "This is a paragraph.\n\n",
                "```python\n",
                "print('hello')\n",
                "```\n\n",
                "[Link](https://example.com)");

        List<MarkdownContent> parsedObjects = streamParse(chunks);

        assertFalse(parsedObjects.isEmpty());
        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);
        assertEquals(1, finalResult.getHeaders().size());
        assertEquals("Title", finalResult.getHeaders().get(0).get("title"));
        assertEquals(1, finalResult.getCodeBlocks().size());
        assertEquals("python", finalResult.getCodeBlocks().get(0).get("language"));
        assertEquals(1, finalResult.getLinks().size());
        assertEquals("Link", finalResult.getLinks().get(0).get("text"));
    }

    @Test
    @Tag("level2")
    void testStreamParseFragmentedMarkdown() {
        List<String> chunks = List.of(
                "## Sect",
                "ion Title\n\n",
                "Here's a co",
                "de block:\n```js\ncons",
                "ole.log('test');\n```\n\n",
                "And a [li",
                "nk](https://test.com)");

        List<MarkdownContent> parsedObjects = streamParse(chunks);
        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);

        assertEquals(1, finalResult.getHeaders().size());
        assertEquals("Section Title", finalResult.getHeaders().get(0).get("title"));
        assertEquals(1, finalResult.getCodeBlocks().size());
        assertEquals("js", finalResult.getCodeBlocks().get(0).get("language"));
        assertEquals(1, finalResult.getLinks().size());
    }

    @Test
    @Tag("level2")
    void testStreamParseAssistantMessageChunks() {
        List<AssistantMessageChunk> chunks = List.of(
                AssistantMessageChunk.builder().content("# Report\n\n").build(),
                AssistantMessageChunk.builder().content("## Summary\n").build(),
                AssistantMessageChunk.builder().content("The analysis shows:\n").build(),
                AssistantMessageChunk.builder().content("- Result 1\n- Result 2\n").build());

        List<MarkdownContent> parsedObjects = streamParse(chunks);
        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);

        assertEquals(2, finalResult.getHeaders().size());
        assertEquals("Report", finalResult.getHeaders().get(0).get("title"));
        assertEquals("Summary", finalResult.getHeaders().get(1).get("title"));
        assertEquals(1, finalResult.getLists().size());
        assertFalse(finalResult.getElements().isEmpty());
        assertEquals(MarkdownElementType.HEADER, finalResult.getElements().get(0).getType());
        assertEquals("Report", finalResult.getElements().get(0).getContent().get("title"));
    }

    @Test
    @Tag("level2")
    void testStreamParseComplexMarkdown() {
        List<String> chunks = List.of(
                "# Main Title\n\n",
                "Introduction paragraph.\n\n",
                "## Code Examples\n\n",
                "```python\n",
                "def example():\n",
                "    return 'test'\n",
                "```\n\n",
                "## Links and Images\n\n",
                "Visit [our site](https://example.com)\n\n",
                "![Image](https://example.com/img.png)\n\n",
                "## Data Table\n\n",
                "| Col1 | Col2 |\n",
                "|------|------|\n",
                "| A    | B    |\n");

        List<MarkdownContent> parsedObjects = streamParse(chunks);
        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);

        assertEquals(4, finalResult.getHeaders().size());
        assertEquals(1, finalResult.getCodeBlocks().size());
        assertEquals(1, finalResult.getLinks().size());
        assertEquals(1, finalResult.getImages().size());
        assertEquals(1, finalResult.getTables().size());
        assertEquals("Main Title", finalResult.getHeaders().get(0).get("title"));
        assertEquals("python", finalResult.getCodeBlocks().get(0).get("language"));
        assertEquals("https://example.com", finalResult.getLinks().get(0).get("url"));
        assertEquals("Image", finalResult.getImages().get(0).get("alt"));
    }

    @Test
    @Tag("level2")
    void testStreamParseEmptyChunks() {
        List<Object> chunks = new ArrayList<>();
        chunks.add("");
        chunks.add(null);
        chunks.add("");

        List<MarkdownContent> parsedObjects = streamParse(chunks);

        assertEquals(0, parsedObjects.size());
    }

    @Test
    @Tag("level1")
    void testParseMixedHeaders() {
        String markdownText = """
                # H1 Title
                ## H2 Title
                ### H3 Title
                #### H4 Title
                ##### H5 Title
                ###### H6 Title
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertEquals(6, result.getHeaders().size());
        for (int index = 1; index <= 6; index++) {
            assertEquals(String.valueOf(index), result.getHeaders().get(index - 1).get("level"));
            assertEquals("H" + index + " Title", result.getHeaders().get(index - 1).get("title"));
        }
    }

    @Test
    @Tag("level2")
    void testElementOrderPreservation() {
        String markdownText = """
                # First Header

                This is a paragraph.

                [A Link](https://example.com)

                ## Second Header

                ```python
                print("code")
                ```

                ![Image](https://example.com/img.png)

                - List item 1
                - List item 2
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        assertFalse(result.getElements().isEmpty());
        List<String> expectedOrder = List.of(
                MarkdownElementType.HEADER,
                MarkdownElementType.LINK,
                MarkdownElementType.HEADER,
                MarkdownElementType.CODE_BLOCK,
                MarkdownElementType.IMAGE,
                MarkdownElementType.LIST);
        List<String> actualOrder = result.getElements().stream()
                .map(MarkdownElement::getType)
                .toList();
        assertEquals(expectedOrder, actualOrder);

        for (int index = 0; index < result.getElements().size() - 1; index++) {
            assertTrue(result.getElements().get(index).getStartPos()
                    < result.getElements().get(index + 1).getStartPos());
        }
    }

    @Test
    @Tag("level1")
    void testGetElementsByType() {
        String markdownText = """
                # Title 1
                ## Title 2
                [Link 1](url1)
                [Link 2](url2)
                """;
        MarkdownContent result = parse(markdownText);

        assertNotNull(result);
        List<MarkdownElement> headers = result.getElements().stream()
                .filter(element -> MarkdownElementType.HEADER.equals(element.getType()))
                .toList();
        List<MarkdownElement> links = result.getElements().stream()
                .filter(element -> MarkdownElementType.LINK.equals(element.getType()))
                .toList();

        assertEquals(2, headers.size());
        assertEquals(2, links.size());
        assertEquals("Title 1", headers.get(0).getContent().get("title"));
        assertEquals("Title 2", headers.get(1).getContent().get("title"));
        assertEquals("Link 1", links.get(0).getContent().get("text"));
        assertEquals("Link 2", links.get(1).getContent().get("text"));
    }
}
