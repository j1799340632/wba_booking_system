package edu.unsw.core.properties;

import lombok.Data;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 22:14
 * @description Redisson connection parameters
 */
@Data
@ConfigurationProperties("redisson")
public class RedissonProperties {
    /**
     * redis:// can also be used to enable SSL connections
     * Multiple cluster addresses are separated by commas
     */
    private String[] uri;

    /**
     * Do not set this option if there is no password or leave it blank
     */
    private String password;

    /**
     * Command wait timeout, default 3000, unit: milliseconds
     */
    private Integer timeout;

    /**
     * Connection timeout, default 10000, unit: milliseconds
     */
    private Integer connectionTimeout;

    /**
     * Connection idle timeout, default 10000, unit: milliseconds
     */
    private Integer idleConnectionTimeout;

    /**
     * Minimum number of idle connections, default 24 (valid on a single machine)
     */
    private Integer connectionMinimumIdleSize;

    /**
     * Minimum number of idle connections for the master node, default 24 (for clusters only)
     */
    private Integer masterConnectionMinimumIdleSize;

    /**
     * Minimum number of idle slave connections, default 24 (for clusters only)
     */
    private Integer slaveConnectionMinimumIdleSize;

    /**
     * Read mode, default value: SLAVE (readonly from the server node). Note: Reading data from the server node means that at least two nodes hold the data, ensuring high data availability
     */
    private ReadMode readMode;

    /**
     * The load balancing mode for subscription operations. The default value is SLAVE (subscribing only from slave service nodes)
     */
    private SubscriptionMode subscriptionMode;
}
