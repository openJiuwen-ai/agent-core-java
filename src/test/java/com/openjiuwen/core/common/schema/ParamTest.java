package com.openjiuwen.core.common.schema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParamTest {

    @Test
    void stringFactoryMatchesPythonDefaults() {
        Param param = Param.string("username", "Username", true);

        assertEquals("username", param.getName());
        assertEquals("Username", param.getDescription());
        assertSame(ParamType.STRING, param.getType());
        assertEquals(true, param.isRequired());
        assertNull(param.getDefaultValue());
        assertNull(param.getItems());
        assertNull(param.getProperties());
    }

    @Test
    void booleanFactoryMatchesPythonNameAndDefault() {
        Param param = Param.booleanParam("enabled", "Enabled flag", false, true);

        assertSame(ParamType.BOOLEAN, param.getType());
        assertEquals(false, param.isRequired());
        assertEquals(true, param.getDefaultValue());
    }

    @Test
    void arrayFactoryRequiresItems() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Param.array("tags", "Tag list", false, null)
        );

        assertEquals("Param 'tags': Array type requires 'items' field", error.getMessage());
    }

    @Test
    void objectFactoryRequiresProperties() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Param.object("user", "User object", true, null)
        );

        assertEquals("Param 'user': Object type requires 'properties' field", error.getMessage());
    }

    @Test
    void nestedArrayAndObjectFactoriesPreserveStructure() {
        Param item = Param.string("tag", "Tag", true);
        Param tags = Param.array("tags", "Tag list", false, item, List.of("a", "b"));
        Param user = Param.object(
                "user",
                "User object",
                true,
                List.of(
                        Param.string("name", "Name", true),
                        Param.integer("age", "Age", false, 18)
                ),
                Map.of("name", "Jun")
        );

        assertSame(item, tags.getItems());
        assertEquals(List.of("a", "b"), tags.getDefaultValue());
        assertEquals(2, user.getProperties().size());
        assertEquals(Map.of("name", "Jun"), user.getDefaultValue());
    }

    @Test
    void paramTypeRoundTripsLowerCaseValues() {
        assertSame(ParamType.STRING, ParamType.fromValue("string"));
        assertSame(ParamType.BOOLEAN, ParamType.fromValue("BOOLEAN"));
        assertEquals("number", ParamType.NUMBER.getValue());
    }
}
