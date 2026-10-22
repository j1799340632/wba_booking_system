package edu.unsw.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.net.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/21 13:09
 * @description Get the IP address of the device requesting the interface
 */
public class IpUtils {
    private IpUtils() {

    }

    private static final Logger logger = LoggerFactory.getLogger(IpUtils.class);
    private static final String LOCAL_IPV4 = "127.0.0.1";
    private static final String LOCAL_IPV6 = "::1";

    private static final Predicate<String> INVALID_IP = s ->
            s == null || s.isBlank() || "unknown".equalsIgnoreCase(s.trim());

    /**
     * Get the source IP address of the request
     * When using reverse proxy software such as Nginx, the IP address cannot be obtained via request.getRemoteAddr(). Instead, you need to configure Nginx with settings such as proxy_set_header X-real-ip $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; etc.
     * If a multi-level reverse proxy is used, the value of X-Forwarded-For is not limited to one; it typically contains a comma-separated list of IP addresses, representing the chain of IP addresses of the proxy servers through which the request passes.
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (Objects.isNull(request)) {
            return "";
        }

        // X-Real-IP is preferred because it is typically forcibly overwritten by the Nginx instance closest to the service, making it the most difficult for clients to forge
        String ip = request.getHeader("X-Real-IP");

        if (INVALID_IP.test(ip)) {
            ip = request.getHeader("X-Forwarded-For");
            if (!INVALID_IP.test(ip)) {
                // Handling multi-level reverse proxies, securely extracting the first legitimate real client IP
                String[] ipArray = ip.split(",");
                for (String s : ipArray) {
                    if (!INVALID_IP.test(s)) {
                        ip = s.trim();
                        break;
                    }
                }
            }
        }
        if (INVALID_IP.test(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (INVALID_IP.test(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (INVALID_IP.test(ip)) {
            // The getRemoteAddr() method returns the IP address of the client that directly connected to the server, which is valid when there is no proxy
            ip = request.getRemoteAddr();
            // Handling local IPv6 loopback mapping
            if ("0:0:0:0:0:0:0:1".equals(ip) || LOCAL_IPV6.equals(ip)) {
                ip = LOCAL_IPV4;
            }
        }

        return ip;
    }

    /**
     * Determine if the IP address is a local IP address
     *
     * @param ip ipv4 string
     * @return Is it a local IP address?
     */
    public static boolean internalIp(String ip) {
        if (!StringUtils.hasLength(ip)) {
            return false;
        }
        String cleanIp = ip.trim();
        if (LOCAL_IPV4.equals(cleanIp) || LOCAL_IPV6.equals(cleanIp)) {
            return true;
        }
        byte[] addr = textToNumericFormatV4(cleanIp);
        return addr.length == 4 && internalIp(addr);
    }

    /**
     * Determine if the IP address is a local IP address
     *
     * @param addr ipv4 bytes
     * @return Is it a local IP address?
     */
    private static boolean internalIp(byte[] addr) {
        byte b0 = addr[0];
        byte b1 = addr[1];
        // 10.x.x.x/8
        final byte section1 = 0x0A;
        // 172.16.x.x/12
        final byte section2 = (byte) 0xAC;
        byte section3 = (byte) 0x10;
        byte section4 = (byte) 0x1F;
        // 192.168.x.x/16
        final byte section5 = (byte) 0xC0;
        byte section6 = (byte) 0xA8;
        return switch (b0) {
            case section1 -> true;
            case section2 -> b1 >= section3 && b1 <= section4;
            case section5 -> b1 == section6;
            default -> false;
        };
    }

