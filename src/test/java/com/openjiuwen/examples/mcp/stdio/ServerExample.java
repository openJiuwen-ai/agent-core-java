/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import java.util.Locale;

/**
 * Stdio MCP Server Example.
 * <p>
 * This class mirrors the Python Stdio MCP server's text processing tools as
 * Java static methods. The Python file still owns FastMCP stdio transport
 * startup; these methods preserve the observable tool behavior.
 * <p>
 * Mirrors Python's {@code server} in
 * {@code examples.mcp.stdio.server}.
 * <p>
 * Python server implementation (server.py):
 * <pre>
 * from fastmcp import FastMCP
 * 
 * mcp = FastMCP(name="text-processor-stdio-server")
 * 
 * &#64;mcp.tool()
 * def word_count(text: str) -> int:
 *     """Count the number of words in the given text."""
 *     return len(text.split())
 * 
 * &#64;mcp.tool()
 * def char_count(text: str) -> int:
 *     """Count the number of characters (including spaces) in the given text."""
 *     return len(text)
 * 
 * &#64;mcp.tool()
 * def reverse_text(text: str) -> str:
 *     """Reverse the characters of the given text."""
 *     return text[::-1]
 * 
 * &#64;mcp.tool()
 * def to_uppercase(text: str) -> str:
 *     """Convert the given text to uppercase."""
 *     return text.upper()
 * 
 * &#64;mcp.tool()
 * def to_lowercase(text: str) -> str:
 *     """Convert the given text to lowercase."""
 *     return text.lower()
 * </pre>
 * <p>
 * For Stdio transport, stdout is reserved exclusively for JSON-RPC messages.
 * Java clients can connect using {@link com.openjiuwen.core.foundation.tool.mcp.client.StdioClient}
 * which launches this server as a subprocess.
 */
public final class ServerExample {

    private ServerExample() {
        // Prevent instantiation
    }
    
    // Server configuration constants
    public static final String SERVER_NAME = "text-processor-stdio-server";
    
    // Available tools on this server
    public static final String TOOL_WORD_COUNT = "word_count";
    public static final String TOOL_CHAR_COUNT = "char_count";
    public static final String TOOL_REVERSE_TEXT = "reverse_text";
    public static final String TOOL_TO_UPPERCASE = "to_uppercase";
    public static final String TOOL_TO_LOWERCASE = "to_lowercase";
    public static final String TOOL_COUNT_LINES = "count_lines";

    /**
     * Count words using Python {@code str.split()}-style whitespace handling.
     *
     * @param text text to inspect
     * @return number of whitespace-separated words
     */
    public static int wordCount(String text) {
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            return 0;
        }
        return stripped.split("\\s+").length;
    }

    /**
     * Count Unicode code points in the text.
     *
     * @param text text to inspect
     * @return character count
     */
    public static int charCount(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * Reverse the characters of the given text.
     *
     * @param text text to reverse
     * @return reversed text
     */
    public static String reverseText(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    /**
     * Convert text to uppercase.
     *
     * @param text text to convert
     * @return uppercase text
     */
    public static String toUppercase(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    /**
     * Convert text to lowercase.
     *
     * @param text text to convert
     * @return lowercase text
     */
    public static String toLowercase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Count lines using Java's closest equivalent to Python {@code splitlines()}.
     *
     * @param text text to inspect
     * @return line count
     */
    public static int countLines(String text) {
        return (int) text.lines().count();
    }
}
