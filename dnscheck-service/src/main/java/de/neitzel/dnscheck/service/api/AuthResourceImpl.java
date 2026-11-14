package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.ChangePasswordRequest;
import de.neitzel.dnscheck.service.api.generated.model.LoginRequest;
import de.neitzel.dnscheck.service.api.generated.model.LoginResponse;
import de.neitzel.dnscheck.service.api.generated.model.RightsSummaryResponse;
import de.neitzel.dnscheck.service.api.generated.resources.AuthResource;
import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.RightsSummaryService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * HTTP API for operator sign-in, sign-out, password changes, and the current operator's rights.
 * Each call runs in one transaction.
 */
@ApplicationScoped
@Transactional
public class AuthResourceImpl implements AuthResource {

    /**
     * Checks the password and MFA code when an operator signs in or changes a password.
     */
    private final OperatorAuthService operatorAuthService;

    /**
     * Resolves the authenticated operator for a password change.
     */
    private final OperatorRightsService operatorRightsService;

    /**
     * Builds the current operator's effective global rights and zone rights.
     */
    private final RightsSummaryService rightsSummaryService;

    /**
     * Creates and invalidates the server-side operator session.
     */
    private final SessionService sessionService;

    /**
     * Exposes the current HTTP request so the session cookie can be read and written.
     */
    private final RoutingContext routingContext;

    /**
     * Creates the authentication API adapter.
     *
     * @param operatorAuthService Checks passwords and MFA codes for sign-in and password changes.
     * @param operatorRightsService Resolves the authenticated operator.
     * @param rightsSummaryService Builds the current operator's global rights and zone rights.
     * @param sessionService Creates and invalidates server-side sessions.
     * @param routingContext Carries the HTTP request used to read and write the session cookie.
     */
    @Inject
    public AuthResourceImpl(
            OperatorAuthService operatorAuthService,
            OperatorRightsService operatorRightsService,
            RightsSummaryService rightsSummaryService,
            SessionService sessionService,
            RoutingContext routingContext) {
        this.operatorAuthService = operatorAuthService;
        this.operatorRightsService = operatorRightsService;
        this.rightsSummaryService = rightsSummaryService;
        this.sessionService = sessionService;
        this.routingContext = routingContext;
    }

    /**
     * Handles POST /api/v1/auth/login by authenticating an operator and storing an HTTP-only session
     * cookie for eight hours. When MFA is active, the operator must also present an MFA code or a
     * recovery code.
     *
     * @param loginRequest Supplies the username, password, and optional MFA code or recovery code.
     * @return Authenticated operator username after the session cookie is set.
     * @throws NotAuthorizedException No enabled operator matches the username and password.
     */
    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        Operator operator = operatorAuthService
                .authenticate(loginRequest.getUsername(), loginRequest.getPassword(), loginRequest.getMfaCode())
                .orElseThrow(() -> new NotAuthorizedException("Invalid credentials"));

        String sessionId = sessionService.createSession(operator.id, operator.username);
        routingContext.response().addCookie(Cookie.cookie(SessionService.COOKIE_NAME, sessionId)
                .setPath("/")
                .setHttpOnly(true)
                .setMaxAge(sessionMaxAgeSeconds()));

        LoginResponse response = new LoginResponse();
        response.setUsername(operator.username);
        return response;
    }

    /**
     * Handles POST /api/v1/auth/logout by ending the current server-side session and clearing the
     * session cookie.
     *
     * @return HTTP 204 response that expires the session cookie.
     */
    @Override
    public Response logout() {
        String sessionId = sessionService.readSessionCookie(routingContext);
        sessionService.invalidate(sessionId);
        return Response.noContent().cookie(clearSessionCookie()).build();
    }

    /**
     * Handles POST /api/v1/auth/change-password by changing the password of the current operator.
     * Requires an authenticated operator. An incorrect current password is rejected.
     *
     * @param request Supplies the current password and the replacement password.
     * @return HTTP 204 response after the password is updated.
     */
    @Override
    public Response changePassword(ChangePasswordRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        operatorAuthService.changePassword(
                operator, request.getCurrentPassword(), request.getNewPassword());
        return Response.noContent().build();
    }

    /**
     * Handles GET /api/v1/auth/me/rights by returning the current operator's effective global rights
     * and zone rights.
     *
     * @return Rights summary for the authenticated operator.
     */
    @Override
    public RightsSummaryResponse getMyRights() {
        return RightsApiMapper.toRightsSummaryResponse(rightsSummaryService.getMyRights());
    }

    /**
     * Builds the session cookie that ends the browser session on logout.
     *
     * @return HTTP-only cookie with an empty value and a max age of zero.
     */
    private static NewCookie clearSessionCookie() {
        return new NewCookie.Builder(SessionService.COOKIE_NAME)
                .value("")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
    }

    /**
     * Returns the session cookie lifetime of eight hours.
     *
     * @return Session cookie lifetime in seconds.
     */
    private static int sessionMaxAgeSeconds() {
        return 8 * 60 * 60;
    }
}
