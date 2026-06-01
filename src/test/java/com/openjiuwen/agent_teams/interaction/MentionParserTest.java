package com.openjiuwen.agent_teams.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.interaction.router}.
 */
class MentionParserTest {

    @Test
    void parseMentionReturnsNullForEmptyInput() {
        assertNull(MentionParser.parseMention(null));
        assertNull(MentionParser.parseMention(""));
        assertNull(MentionParser.parseMention("   "));
    }

    @Test
    void parseMentionReturnsNullWhenPrefixIsMissingOrIncomplete() {
        assertNull(MentionParser.parseMention("hello team"));
        assertNull(MentionParser.parseMention("@dev"));
        assertNull(MentionParser.parseMention("@dev "));
    }

    @Test
    void parseMentionExtractsTargetAndBody() {
        MentionParser.Mention mention = MentionParser.parseMention("@dev build the parser");

        assertEquals("dev", mention.target());
        assertEquals("build the parser", mention.body());
    }

    @Test
    void parseMentionAllowsMultilineBody() {
        MentionParser.Mention mention = MentionParser.parseMention("@dev first line\nsecond line");

        assertEquals("dev", mention.target());
        assertEquals("first line\nsecond line", mention.body());
    }

    @Test
    void parseMentionPreservesWhitespaceBodyWhenPresent() {
        MentionParser.Mention mention = MentionParser.parseMention("@dev  ");

        assertEquals("dev", mention.target());
        assertEquals(" ", mention.body());
    }

    @Test
    void isReservedNameMatchesRuntimeConstants() {
        assertTrue(MentionParser.isReservedName("user"));
        assertTrue(MentionParser.isReservedName("team_leader"));
        assertTrue(MentionParser.isReservedName("human_agent"));
        assertFalse(MentionParser.isReservedName("dev"));
        assertFalse(MentionParser.isReservedName(null));
    }
}
