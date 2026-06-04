/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import java.util.ArrayList;
import java.util.List;

/**
 * Streamable HTTP MCP Server Example.
 * <p>
 * This class mirrors the Python Streamable HTTP MCP server's in-memory note
 * tools as Java static methods. The Python file still owns FastMCP transport
 * startup; these methods preserve the observable tool behavior.
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

    private static final List<String> NOTES = new ArrayList<>();

    public static final String SERVER_HOST = "127.0.0.1";
    public static final int SERVER_PORT = 3002;
    public static final String SERVER_ENDPOINT = "/mcp";
    public static final String SERVER_URL = "http://" + SERVER_HOST + ":" + SERVER_PORT + SERVER_ENDPOINT;
    public static final String SERVER_NAME = "notes-streamable-http-server";

    public static final String TOOL_ADD_NOTE = "add_note";
    public static final String TOOL_GET_NOTE = "get_note";
    public static final String TOOL_LIST_NOTES = "list_notes";
    public static final String TOOL_DELETE_NOTE = "delete_note";
    public static final String TOOL_CLEAR_NOTES = "clear_notes";

    /**
     * Add a new note and return its assigned ID.
     *
     * @param content note content
     * @return creation message
     */
    public static synchronized String addNote(String content) {
        NOTES.add(content);
        int noteId = NOTES.size() - 1;
        return "Note added with ID " + noteId;
    }

    /**
     * Retrieve a note by ID.
     *
     * @param noteId note ID
     * @return note content or Python-compatible error text
     */
    public static synchronized String getNote(int noteId) {
        if (noteId < 0 || noteId >= NOTES.size()) {
            return "Error: note with ID " + noteId + " does not exist";
        }
        return NOTES.get(noteId);
    }

    /**
     * List all notes with IDs.
     *
     * @return note list, or Python-compatible empty-store message
     */
    public static synchronized List<String> listNotes() {
        if (NOTES.isEmpty()) {
            return List.of("No notes yet.");
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < NOTES.size(); i++) {
            result.add("[" + i + "] " + NOTES.get(i));
        }
        return result;
    }

    /**
     * Delete a note by ID.
     *
     * @param noteId note ID
     * @return deletion message or Python-compatible error text
     */
    public static synchronized String deleteNote(int noteId) {
        if (noteId < 0 || noteId >= NOTES.size()) {
            return "Error: note with ID " + noteId + " does not exist";
        }
        String removed = NOTES.remove(noteId);
        return "Deleted note: '" + removed + "'";
    }

    /**
     * Delete all notes.
     *
     * @return clear count message
     */
    public static synchronized String clearNotes() {
        int count = NOTES.size();
        NOTES.clear();
        return "Cleared " + count + " note(s)";
    }
}
