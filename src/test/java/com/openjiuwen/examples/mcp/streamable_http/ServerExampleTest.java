/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.streamable_http;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerExampleTest {

    @Test
    void noteToolsMirrorPythonBehavior() {
        ServerExample.clearNotes();

        assertEquals(List.of("No notes yet."), ServerExample.listNotes());
        assertEquals("Note added with ID 0", ServerExample.addNote("first"));
        assertEquals("Note added with ID 1", ServerExample.addNote("second"));
        assertEquals("first", ServerExample.getNote(0));
        assertEquals(List.of("[0] first", "[1] second"), ServerExample.listNotes());
        assertEquals("Deleted note: 'first'", ServerExample.deleteNote(0));
        assertEquals("second", ServerExample.getNote(0));
        assertEquals("Error: note with ID 7 does not exist", ServerExample.getNote(7));
        assertEquals("Cleared 1 note(s)", ServerExample.clearNotes());
        assertEquals(List.of("No notes yet."), ServerExample.listNotes());
    }
}
