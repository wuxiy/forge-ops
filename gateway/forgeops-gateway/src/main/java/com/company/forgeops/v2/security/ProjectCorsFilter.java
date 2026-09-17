package com.company.forgeops.v2.security;

import com.company.forgeops.v2.registry.ProjectCatalog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cross-origin browser access is allowed only by the current project's validated Registry entry. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProjectCorsFilter extends OncePerRequestFilter {

    private final ProjectCatalog catalog;

    public ProjectCorsFilter(ProjectCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v2/projects/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null) {
            chain.doFilter(request, response);
            return;
        }
        String[] segments = request.getRequestURI().split("/");
        if (segments.length < 5 || !catalog.resolve(segments[4]).map(project -> project.browserOrigins().contains(origin)).orElse(false)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        chain.doFilter(request, response);
    }
}