    /**
     * Convert IPv4 address to bytes
     *
     * @param text IPv4 string
     * @return IPv4 bytes
     */
    public static byte[] textToNumericFormatV4(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String[] elements = text.split("\\.", -1);
        byte[] bytes = new byte[4];
        try {
            long l;
            switch (elements.length) {
                case 1 -> {
                    l = Long.parseLong(elements[0]);
                    if ((l < 0L) || (l > 4294967295L)) {
                        return new byte[0];
                    }
                    bytes[0] = (byte) (int) (l >> 24 & 0xFF);
                    bytes[1] = (byte) (int) ((l & 0xFFFFFF) >> 16 & 0xFF);
                    bytes[2] = (byte) (int) ((l & 0xFFFF) >> 8 & 0xFF);
                    bytes[3] = (byte) (int) (l & 0xFF);
                }
                case 2 -> {
                    l = Integer.parseInt(elements[0]);
                    if ((l < 0L) || (l > 255L)) {
                        return new byte[0];
                    }
                    bytes[0] = (byte) (int) (l & 0xFF);
                    l = Integer.parseInt(elements[1]);
                    if ((l < 0L) || (l > 16777215L)) {
                        return new byte[0];
                    }
                    bytes[1] = (byte) (int) (l >> 16 & 0xFF);
                    bytes[2] = (byte) (int) ((l & 0xFFFF) >> 8 & 0xFF);
                    bytes[3] = (byte) (int) (l & 0xFF);
                }
                case 3 -> {
                    for (int i = 0; i < 2; ++i) {
                        l = Integer.parseInt(elements[i]);
                        if ((l < 0L) || (l > 255L)) {
                            return new byte[0];
                        }
                        bytes[i] = (byte) (int) (l & 0xFF);
                    }
                    l = Integer.parseInt(elements[2]);
                    if ((l < 0L) || (l > 65535L)) {
                        return new byte[0];
                    }
                    bytes[2] = (byte) (int) (l >> 8 & 0xFF);
                    bytes[3] = (byte) (int) (l & 0xFF);
                }
                case 4 -> {
                    for (int i = 0; i < 4; ++i) {
                        l = Integer.parseInt(elements[i]);
                        if ((l < 0L) || (l > 255L)) {
                            return new byte[0];
                        }
                        bytes[i] = (byte) (int) (l & 0xFF);
                    }
                }
                default -> {
                    return new byte[0];
                }
            }
        } catch (NumberFormatException e) {
            logger.error("Failed to convert IPv4 address to bytes", e);
            return new byte[0];
        }
        return bytes;
    }

    /**
     * Get the local network card IPv4 address
     *
     * @return Local network card IPv4 address string
     */
    public static String getLocalIpv4() {
        InetAddress commonIpv4 = getExactLocalIpv4();
        return commonIpv4 != null ? commonIpv4.getHostAddress() : LOCAL_IPV4;
    }

    /**
     * Get the network card IPv4 address
     *
     * @return ipv4 address
     */
    public static InetAddress getExactLocalIpv4() {
        try {
            InetAddress candidateAddress = null;
            // Traverse all network cards
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                // Exclude loopback network interfaces, network interfaces that are not running, and virtual network interfaces (such as unusable network interfaces other than Docker bridged network interfaces)
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddrs = iface.getInetAddresses();
                while (inetAddrs.hasMoreElements()) {
                    InetAddress inetAddr = inetAddrs.nextElement();
                    // Only concerned with IPv4 and non-constant loopback addresses
                    if (!inetAddr.isLoopbackAddress() && inetAddr.getHostAddress().indexOf(':') == -1) {
                        if (inetAddr.isSiteLocalAddress()) {
                            // If it is a local address (such as 192.168.x.x or 10.x.x.x), return directly
                            return inetAddr;
                        } else if (candidateAddress == null) {
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            return candidateAddress != null ? candidateAddress : InetAddress.getLocalHost();
        } catch (Exception e) {
            // Extreme tip: If all network card resolutions fail, return to the default localhost
            logger.error("Get ipv4 failed, return localhost", e);
            try {
                return InetAddress.getLocalHost();
            } catch (Exception _) {
                return null;
            }
        }
    }

    /**
     * Get the local network card IPv6 address
     *
     * @return Local network card IPv6 address string
     */
    public static String getLocalIpv6() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                    InetAddress inetAddress = interfaceAddress.getAddress();
                    // `!inetAddress.isLinkLocalAddress()` filters Link-Local addresses (internal network addresses starting with fe80) to ensure that the obtained addresses are as meaningful as possible
                    if (inetAddress instanceof Inet6Address && !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("Failed to extract local IPv6 address", e);
        }
        return LOCAL_IPV6;
    }

}
