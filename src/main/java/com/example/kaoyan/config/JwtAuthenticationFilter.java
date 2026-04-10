package com.example.kaoyan.config;

import com.example.kaoyan.util.AgentDebugLog;
import com.example.kaoyan.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean hasBearer = header != null && header.startsWith("Bearer ");
        boolean tokenValid = false;
        if (hasBearer) {
            String token = header.substring(7);
            tokenValid = jwtUtil.validateToken(token);
            if (tokenValid) {
                String username = jwtUtil.getUsernameFromToken(token);
                Long userId = jwtUtil.getUserIdFromToken(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/api/chat")) {
            // #region agent log
            String safe = uri.replace("\"", "'");
            AgentDebugLog.ndjson("H0", "JwtAuthenticationFilter", "chat path",
                    "{\"uri\":\"" + safe + "\",\"method\":\"" + request.getMethod() + "\",\"hasBearer\":"
                            + hasBearer + ",\"tokenValid\":" + tokenValid + "}");
            // #endregion
        }

        filterChain.doFilter(request, response);
    }
}
