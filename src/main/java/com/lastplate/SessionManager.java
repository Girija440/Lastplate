package com.lastplate;

import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active user sessions in memory using secure cryptographically random tokens.
 * Enforces role-based access control on backend endpoints.
 */
public class SessionManager {

    private static final long SESSION_DURATION_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final Map<String, SessionRecord> SESSIONS = new ConcurrentHashMap<>();

    private static class SessionRecord {
        final User user;
        long lastAccess;

        SessionRecord(User user) {
            this.user = user;
            this.lastAccess = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - lastAccess) > SESSION_DURATION_MS;
        }

        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
    }

    /**
     * Creates a new session for the given user.
     * @param user The logged-in user
     * @return Unique session token
     */
    public static String createSession(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(token, new SessionRecord(user));
        return token;
    }

    /**
     * Retrieves the user associated with the token if active and not expired.
     */
    public static User getSessionUser(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        SessionRecord record = SESSIONS.get(token);
        if (record == null) {
            return null;
        }
        if (record.isExpired()) {
            SESSIONS.remove(token);
            return null;
        }
        record.touch();
        return record.user;
    }

    /**
     * Destroys an existing session (logout).
     */
    public static void removeSession(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }

    /**
     * Extracts session token from HTTP request (Cookie, Authorization header, or X-Session-Token header).
     */
    public static String extractToken(HttpExchange exchange) {
        // 1. Check Authorization header: Bearer <token>
        List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String val = authHeaders.get(0).trim();
            if (val.toLowerCase().startsWith("bearer ")) {
                return val.substring(7).trim();
            }
        }

        // 2. Check X-Session-Token header
        List<String> xTokens = exchange.getRequestHeaders().get("X-Session-Token");
        if (xTokens != null && !xTokens.isEmpty()) {
            return xTokens.get(0).trim();
        }

        // 3. Check Cookie header: LP_SESSION=<token>
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null && !cookies.isEmpty()) {
            for (String cookieHeader : cookies) {
                String[] parts = cookieHeader.split(";");
                for (String part : parts) {
                    String[] kv = part.trim().split("=");
                    if (kv.length == 2 && "LP_SESSION".equalsIgnoreCase(kv[0].trim())) {
                        return kv[1].trim();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Validates that the request has an active session.
     * Returns User if valid, null otherwise.
     */
    public static User authenticateRequest(HttpExchange exchange) {
        String token = extractToken(exchange);
        return getSessionUser(token);
    }
}

