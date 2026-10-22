package edu.unsw.core.config;

import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.filter.LogTraceFilter;
import edu.unsw.core.interceptor.LoginInterceptor;
import edu.unsw.core.interceptor.PermissionInterceptor;
import edu.unsw.core.interceptor.RepeatRequestInterceptor;
import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 20:09
 * @description mvc config
 */
@SpringBootConfiguration
public class WebConfigurer implements WebMvcConfigurer {
    private final RepeatRequestInterceptor repeatRequestInterceptor;
    private final LoginInterceptor loginInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebConfigurer(RepeatRequestInterceptor repeatRequestInterceptor, LoginInterceptor loginInterceptor, PermissionInterceptor permissionInterceptor) {
        this.repeatRequestInterceptor = repeatRequestInterceptor;
        this.loginInterceptor = loginInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    /**
     * Cross-domain configuration
     *
     * @param registry Default cross-domain configuration
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                // Permitted url
                .addMapping("/**")
                // Configure which raw domains are allowed to pass
                .allowedOriginPatterns("*")
                // Allow cookies
                .allowCredentials(true)
                // Set the information that the header can carry
                .allowedHeaders("*")
                // Cross-origin request methods are supported; filling in * in parentheses allows all requests to proceed
                .allowedMethods("*")
                // Headers that are allowed to be accessed by the front end
                .exposedHeaders("*")
                // Set the cross-domain expiration time in seconds
                .maxAge(3600);
    }

    /**
     * Registration log tracking filter
     *
     * @return Log tracking filters
     */
    @Bean
    public FilterRegistrationBean<LogTraceFilter> logTraceFilter() {
        FilterRegistrationBean<LogTraceFilter> logTraceFilterBean = new FilterRegistrationBean<>();
        logTraceFilterBean.setFilter(new LogTraceFilter());
        logTraceFilterBean.addUrlPatterns(
                "/*",
                RequestPrefixConstant.GENERAL_PREFIX + "/*",
                RequestPrefixConstant.STUDENT_PREFIX + "/*",
                RequestPrefixConstant.SUPERVISOR_PREFIX + "/*",
                RequestPrefixConstant.REGISTRAR_PREFIX + "/*"
        );
        logTraceFilterBean.setName("LogTraceFilter");
        logTraceFilterBean.setOrder(0);
        return logTraceFilterBean;
    }

    /**
     * Registration interceptor (notice the order of registration)
     *
     * @param registry Interceptor configuration
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Repeat Request Interceptor
        registry.addInterceptor(repeatRequestInterceptor)
                .addPathPatterns(
                        RequestPrefixConstant.GENERAL_PREFIX + "/**",
                        RequestPrefixConstant.STUDENT_PREFIX + "/**",
                        RequestPrefixConstant.SUPERVISOR_PREFIX + "/**",
                        RequestPrefixConstant.REGISTRAR_PREFIX + "/**"
                );

        // Login interceptor
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        RequestPrefixConstant.GENERAL_PREFIX + "/**",
                        RequestPrefixConstant.STUDENT_PREFIX + "/**",
                        RequestPrefixConstant.SUPERVISOR_PREFIX + "/**",
                        RequestPrefixConstant.REGISTRAR_PREFIX + "/**"
                ).excludePathPatterns(
                        RequestPrefixConstant.GENERAL_PREFIX + "/user/login",
                        RequestPrefixConstant.GENERAL_PREFIX + "/sse/subscribe/**",
                        RequestPrefixConstant.GENERAL_PREFIX + "/sse/disconnect/**",
                        "/**/*.html",
                        "/general/calendar/google/callback"

                );

        // Permission interceptor
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns(
                        RequestPrefixConstant.GENERAL_PREFIX + "/**",
                        RequestPrefixConstant.STUDENT_PREFIX + "/**",
                        RequestPrefixConstant.SUPERVISOR_PREFIX + "/**",
                        RequestPrefixConstant.REGISTRAR_PREFIX + "/**"
                )
                .excludePathPatterns(
                        RequestPrefixConstant.GENERAL_PREFIX + "/user/login",
                        RequestPrefixConstant.GENERAL_PREFIX + "/user/logout",
                        RequestPrefixConstant.GENERAL_PREFIX + "/sse/subscribe/**",
                        RequestPrefixConstant.GENERAL_PREFIX
                                + "/calendar/google/callback",
                        "/**/*.html"
);
    }

    @Override
    public void addResourceHandlers(@Nonnull ResourceHandlerRegistry registry) {
        String projectRoot = System.getProperty("user.dir");
        registry.addResourceHandler("/file/**").addResourceLocations("file:" + projectRoot + "/file/");
    }

}
