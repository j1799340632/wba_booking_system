package edu.unsw.core.config.mybatisplus;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import edu.unsw.core.utils.IpUtils;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/21 12:02
 * @description MybatisPlus plugin configuration
 */
@SpringBootConfiguration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // Pagination plugin
        interceptor.addInnerInterceptor(paginationInnerInterceptor());
        // Optimistic Locking Plugin
        interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * Pagination plugin
     */
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        return new PaginationInnerInterceptor();
    }

    /**
     * Optimistic Locking Plugin
     */
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }

    /**
     * Use network card information to bind the snowflake generator
     */
    @Primary
    @Bean
    public IdentifierGenerator idGenerator() {
        return new DefaultIdentifierGenerator(IpUtils.getExactLocalIpv4());
    }

    /**
     * Metaobject field populates controller
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MyMetaObjectHandler();
    }

}
