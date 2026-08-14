package com.bizkredit.credit.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    // Generate token without extra claims
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, Map.of()); // empty claims
    }

    /**
     * Mints a short-lived internal token for calls where the real caller's
     * role wouldn't be permitted on the downstream endpoint - specifically
     * the role-based notification broadcast (GET /api/users/role/{role} on
     * auth-service only permits ADMIN/RELATIONSHIP_MANAGER, but
     * makeDecision() - which triggers the broadcast - is also callable by
     * UNDERWRITING_MANAGER). Signed with the same shared jwt.secret every
     * service already trusts, so the receiving JwtAuthFilter accepts it
     * exactly like a normal user token: it only checks the signature and
     * reads the "role" claim, it never looks the subject up against a
     * real user.
     */
    public String generateSystemToken(String subject, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 60_000); // 60s - one call chain
        return Jwts.builder()
                .claims(Map.of("role", role))
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey())
                .compact();
    }

    // Generate JWT token
    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .claims(claims)               // custom data
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey())       // sign token
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return userDetails.getUsername().equals(extractUsername(token))
                && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Generic method to extract any claim
    public <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        return claimResolver.apply(extractAllClaims(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Parse token and get all claims
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())  // verify signature
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Create signing key from secret
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
