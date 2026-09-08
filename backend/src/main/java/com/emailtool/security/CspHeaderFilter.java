package com.emailtool.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CspHeaderFilter extends OncePerRequestFilter {

    static final String APP_CSP =
            "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-src 'self'; form-action 'none'; base-uri 'none'";
    static final String BODY_CSP = "default-src 'none'; img-src data:; style-src 'none'; script-src 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean body = path != null && path.matches("/api/emails/\\d+/body");
        if (body) {
            response.setHeader("Content-Security-Policy", BODY_CSP);
        } else {
            response.setHeader("Content-Security-Policy", APP_CSP);
            response.setHeader("X-Frame-Options", "DENY");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        filterChain.doFilter(request, response);
    }
}
