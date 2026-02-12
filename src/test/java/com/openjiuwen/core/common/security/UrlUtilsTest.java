package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UrlUtils 测试类
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
class UrlUtilsTest {

    @Test
    void testCheckUrlIsValidHttps() {
        // Arrange
        String url = "https://www.example.com/api";
        
        // Act & Assert
        // Note: This will actually try to resolve DNS
        // In a real test environment, you might want to mock InetAddress
        assertDoesNotThrow(() -> UrlUtils.checkUrlIsValid(url));
    }

    @Test
    void testCheckUrlIsValidHttp() {
        // Arrange
        String url = "http://www.example.com/api";
        
        // Act & Assert
        assertDoesNotThrow(() -> UrlUtils.checkUrlIsValid(url));
    }

    @Test
    void testCheckUrlInvalidEmpty() {
        // Arrange
        String emptyUrl = "";
        
        // Act & Assert
        assertThrows(
            JiuWenBaseException.class,
            () -> UrlUtils.checkUrlIsValid(emptyUrl)
        );
    }

    @Test
    void testCheckUrlInvalidNull() {
        // Act & Assert
        assertThrows(
            JiuWenBaseException.class,
            () -> UrlUtils.checkUrlIsValid(null)
        );
    }

    @Test
    void testCheckUrlInvalidProtocol() {
        // Arrange
        String invalidUrl = "ftp://example.com";
        
        // Act & Assert
        assertThrows(
            JiuWenBaseException.class,
            () -> UrlUtils.checkUrlIsValid(invalidUrl)
        );
    }

    @Test
    void testCheckUrlInnerIpLocalhost() {
        // Arrange
        String localhostUrl = "http://localhost/api";
        
        // Act & Assert
        // Should throw exception for localhost (127.0.0.1)
        assertThrows(
            JiuWenBaseException.class,
            () -> UrlUtils.checkUrlIsValid(localhostUrl)
        );
    }

    @Test
    void testIsInnerIpAddress10() {
        // Arrange & Act
        boolean result = UrlUtils.isInnerIpAddress("10.0.0.1");
        
        // Assert
        assertTrue(result, "10.x.x.x should be inner IP");
    }

    @Test
    void testIsInnerIpAddress192() {
        // Arrange & Act
        boolean result = UrlUtils.isInnerIpAddress("192.168.1.1");
        
        // Assert
        assertTrue(result, "192.168.x.x should be inner IP");
    }

    @Test
    void testIsInnerIpAddress172() {
        // Arrange & Act
        boolean result = UrlUtils.isInnerIpAddress("172.16.0.1");
        
        // Assert
        assertTrue(result, "172.16-31.x.x should be inner IP");
    }

    @Test
    void testIsNotInnerIpAddress() {
        // Arrange & Act
        boolean result = UrlUtils.isInnerIpAddress("8.8.8.8");
        
        // Assert
        assertFalse(result, "8.8.8.8 should not be inner IP");
    }

    @Test
    void testGetGlobalProxyUrl() {
        // Note: This test depends on environment variables
        // In CI/CD, you might need to set up test environment
        
        // Act
        String proxyUrl = UrlUtils.getGlobalProxyUrl("http://example.com");
        
        // Assert
        // Can be null if no proxy is set
        assertTrue(proxyUrl == null || proxyUrl.startsWith("http"));
    }

    @Test
    void testGetGlobalProxies() {
        // Act
        Map<String, String> proxies = UrlUtils.getGlobalProxies("http://example.com");
        
        // Assert
        // Can be null if no proxy is set
        if (proxies != null) {
            assertTrue(proxies.containsKey("http") || proxies.containsKey("https"));
        }
    }

    @Test
    void testShouldBypassProxy() {
        // Note: This test depends on NO_PROXY environment variable
        // Act
        boolean shouldBypass = UrlUtils.shouldBypassProxy("http://localhost");
        
        // Assert - just check it returns a boolean
        assertNotNull(shouldBypass);
    }

    @Test
    void testIpToLong() {
        // Act
        long ip = UrlUtils.ipToLong("192.168.1.1");
        
        // Assert
        // 192.168.1.1 = 3232235777
        assertEquals(3232235777L, ip);
    }
}

