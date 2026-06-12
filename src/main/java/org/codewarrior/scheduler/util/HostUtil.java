package org.codewarrior.scheduler.util;

import java.net.InetAddress;

public class HostUtil {
    public static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}
