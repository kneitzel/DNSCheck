package de.neitzel.dnscheck.service.auth;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SessionService {

    public static final String COOKIE_NAME = "DNSCHECK_SESSION";

    public record SessionData(long operatorId, String username) {}

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public String createSession(long operatorId, String username) {
        String sessionId = generateSessionId();
        sessions.put(sessionId, new SessionData(operatorId, username));
        return sessionId;
    }

    public Optional<SessionData> resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public String readSessionCookie(io.vertx.ext.web.RoutingContext context) {
        if (context == null) {
            return null;
        }
        io.vertx.core.http.Cookie cookie = context.request().getCookie(COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }

    void clearAll() {
        sessions.clear();
    }

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
