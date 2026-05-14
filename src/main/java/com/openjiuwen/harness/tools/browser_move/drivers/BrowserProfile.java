package com.openjiuwen.harness.tools.browser_move.drivers;

/**
 * Minimal browser profile used by the managed browser driver.
 */
public class BrowserProfile {
    private final String name;
    private final String driverType;
    private final String cdpUrl;
    private final String userDataDir;
    private final int debugPort;
    private final String host;

    public BrowserProfile(String name, String driverType, String cdpUrl, String userDataDir, int debugPort, String host) {
        this.name = name;
        this.driverType = driverType;
        this.cdpUrl = cdpUrl;
        this.userDataDir = userDataDir;
        this.debugPort = debugPort;
        this.host = host;
    }

    public String getName() { return name; }
    public String getDriverType() { return driverType; }
    public String getCdpUrl() { return cdpUrl; }
    public String getUserDataDir() { return userDataDir; }
    public int getDebugPort() { return debugPort; }
    public String getHost() { return host; }
}
