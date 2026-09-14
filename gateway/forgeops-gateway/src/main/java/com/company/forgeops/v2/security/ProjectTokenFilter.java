package com.company.forgeops.v2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Every 2.0 API request requires a signed, short-lived project-scoped bearer token. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProjectTokenFilter extends OncePerRequestFilter {

    private final ProjectTokenService tokens;

    public ProjectTokenFilter(ProjectTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            reject(response);
            return;
        }
        try {
            CallerIdentity.set(tokens.verify(authorization.substring("Bearer ".length())));
            chain.doFilter(request, response);
        } catch (TokenValidationException invalid) {
            reject(response);
        } finally {
            CallerIdentity.clear();
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\"}");
    }
}
