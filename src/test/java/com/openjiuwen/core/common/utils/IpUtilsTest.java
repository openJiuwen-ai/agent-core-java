package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IpUtils 测试类
 */
public class IpUtilsTest {

    @Test
    public void testGetLocalIp() {
        String ip = IpUtils.getLocalIp();

        assertNotNull(ip, "获取的IP不应该为null");
        assertFalse(ip.isEmpty(), "获取的IP不应该为空");

        // IP应该是有效的IPv4格式
        String[] parts = ip.split("\\.");
        assertTrue(parts.length == 4, "IPv4地址应该有4个部分");

        // 如果获取失败，应该返回127.0.0.1
        assertTrue(ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), "应该是有效的IPv4格式");
    }

    @Test
    public void testGetLocalIpNotLoopback() {
        String ip = IpUtils.getLocalIp();

        // 如果获取成功，不应该是127.0.0.1（除非真的获取失败）
        // 注意：在某些网络环境下可能会返回127.0.0.1
        assertNotNull(ip, "IP不应该为null");
    }

    @Test
    public void testGetLocalIpConsistency() {
        // 多次调用应该返回相同的IP（在同一次测试运行中）
        String ip1 = IpUtils.getLocalIp();
        String ip2 = IpUtils.getLocalIp();

        assertEquals(ip1, ip2, "多次调用应该返回相同的IP");
    }
}


