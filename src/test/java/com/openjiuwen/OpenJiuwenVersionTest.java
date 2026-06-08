package com.openjiuwen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OpenJiuwenVersionTest {

    @Test
    void returnsCurrentSourceVersionWhenPackageMetadataIsUnavailable() {
        assertEquals("0.1.14", OpenJiuwenVersion.version());
    }
}
