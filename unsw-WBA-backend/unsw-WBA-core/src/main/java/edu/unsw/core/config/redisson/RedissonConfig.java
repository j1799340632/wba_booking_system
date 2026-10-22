package edu.unsw.core.config.redisson;

import edu.unsw.core.config.jackson.ObjectMapperConfig;
import edu.unsw.core.properties.RedissonProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.*;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 22:19
 * @description redisson 配置类
 */
@SpringBootConfiguration
@EnableConfigurationProperties(RedissonProperties.class)
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800, flushMode = FlushMode.IMMEDIATE, saveMode = SaveMode.ON_GET_ATTRIBUTE)
public class RedissonConfig {
    private final RedissonProperties redissonProperties;

    public RedissonConfig(RedissonProperties redissonProperties) {
        this.redissonProperties = redissonProperties;
    }

    private RedissonClient buildRedissonClient(int databaseNo, String clientName) {
        // Get the number of CPU threads
        int cpuProcessorSize = Runtime.getRuntime().availableProcessors();
        Config config = new Config()
                // Communication mode, default NIO
                .setTransportMode(TransportMode.NIO)
                // The number of threads shared among all listeners of the RTopic object, the call handlers of the RRemoteService object, and the RExecutorService tasks
                .setThreads(cpuProcessorSize)
                // Redisson uses a shared number of threads across all Redis clients
                .setNettyThreads(cpuProcessorSize)
                // Serialization
                .setCodec(new JsonJacksonCodec(new ObjectMapperConfig().myObjectMapper()));
        // Do not set this option if the password is blank
        if (StringUtils.hasLength(redissonProperties.getPassword())) {
            config.setCredentialsResolver(_ -> CompletableFuture.completedFuture(new Credentials(null, redissonProperties.getPassword())));
        }
        // Single machine configuration
        if (redissonProperties.getUri().length == 1) {
            SingleServerConfig singleServerConfig = config.useSingleServer();
            // "redis://" can also be used, or "rediss://" can be used to enable SSL connections
            singleServerConfig.setAddress(redissonProperties.getUri()[0]);
            singleServerConfig.setDatabase(databaseNo);
            // Set Redis key prefix
            //singleServerConfig.setNameMapper(new RedisKeyPrefixHandler(RedisConstant.VISITOR_PREFIX))
            // The client name (the client name displayed in the Redis node) cannot be Chinese and cannot contain spaces or illegal characters
            singleServerConfig.setClientName(clientName);
            // Command wait timeout, default 3000, unit: milliseconds
            singleServerConfig.setTimeout(redissonProperties.getTimeout());
            // Connection timeout, default 10000, unit: milliseconds
            singleServerConfig.setConnectTimeout(redissonProperties.getConnectionTimeout());
            // Connection idle timeout, default 10000, unit: milliseconds
            singleServerConfig.setIdleConnectionTimeout(redissonProperties.getIdleConnectionTimeout());
            // Minimum number of idle connections, default 24
            singleServerConfig.setConnectionMinimumIdleSize(redissonProperties.getConnectionMinimumIdleSize());
            // Publish and Subscribe Connection Pool Size
            singleServerConfig.setSubscriptionConnectionPoolSize(cpuProcessorSize);
            // Connection pool size
            singleServerConfig.setConnectionPoolSize(cpuProcessorSize);
        } else {
            // Cluster configuration
            ClusterServersConfig clusterServersConfig = config.useClusterServers();
            clusterServersConfig
                    // Multiple cluster addresses are separated by commas.
                    .addNodeAddress(redissonProperties.getUri())
                    // The client name (the client name displayed in the Redis node) cannot be Chinese and cannot contain spaces or illegal characters
                    .setClientName(clientName)
                    // Command wait timeout, default 3000, unit: milliseconds
                    .setTimeout(redissonProperties.getTimeout())
                    // Connection timeout, default 10000, unit: milliseconds
                    .setConnectTimeout(redissonProperties.getConnectionTimeout())
                    // Connection idle timeout, default 10000, unit: milliseconds
                    .setIdleConnectionTimeout(redissonProperties.getIdleConnectionTimeout())
                    // Publish and Subscribe Connection Pool Size
                    .setSubscriptionConnectionPoolSize(cpuProcessorSize)
                    // Minimum number of idle connections for the master node, default 24
                    .setMasterConnectionMinimumIdleSize(redissonProperties.getMasterConnectionMinimumIdleSize())
                    // master connection pool size
                    .setMasterConnectionPoolSize(cpuProcessorSize)
                    // Minimum number of idle slave connections, default 24
                    .setSlaveConnectionMinimumIdleSize(redissonProperties.getSlaveConnectionMinimumIdleSize())
                    // slave connection pool size
                    .setSlaveConnectionPoolSize(cpuProcessorSize)
                    // Read mode, default value: SLAVE (readonly from the server node). Note: Reading data from the server node means that at least two nodes hold the data, ensuring high data availability
                    .setReadMode(redissonProperties.getReadMode())
                    // The load balancing mode of the subscription operation, default value: SLAVE (only subscribes from the service node)
                    .setSubscriptionMode(redissonProperties.getSubscriptionMode())
                    // The default load balancing algorithm is round-robin
                    .setLoadBalancer(new RoundRobinLoadBalancer());
        }

        return Redisson.create(config);
    }

    @Bean(destroyMethod = "shutdown")
    @Primary
    public RedissonClient defaultRedissonClient() {
        return buildRedissonClient(0, "defaultRedissonClient");
    }

    /**
     * Taking over the session requires configuring the factory
     * @return RedissonConnectionFactory
     */
    @Bean
    public RedissonConnectionFactory redissonConnectionFactory() {
        return new RedissonConnectionFactory(defaultRedissonClient());
    }

}
