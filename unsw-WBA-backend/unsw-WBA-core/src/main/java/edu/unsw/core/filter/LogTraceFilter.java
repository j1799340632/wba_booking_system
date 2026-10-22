package edu.unsw.core.filter;

import edu.unsw.core.utils.mdc.MdcUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 20:07
 * @description Log tracking filters
 */
public class LogTraceFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, @Nonnull HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // If there's a call from a higher level, use the ID from that level; otherwise, construct a new one
        MdcUtils.setTraceIdIfAbsent(request.getHeader(MdcUtils.TRACE_ID));
        filterChain.doFilter(request, response);
        // Clear the current thread mdc
        MdcUtils.clear();
    }
}
