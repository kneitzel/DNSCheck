package de.neitzel.dnscheck.service.auth;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.Principal;
import java.util.Set;

/**
 * Authenticates an HTTP request from the server-side session cookie and builds the operator's security identity.
 */
@ApplicationScoped
public class SessionAuthMechanism implements HttpAuthenticationMechanism {

    /**
     * Resolves the session cookie to the signed-in operator.
     */
    @Inject
    SessionService sessionService;

    /**
     * Resolves the session cookie and returns the operator identity.
     * An unknown or missing session yields an empty result so other mechanisms can run.
     *
     * @param context Request that may carry the session cookie.
     * @param identityProviderManager Identity-provider manager required by the mechanism contract. It is not used,
     *     because the session already names the operator.
     * @return The operator identity, or an empty asynchronous result when no valid session is present.
     */
    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context, io.quarkus.security.identity.IdentityProviderManager identityProviderManager) {
        return sessionService.resolve(sessionService.readSessionCookie(context))
                .map(this::buildIdentity)
                .map(identity -> (SecurityIdentity) identity)
                .map(Uni.createFrom()::item)
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    /**
     * Builds an authenticated identity whose principal name is the operator username stored in the session.
     *
     * @param session Resolved server-side session.
     * @return The security identity for that operator.
     */
    private SecurityIdentity buildIdentity(SessionService.SessionData session) {
        Principal principal = () -> session.username();
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRole("authenticated")
                .build();
    }

    /**
     * Returns no authentication challenge. Session authentication does not ask the browser for credentials.
     *
     * @param context Request that failed authentication. It is not read.
     * @return An empty asynchronous result.
     */
    @Override
    public Uni<io.quarkus.vertx.http.runtime.security.ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    /**
     * Returns no credential types. The session identifier arrives in a cookie rather than an authentication request.
     *
     * @return An empty set of credential types.
     */
    @Override
    public Set<Class<? extends io.quarkus.security.identity.request.AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    /**
     * Reports that the operator session is carried in the session cookie.
     *
     * @param context Request whose transport is described. It is not read.
     * @return Cookie transport for the session cookie name.
     */
    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom()
                .item(new HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE, SessionService.COOKIE_NAME));
    }
}
