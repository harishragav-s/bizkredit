package com.bizkredit.auth.config;

import com.bizkredit.auth.service.CustomUserDetailsService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    private static final String SYSTEM_SUBJECT_PREFIX = "system-";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

         String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String jwt = authHeader.substring(7);

            try {
                String username = jwtUtil.extractUsername(jwt);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    if (username.startsWith(SYSTEM_SUBJECT_PREFIX)) {

                        if (!jwtUtil.extractExpiration(jwt).before(new java.util.Date())) {
                            String role = jwtUtil.extractClaim(jwt, claims -> claims.get("role", String.class));
                            List<GrantedAuthority> authorities = role != null
                                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                                    : List.of();

                            var authToken = new UsernamePasswordAuthenticationToken(
                                    username, null, authorities);

                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authToken);

                            log.debug("JWT authenticated system caller: {} with role: {}", username, role);
                        }
                    }
                    else {
                        var userDetails = userDetailsService.loadUserByUsername(username);
                        if (jwtUtil.isTokenValid(jwt, userDetails)) {
                            var authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request)
                            );

                            SecurityContextHolder.getContext().setAuthentication(authToken);

                            log.debug("JWT authenticated user: {}", username);
                        }
                    }
                }

            } catch (Exception e) {

                log.error("JWT authentication failed", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}

