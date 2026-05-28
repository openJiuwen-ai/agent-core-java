/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

/**
 * Streamable HTTP MCP Server Example - Documentation placeholder.
 * <p>
 * This class documents the Python Streamable HTTP MCP server implementation.
 * The MCP server in Python uses FastMCP framework, which is Python-specific.
 * Java MCP implementation currently provides client-side only.
 * <p>
 * Mirrors Python's {@code server} in
 * {@code examples.mcp.streamable_http.server}.
 * <p>
 * Python server tools:
 * <ul>
 *   <li>add_note(content) - Add a new note</li>
 *   <li>get_note(note_id) - Retrieve a note by ID</li>
 *   <li>list_notes() - List all notes</li>
 *   <li>delete_note(note_id) - Delete a note</li>
 * </ul>
 */
public final class ServerExample {

    private ServerExample() {}

    public static final String SERVER_HOST = "127.0.0.1";
    public static final int SERVER_PORT = 3002;
    public static final String SERVER_ENDPOINT = "/mcp";
    public static final String SERVER_URL = "http://" + SERVER_HOST + ":" + SERVER_PORT + SERVER_ENDPOINT;
    public static final String SERVER_NAME = "notes-streamable-http-server";

    public static final String TOOL_ADD_NOTE = "add_note";
    public static final String TOOL_GET_NOTE = "get_note";
    public static final String TOOL_LIST_NOTES = "list_notes";
    public static final String TOOL_DELETE_NOTE = "delete_note";
}