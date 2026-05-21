/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

/**
 * Stdio MCP Server Example - Documentation placeholder.
 * <p>
 * This class documents the Python Stdio MCP server implementation.
 * The MCP server in Python uses FastMCP framework, which is Python-specific.
 * Java MCP implementation currently provides client-side only.
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
}