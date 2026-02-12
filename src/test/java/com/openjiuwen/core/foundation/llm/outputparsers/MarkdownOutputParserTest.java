// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown输出解析器测试
 */
class MarkdownOutputParserTest {

    private MarkdownOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarkdownOutputParser();
    }

    @Test
    @DisplayName("测试解析简单的Markdown内容")
    void testParseSimpleMarkdown() throws Exception {
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
        MarkdownContent result = parser.parse(markdownText).get();
        
        assertNotNull(result);
        assertEquals(2, result.getHeaders().size());
        assertEquals("Main Title", result.getHeaders().get(0).get("title"));
        assertEquals("1", result.getHeaders().get(0).get("level"));
        assertEquals("Subtitle", result.getHeaders().get(1).get("title"));
        assertEquals("2", result.getHeaders().get(1).get("level"));
        
        assertEquals(1, result.getCodeBlocks().size());
        assertEquals("python", result.getCodeBlocks().get(0).get("language"));
        assertTrue(result.getCodeBlocks().get(0).get("code").toString().contains("def hello():"));
        
        assertEquals(1, result.getLinks().size());
        assertEquals("OpenAI", result.getLinks().get(0).get("text"));
        assertEquals("https://openai.com", result.getLinks().get(0).get("url"));
    }

    @Test
    @DisplayName("测试解析AssistantMessage对象中的Markdown")
    void testParseAssistantMessageMarkdown() throws Exception {
        AssistantMessage aiMessage = new AssistantMessage("""
            ## Analysis Results
            
            The data shows:
            - Item 1: Important finding
            - Item 2: Another insight
            
            ![Chart](https://example.com/chart.png)
            """);
        MarkdownContent result = parser.parse(aiMessage).get();
        
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
    @DisplayName("测试解析空内容")
    void testParseEmptyContent() throws Exception {
        MarkdownContent result = parser.parse("").get();
        assertNull(result);
        
        result = parser.parse((String) null).get();
        assertNull(result);
    }

    @Test
    @DisplayName("测试解析纯文本")
    void testParsePlainText() throws Exception {
        String plainText = "This is just plain text without any markdown formatting.";
        MarkdownContent result = parser.parse(plainText).get();
        
        assertNotNull(result);
        assertEquals(plainText, result.getRawContent());
        assertEquals(0, result.getHeaders().size());
        assertEquals(0, result.getCodeBlocks().size());
        assertEquals(0, result.getLinks().size());
    }

    @Test
    @DisplayName("测试解析不同级别的标题")
    void testParseMixedHeaders() throws Exception {
        String markdownText = """
            # H1 Title
            ## H2 Title
            ### H3 Title
            #### H4 Title
            ##### H5 Title
            ###### H6 Title
            """;
        MarkdownContent result = parser.parse(markdownText).get();
        
        assertEquals(6, result.getHeaders().size());
        for (int i = 0; i < 6; i++) {
            assertEquals(String.valueOf(i + 1), result.getHeaders().get(i).get("level"));
            assertEquals("H" + (i + 1) + " Title", result.getHeaders().get(i).get("title"));
        }
    }

    @Test
    @DisplayName("测试解析表格")
    void testParseTables() throws Exception {
        String markdownText = """
            Here's a data table:
            
            | Name | Age | City |
            |------|-----|------|
            | Alice | 30 | NYC |
            | Bob | 25 | LA |
            """;
        MarkdownContent result = parser.parse(markdownText).get();
        
        assertEquals(1, result.getTables().size());
        assertTrue(result.getTables().get(0).contains("Alice"));
        assertTrue(result.getTables().get(0).contains("Bob"));
    }

    @Test
    @DisplayName("测试解析链接和图片")
    void testParseLinksAndImages() throws Exception {
        String markdownText = """
            Check out these resources:
            
            [GitHub](https://github.com)
            [Documentation](https://docs.example.com)
            
            Here are some images:
            ![Logo](https://example.com/logo.png)
            ![Screenshot](https://example.com/screen.jpg)
            """;
        MarkdownContent result = parser.parse(markdownText).get();
        
        assertEquals(2, result.getLinks().size());
        assertEquals("GitHub", result.getLinks().get(0).get("text"));
        assertEquals("https://github.com", result.getLinks().get(0).get("url"));
        
        assertEquals(2, result.getImages().size());
        assertEquals("Logo", result.getImages().get(0).get("alt"));
        assertEquals("https://example.com/logo.png", result.getImages().get(0).get("url"));
    }

    @Test
    @DisplayName("测试解析各种代码块(js/sql/inline)")
    void testParseCodeBlocks() throws Exception {
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
        MarkdownContent result = parser.parse(markdownText).get();
        
        assertEquals(3, result.getCodeBlocks().size());
        
        // JavaScript代码块
        boolean foundJs = false;
        for (var block : result.getCodeBlocks()) {
            if ("javascript".equals(block.get("language"))) {
                assertTrue(block.get("code").toString().contains("console.log"));
                foundJs = true;
            }
        }
        assertTrue(foundJs);
        
        // SQL代码块
        boolean foundSql = false;
        for (var block : result.getCodeBlocks()) {
            if ("sql".equals(block.get("language"))) {
                assertTrue(block.get("code").toString().contains("SELECT"));
                foundSql = true;
            }
        }
        assertTrue(foundSql);
        
        // 内联代码
        boolean foundInline = false;
        for (var block : result.getCodeBlocks()) {
            if ("inline".equals(block.get("language"))) {
                assertEquals("print(\"test\")", block.get("code"));
                foundInline = true;
            }
        }
        assertTrue(foundInline);
    }

    @Test
    @DisplayName("测试解析无序/有序列表")
    void testParseLists() throws Exception {
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
        MarkdownContent result = parser.parse(markdownText).get();
        
        assertEquals(3, result.getLists().size());
        
        // 无序列表
        String unorderedList = result.getLists().get(0);
        assertTrue(unorderedList.contains("Milk"));
        assertTrue(unorderedList.contains("Bread"));
        
        // 有序列表
        String orderedList = result.getLists().get(1);
        assertTrue(orderedList.contains("1. Review code"));
        assertTrue(orderedList.contains("2. Write tests"));
        
        // 另一个无序列表
        String anotherList = result.getLists().get(2);
        assertTrue(anotherList.contains("Item A"));
    }

    @Test
    @DisplayName("测试流式解析Markdown块")
    void testStreamParseMarkdownChunks() throws Exception {
        List<String> chunks = List.of(
                "# Title\n\n",
                "This is a paragraph.\n\n",
                "```python\n",
                "print('hello')\n",
                "```\n\n",
                "[Link](https://example.com)"
        );
        
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<MarkdownContent> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        // 应该有多个中间结果，最后一个包含完整内容
        assertTrue(parsedObjects.size() > 0);
        
        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);
        assertEquals(1, finalResult.getHeaders().size());
        assertEquals("Title", finalResult.getHeaders().get(0).get("title"));
        assertEquals(1, finalResult.getCodeBlocks().size());
        assertEquals("python", finalResult.getCodeBlocks().get(0).get("language"));
        assertEquals(1, finalResult.getLinks().size());
        assertEquals("Link", finalResult.getLinks().get(0).get("text"));
    }

    @Test
    @DisplayName("测试流式解析分片Markdown")
    void testStreamParseFragmentedMarkdown() throws Exception {
        List<String> chunks = List.of(
                "## Sect",
                "ion Title\n\n",
                "Here's a co",
                "de block:\n```js\ncons",
                "ole.log('test');\n```\n\n",
                "And a [li",
                "nk](https://test.com)"
        );
        
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<MarkdownContent> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);
        assertEquals(1, finalResult.getHeaders().size());
        assertEquals("Section Title", finalResult.getHeaders().get(0).get("title"));
        assertEquals(1, finalResult.getCodeBlocks().size());
        assertEquals("js", finalResult.getCodeBlocks().get(0).get("language"));
        assertEquals(1, finalResult.getLinks().size());
    }

    @Test
    @DisplayName("测试流式解析AssistantMessageChunk")
    void testStreamParseAssistantMessageChunks() throws Exception {
        List<AssistantMessageChunk> chunks = List.of(
                new AssistantMessageChunk.Builder().content("# Report\n\n").build(),
                new AssistantMessageChunk.Builder().content("## Summary\n").build(),
                new AssistantMessageChunk.Builder().content("The analysis shows:\n").build(),
                new AssistantMessageChunk.Builder().content("- Result 1\n- Result 2\n").build()
        );
        
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<MarkdownContent> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);
        assertEquals(2, finalResult.getHeaders().size());
        assertEquals("Report", finalResult.getHeaders().get(0).get("title"));
        assertEquals("Summary", finalResult.getHeaders().get(1).get("title"));
        assertEquals(1, finalResult.getLists().size());
    }

    @Test
    @DisplayName("测试流式解析复杂Markdown")
    void testStreamParseComplexMarkdown() throws Exception {
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
        
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<MarkdownContent> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        MarkdownContent finalResult = parsedObjects.get(parsedObjects.size() - 1);
        
        // 验证所有元素都被正确解析
        assertEquals(4, finalResult.getHeaders().size());  // Main Title + 3 sections
        assertEquals(1, finalResult.getCodeBlocks().size());
        assertEquals(1, finalResult.getLinks().size());
        assertEquals(1, finalResult.getImages().size());
        assertEquals(1, finalResult.getTables().size());
        
        // 验证具体内容
        assertEquals("Main Title", finalResult.getHeaders().get(0).get("title"));
        assertEquals("python", finalResult.getCodeBlocks().get(0).get("language"));
        assertEquals("https://example.com", finalResult.getLinks().get(0).get("url"));
        assertEquals("Image", finalResult.getImages().get(0).get("alt"));
    }

    @Test
    @DisplayName("测试流式解析空块")
    void testStreamParseEmptyChunks() {
        // 使用Arrays.asList而不是List.of，因为List.of不允许null元素
        List<String> chunks = java.util.Arrays.asList("", null, "");
        
        List<MarkdownContent> parsedObjects = new ArrayList<>();
        Iterator<MarkdownContent> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(0, parsedObjects.size());
    }

    @Test
    @DisplayName("测试元素顺序保持")
    void testElementOrderPreservation() throws Exception {
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
        MarkdownContent result = parser.parse(markdownText).get();
        
        // 验证元素按原文顺序排列
        assertTrue(result.getElements().size() > 0);
        
        // 验证顺序：Header -> Link -> Header -> Code -> Image -> List
        List<String> expectedOrder = List.of(
                MarkdownElementType.HEADER,  // First Header
                MarkdownElementType.LINK,    // A Link
                MarkdownElementType.HEADER,  // Second Header
                MarkdownElementType.CODE_BLOCK,  // python code
                MarkdownElementType.IMAGE,   // Image
                MarkdownElementType.LIST     // List
        );
        
        List<String> actualOrder = new ArrayList<>();
        for (var element : result.getElements()) {
            actualOrder.add(element.type());  // record的访问器不带get前缀
        }
        assertEquals(expectedOrder, actualOrder);
        
        // 验证位置信息
        for (int i = 0; i < result.getElements().size() - 1; i++) {
            assertTrue(result.getElements().get(i).startPos() < 
                       result.getElements().get(i + 1).startPos());
        }
    }

    @Test
    @DisplayName("测试按类型获取元素")
    void testGetElementsByType() throws Exception {
        String markdownText = """
            # Title 1
            ## Title 2
            [Link 1](url1)
            [Link 2](url2)
            """;
        MarkdownContent result = parser.parse(markdownText).get();
        
        // 按类型筛选元素
        List<MarkdownElement> headers = new ArrayList<>();
        List<MarkdownElement> links = new ArrayList<>();
        for (var element : result.getElements()) {
            if (MarkdownElementType.HEADER.equals(element.type())) {
                headers.add(element);
            } else if (MarkdownElementType.LINK.equals(element.type())) {
                links.add(element);
            }
        }
        
        assertEquals(2, headers.size());
        assertEquals(2, links.size());
        
        // 验证内容
        assertEquals("Title 1", headers.get(0).content().get("title"));
        assertEquals("Title 2", headers.get(1).content().get("title"));
        assertEquals("Link 1", links.get(0).content().get("text"));
        assertEquals("Link 2", links.get(1).content().get("text"));
    }
}

