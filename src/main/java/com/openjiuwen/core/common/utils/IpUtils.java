package com.openjiuwen.core.common.utils;

import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * IP地址工具类
 * 
 * 从 Python ip_utils.py 转换
 * 提供本地IP地址获取功能
 */
public final class IpUtils {

    private IpUtils() {
        // 防止实例化
    }

    /**
     * 获取本地可用的IPv4地址（排除127.0.0.1）
     * 
     * 通过尝试连接外部地址来获取本机实际使用的网络接口IP
     *
     * @return 本地IPv4地址，如果获取失败返回"127.0.0.1"
     */
    public static String getLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // 连接到外部地址（不会实际发送数据）
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            String ip = socket.getLocalAddress().getHostAddress();
            return ip;
        } catch (Exception e) {
            // 如果发生异常，返回localhost
            return "127.0.0.1";
        }
    }
}


