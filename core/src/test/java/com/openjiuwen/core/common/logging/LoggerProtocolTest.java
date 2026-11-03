package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggerProtocolTest {

    @Test
    void mirrorsPythonProtocolMethodSurface() {
        Set<String> methodNames = Arrays.stream(LoggerProtocol.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(methodNames.contains("debug"));
        assertTrue(methodNames.contains("info"));
        assertTrue(methodNames.contains("warning"));
        assertTrue(methodNames.contains("error"));
        assertTrue(methodNames.contains("critical"));
        assertTrue(methodNames.contains("exception"));
        assertTrue(methodNames.contains("log"));
        assertTrue(methodNames.contains("setLevel"));
        assertTrue(methodNames.contains("addHandler"));
        assertTrue(methodNames.contains("removeHandler"));
        assertTrue(methodNames.contains("addFilter"));
        assertTrue(methodNames.contains("removeFilter"));
        assertTrue(methodNames.contains("getConfig"));
        assertTrue(methodNames.contains("reconfigure"));
        assertTrue(methodNames.contains("logger"));
    }
}
