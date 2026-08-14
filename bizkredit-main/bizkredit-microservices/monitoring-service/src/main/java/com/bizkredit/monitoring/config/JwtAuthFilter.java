package com.bizkredit.monitoring.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Stateless JWT validation for this microservice.
// This service has no local users table - it trusts the signature on the
// token (signed with the shared jwt.secret) and builds the Authentication
// directly from the "role" claim that auth-service embedded at login time.
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            // Extract JWT token by removing "Bearer " prefix
            String jwt = authHeader.substring(7);

            try {
                String username = jwtUtil.extractUsername(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null
                        && !jwtUtil.extractExpiration(jwt).before(new java.util.Date())) {

                    String role = jwtUtil.extractClaim(jwt, claims -> claims.get("role", String.class));
                    List<GrantedAuthority> authorities = role != null
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : List.of();

                    var authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );

                    // Add request details like IP address and session
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Store authentication in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("JWT authenticated user: {} with role: {}", username, role);
                }

            } catch (Exception e) {

                log.error("JWT authentication failed", e);
            }
        }

        // Continue request processing
        filterChain.doFilter(request, response);
    }
}
