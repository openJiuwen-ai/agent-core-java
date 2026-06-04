/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.sse;

/**
 * SSE (Server-Sent Events) MCP Server Example.
 * <p>
 * This class mirrors the Python SSE MCP server's calculator tools as Java
 * static methods. The Python file still owns the FastMCP transport startup;
 * these methods preserve the observable tool behavior for Java tests and
 * examples.
 * <p>
 * Mirrors Python's {@code server} in
 * {@code examples.mcp.sse.server}.
 * <p>
 * Python server implementation (server.py):
 * <pre>
 * from fastmcp import FastMCP
 * 
 * mcp = FastMCP(name="calculator-sse-server")
 * 
 * &#64;mcp.tool()
 * def add(a: float, b: float) -> float:
 *     """Add two numbers together."""
 *     return a + b
 * 
 * &#64;mcp.tool()
 * def subtract(a: float, b: float) -> float:
 *     """Subtract b from a."""
 *     return a - b
 * 
 * &#64;mcp.tool()
 * def multiply(a: float, b: float) -> float:
 *     """Multiply two numbers together."""
 *     return a * b
 * 
 * &#64;mcp.tool()
 * def divide(a: float, b: float) -> float:
 *     """Divide a by b. Returns an error message if b is zero."""
 *     if b == 0:
 *         return "Error: division by zero"
 *     return a / b
 * 
 * &#64;mcp.tool()
 * def power(base: float, exponent: float) -> float:
 *     """Raise base to the power of exponent."""
 *     return base ** exponent
 * 
 * if __name__ == "__main__":
 *     mcp.run(transport="sse", host="127.0.0.1", port=3001)
 * </pre>
 * <p>
 * To run the Python server:
 * <ul>
 *   <li>Install requirements: pip install fastmcp</li>
 *   <li>Run: python server.py</li>
 *   <li>Server starts on http://127.0.0.1:3001/sse</li>
 * </ul>
 * <p>
 * Java clients can connect to this Python server using:
 * <ul>
 *   <li>{@link com.openjiuwen.core.foundation.tool.mcp.client.SseClient}</li>
 *   <li>{@link com.openjiuwen.core.foundation.tool.mcp.McpTool}</li>
 * </ul>
 */
public final class ServerExample {

    private ServerExample() {
        // Prevent instantiation
    }
    
    // Server configuration constants (mirrored from Python)
    public static final String SERVER_HOST = "127.0.0.1";
    public static final int SERVER_PORT = 3001;
    public static final String SERVER_ENDPOINT = "/sse";
    public static final String SERVER_URL = "http://" + SERVER_HOST + ":" + SERVER_PORT + SERVER_ENDPOINT;
    public static final String SERVER_NAME = "calculator-sse-server";
    
    // Available tools on this server
    public static final String TOOL_ADD = "add";
    public static final String TOOL_SUBTRACT = "subtract";
    public static final String TOOL_MULTIPLY = "multiply";
    public static final String TOOL_DIVIDE = "divide";
    public static final String TOOL_POWER = "power";

    /**
     * Add two numbers together.
     *
     * @param a first addend
     * @param b second addend
     * @return sum of {@code a} and {@code b}
     */
    public static double add(double a, double b) {
        return a + b;
    }

    /**
     * Subtract {@code b} from {@code a}.
     *
     * @param a minuend
     * @param b subtrahend
     * @return difference
     */
    public static double subtract(double a, double b) {
        return a - b;
    }

    /**
     * Multiply two numbers together.
     *
     * @param a first factor
     * @param b second factor
     * @return product
     */
    public static double multiply(double a, double b) {
        return a * b;
    }

    /**
     * Divide {@code a} by {@code b}.
     *
     * @param a dividend
     * @param b divisor
     * @return quotient, or the same error string returned by Python for zero division
     */
    public static Object divide(double a, double b) {
        if (b == 0.0d) {
            return "Error: division by zero";
        }
        return a / b;
    }

    /**
     * Raise {@code base} to the power of {@code exponent}.
     *
     * @param base base value
     * @param exponent exponent value
     * @return power result
     */
    public static double power(double base, double exponent) {
        return Math.pow(base, exponent);
    }
}
