/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/foundation/llm/output_parsers/markdown_output_parser.py}.
 *
 * <p>Mirrors Python's {@code TestMarkdownOutputParser} in
 * {@code tests/unit_tests/core/foundation/output_parser/test_markdown_output_parser.py}.</p>
 */
class MarkdownOutputParserTest {

    private final MarkdownOutputParser parser = new MarkdownOutputParser();

    @Test
    @Tag("level0")
    void patternClassExists() {
        assertThat(Pattern.class).isNotNull();
    }

    @Test
    @Tag("level0")
    void matcherClassExists() {
        assertThat(Matcher.class).isNotNull();
    }

    @Test
    @Tag("level1")
    void codeBlockPatternMatches() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```(\\w+)?\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        assertThat(matcher.find()).isTrue();
    }

    @Test
    @Tag("level1")
    void jsonCodeBlockLanguageIsDetected() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```(\\w+)");
        Matcher matcher = pattern.matcher(markdown);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("json");
    }

    @Test
    @Tag("level1")
    void codeBlockContentIsExtracted() {
        String markdown = "```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```\\w*\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).contains("{\"key\": \"value\"}");
    }

    @Test
    @Tag("level2")
    void multipleCodeBlocksAreDetected() {
        String markdown = "```python\nprint('hello')\n```\n\n```json\n{\"key\": \"value\"}\n```";
        Pattern pattern = Pattern.compile("```\\w+\\s*([^`]*)```");
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Tag("level2")
    void noCodeBlockInPlainText() {
        Pattern pattern = Pattern.compile("```");
        assertThat(pattern.matcher("This is just plain text without any code blocks.").find()).isFalse();
    }

    @Test
    @Tag("level3")
    void headerPatternMatchesAllLevels() {
        String markdown = "# Header 1\n## Header 2\n### Header 3";
        Pattern pattern = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Tag("level3")
    void headerLevelIsDetected() {
        Matcher matcher = Pattern.compile("^#{1,6}").matcher("## This is a level 2 header");
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group().length()).isEqualTo(2);
    }

    @Test
    @Tag("level4")
    void bulletListPatternMatches() {
        String markdown = "- Item 1\n- Item 2\n- Item 3";
        Matcher matcher = Pattern.compile("^-\\s+(.+)$", Pattern.MULTILINE).matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Tag("level4")
    void numberedListPatternMatches() {
        String markdown = "1. First\n2. Second\n3. Third";
        Matcher matcher = Pattern.compile("^\\d+\\.\\s+(.+)$", Pattern.MULTILINE).matcher(markdown);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertThat(count).isEqualTo(3);
    }

    @Test
    void parseSimpleMarkdown() {
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

        assertThat(result).isNotNull();
        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0)).containsEntry("title", "Main Title").containsEntry("level", "1");
        assertThat(result.getHeaders().get(1)).containsEntry("title", "Subtitle").containsEntry("level", "2");
        assertThat(result.getCodeBlocks()).hasSize(1);
        assertThat(result.getCodeBlocks().get(0)).containsEntry("language", "python");
        assertThat(String.valueOf(result.getCodeBlocks().get(0).get("code"))).contains("def hello():");
        assertThat(result.getLinks()).hasSize(1);
        assertThat(result.getLinks().get(0)).containsEntry("text", "OpenAI").containsEntry("url", "https://openai.com");
    }

    @Test
    void parseAssistantMessageMarkdown() {
        AssistantMessage message = new AssistantMessage("""
                ## Analysis Results

                The data shows:
                - Item 1: Important finding
                - Item 2: Another insight

                ![Chart](https://example.com/chart.png)
                """);

        MarkdownContent result = parse(message);

        assertThat(result).isNotNull();
        assertThat(result.getHeaders()).hasSize(1);
        assertThat(result.getHeaders().get(0)).containsEntry("title", "Analysis Results");
        assertThat(result.getImages()).hasSize(1);
        assertThat(result.getImages().get(0)).containsEntry("alt", "Chart").containsEntry("url", "https://example.com/chart.png");
        assertThat(result.getLists()).hasSize(1);
        assertThat(result.getLists().get(0)).contains("Item 1: Important finding");
    }

    @Test
    void parseCodeBlocks() {
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

        assertThat(result).isNotNull();
        assertThat(result.getCodeBlocks()).hasSize(3);
        Map<String, Object> jsBlock = result.getCodeBlocks().stream()
                .filter(block -> "javascript".equals(block.get("language")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> sqlBlock = result.getCodeBlocks().stream()
                .filter(block -> "sql".equals(block.get("language")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> inlineBlock = result.getCodeBlocks().stream()
                .filter(block -> "inline".equals(block.get("language")))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(jsBlock.get("code"))).contains("console.log");
        assertThat(String.valueOf(sqlBlock.get("code"))).contains("SELECT");
        assertThat(inlineBlock.get("code")).isEqualTo("print(\"test\")");
    }

    @Test
    void parseTables() {
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

        assertThat(result).isNotNull();
        assertThat(result.getTables()).hasSize(2);
        assertThat(result.getTables().get(0)).contains("Alice").contains("Bob");
        assertThat(result.getTables().get(1)).contains("Apple");
    }

    @Test
    void parseLists() {
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

        assertThat(result).isNotNull();
        assertThat(result.getLists()).hasSize(3);
        assertThat(result.getLists().get(0)).contains("Milk").contains("Bread");
        assertThat(result.getLists().get(1)).contains("1. Review code").contains("2. Write tests");
        assertThat(result.getLists().get(2)).contains("Item A");
    }

    @Test
    void parseLinksAndImages() {
        String markdownText = """
                Check out these resources:

                [GitHub](https://github.com)
                [Documentation](https://docs.example.com)

                Here are some images:
                ![Logo](https://example.com/logo.png)
                ![Screenshot](https://example.com/screen.jpg)
                """;

        MarkdownContent result = parse(markdownText);

        assertThat(result).isNotNull();
        assertThat(result.getLinks()).hasSize(2);
        assertThat(result.getLinks().get(0)).containsEntry("text", "GitHub").containsEntry("url", "https://github.com");
        assertThat(result.getImages()).hasSize(2);
        assertThat(result.getImages().get(0)).containsEntry("alt", "Logo").containsEntry("url", "https://example.com/logo.png");
    }

    @Test
    void parseEmptyContent() {
        assertThat(parse("")).isNull();
        assertThat(parse(null)).isNull();
    }

    @Test
    void parsePlainText() {
        String plainText = "This is just plain text without any markdown formatting.";

        MarkdownContent result = parse(plainText);

        assertThat(result).isNotNull();
        assertThat(result.getRawContent()).isEqualTo(plainText);
        assertThat(result.getHeaders()).isEmpty();
        assertThat(result.getCodeBlocks()).isEmpty();
        assertThat(result.getLinks()).isEmpty();
    }

    @Test
    void streamParseMarkdownChunks() {
        List<String> chunks = List.of(
                "# Title\n\n",
                "This is a paragraph.\n\n",
                "```python\n",
                "print('hello')\n",
                "```\n\n",
                "[Link](https://example.com)"
        );

        List<MarkdownContent> parsedObjects = streamParse(chunks);
        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);

        assertThat(parsedObjects).isNotEmpty();
        assertThat(finalResult.getHeaders()).hasSize(1);
        assertThat(finalResult.getHeaders().get(0)).containsEntry("title", "Title");
        assertThat(finalResult.getCodeBlocks()).hasSize(1);
        assertThat(finalResult.getCodeBlocks().get(0)).containsEntry("language", "python");
        assertThat(finalResult.getLinks()).hasSize(1);
        assertThat(finalResult.getLinks().get(0)).containsEntry("text", "Link");
    }

    @Test
    void streamParseFragmentedMarkdown() {
        List<String> chunks = List.of(
                "## Sect",
                "ion Title\n\n",
                "Here's a co",
                "de block:\n```js\ncons",
                "ole.log('test');\n```\n\n",
                "And a [li",
                "nk](https://test.com)"
        );

        MarkdownContent finalResult = streamParse(chunks).getLast();

        assertThat(finalResult.getHeaders()).hasSize(1);
        assertThat(finalResult.getHeaders().get(0)).containsEntry("title", "Section Title");
        assertThat(finalResult.getCodeBlocks()).hasSize(1);
        assertThat(finalResult.getCodeBlocks().get(0)).containsEntry("language", "js");
        assertThat(finalResult.getLinks()).hasSize(1);
    }

    @Test
    void streamParseAssistantMessageChunks() {
        List<AssistantMessageChunk> chunks = List.of(
                AssistantMessageChunk.builder().content("# Report\n\n").build(),
                AssistantMessageChunk.builder().content("## Summary\n").build(),
                AssistantMessageChunk.builder().content("The analysis shows:\n").build(),
                AssistantMessageChunk.builder().content("- Result 1\n- Result 2\n").build()
        );

        MarkdownContent finalResult = streamParse(chunks).getLast();

        assertThat(finalResult.getHeaders()).hasSize(2);
        assertThat(finalResult.getHeaders().get(0)).containsEntry("title", "Report");
        assertThat(finalResult.getHeaders().get(1)).containsEntry("title", "Summary");
        assertThat(finalResult.getLists()).hasSize(1);
        assertThat(finalResult.getElements()).isNotEmpty();
        assertThat(finalResult.getElements().get(0).getType()).isEqualTo(MarkdownElementType.HEADER);
        assertThat(finalResult.getElements().get(0).getContent().get("title")).isEqualTo("Report");
    }

    @Test
    void streamParseComplexMarkdown() {
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
                "| A    | B    |\n"
        );

        MarkdownContent finalResult = streamParse(chunks).getLast();

        assertThat(finalResult.getHeaders()).hasSize(4);
        assertThat(finalResult.getCodeBlocks()).hasSize(1);
        assertThat(finalResult.getLinks()).hasSize(1);
        assertThat(finalResult.getImages()).hasSize(1);
        assertThat(finalResult.getTables()).hasSize(1);
        assertThat(finalResult.getHeaders().get(0)).containsEntry("title", "Main Title");
        assertThat(finalResult.getCodeBlocks().get(0)).containsEntry("language", "python");
        assertThat(finalResult.getLinks().get(0)).containsEntry("url", "https://example.com");
        assertThat(finalResult.getImages().get(0)).containsEntry("alt", "Image");
    }

    @Test
    void streamParseEmptyChunks() {
        List<Object> chunks = new ArrayList<>();
        chunks.add("");
        chunks.add(null);
        chunks.add("");

        assertThat(streamParse(chunks)).isEmpty();
    }

    @Test
    void parseMixedHeaders() {
        MarkdownContent result = parse("""
                # H1 Title
                ## H2 Title
                ### H3 Title
                #### H4 Title
                ##### H5 Title
                ###### H6 Title
                """);

        assertThat(result).isNotNull();
        assertThat(result.getHeaders()).hasSize(6);
        for (int index = 1; index <= 6; index++) {
            assertThat(result.getHeaders().get(index - 1))
                    .containsEntry("level", String.valueOf(index))
                    .containsEntry("title", "H" + index + " Title");
        }
    }

    @Test
    void elementOrderIsPreserved() {
        MarkdownContent result = parse("""
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
                """);

        List<String> expectedOrder = List.of(
                MarkdownElementType.HEADER,
                MarkdownElementType.LINK,
                MarkdownElementType.HEADER,
                MarkdownElementType.CODE_BLOCK,
                MarkdownElementType.IMAGE,
                MarkdownElementType.LIST
        );

        assertThat(result).isNotNull();
        assertThat(result.getElements()).extracting(MarkdownElement::getType).containsExactlyElementsOf(expectedOrder);
        for (int index = 0; index < result.getElements().size() - 1; index++) {
            assertThat(result.getElements().get(index).getStartPos())
                    .isLessThan(result.getElements().get(index + 1).getStartPos());
        }
    }

    @Test
    void getElementsByType() {
        MarkdownContent result = parse("""
                # Title 1
                ## Title 2
                [Link 1](url1)
                [Link 2](url2)
                """);

        List<MarkdownElement> headers = result.getElements().stream()
                .filter(element -> MarkdownElementType.HEADER.equals(element.getType()))
                .toList();
        List<MarkdownElement> links = result.getElements().stream()
                .filter(element -> MarkdownElementType.LINK.equals(element.getType()))
                .toList();

        assertThat(headers).hasSize(2);
        assertThat(links).hasSize(2);
        assertThat(headers.get(0).getContent().get("title")).isEqualTo("Title 1");
        assertThat(headers.get(1).getContent().get("title")).isEqualTo("Title 2");
        assertThat(links.get(0).getContent().get("text")).isEqualTo("Link 1");
        assertThat(links.get(1).getContent().get("text")).isEqualTo("Link 2");
    }

    private MarkdownContent parse(Object input) {
        return (MarkdownContent) parser.parse(input).join();
    }

    private List<MarkdownContent> streamParse(List<?> chunks) {
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add((MarkdownContent) iterator.next());
        }
        return parsedObjects;
    }
}
