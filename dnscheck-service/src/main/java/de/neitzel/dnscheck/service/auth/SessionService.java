package de.neitzel.dnscheck.service.auth;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores server-side sessions that map a random session identifier to an operator.
 * Sessions live in memory for this process and are not written to the database.
 */
@ApplicationScoped
public class SessionService {

    /**
     * Name of the cookie that carries the session identifier. The name is {@code DNSCHECK_SESSION}.
     */
    public static final String COOKIE_NAME = "DNSCHECK_SESSION";

    /**
     * Operator identity stored for one server-side session.
     */
    public record SessionData(
            /**
             * Persisted identifier of the signed-in operator.
             */
            long operatorId,
            /**
             * Username of the signed-in operator, copied into the security identity.
             */
            String username) {}

    /**
     * Source of random bytes for new session identifiers.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * In-memory map from session identifier to the signed-in operator. Entries are not persisted.
     */
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Stores a new session for an operator.
     *
     * @param operatorId Persisted identifier of the operator.
     * @param username Username stored in the session and later used as the principal name.
     * @return The new session identifier. Callers treat it as secret material.
     */
    public String createSession(long operatorId, String username) {
        String sessionId = generateSessionId();
        sessions.put(sessionId, new SessionData(operatorId, username));
        return sessionId;
    }

    /**
     * Looks up the operator stored for a session identifier.
     *
     * @param sessionId Session identifier from the session cookie.
     * @return The session, or empty when {@code sessionId} is missing, blank, or unknown.
     */
    public Optional<SessionData> resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Removes a session so the operator must sign in again.
     *
     * @param sessionId Session identifier to drop. A null value removes nothing.
     */
    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    /**
     * Reads the session identifier from the request cookie.
     *
     * @param context Request that may contain the session cookie.
     * @return The cookie value, or null when {@code context} or the cookie is absent.
     */
    public String readSessionCookie(io.vertx.ext.web.RoutingContext context) {
        if (context == null) {
            return null;
        }
        io.vertx.core.http.Cookie cookie = context.request().getCookie(COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * Removes every in-memory session.
     */
    void clearAll() {
        sessions.clear();
    }

    /**
     * Creates a random session identifier.
     *
     * @return The new identifier. Callers treat it as secret material.
     */
    private String generateSessionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
