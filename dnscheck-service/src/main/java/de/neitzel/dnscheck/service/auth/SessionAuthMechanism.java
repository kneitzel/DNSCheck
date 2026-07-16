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

@ApplicationScoped
public class SessionAuthMechanism implements HttpAuthenticationMechanism {

    @Inject
    SessionService sessionService;

    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context, io.quarkus.security.identity.IdentityProviderManager identityProviderManager) {
        return sessionService.resolve(sessionService.readSessionCookie(context))
                .map(this::buildIdentity)
                .map(identity -> (SecurityIdentity) identity)
                .map(Uni.createFrom()::item)
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    private SecurityIdentity buildIdentity(SessionService.SessionData session) {
        Principal principal = () -> session.username();
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRole("authenticated")
                .build();
    }

    @Override
    public Uni<io.quarkus.vertx.http.runtime.security.ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends io.quarkus.security.identity.request.AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom()
                .item(new HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE, SessionService.COOKIE_NAME));
    }
}
