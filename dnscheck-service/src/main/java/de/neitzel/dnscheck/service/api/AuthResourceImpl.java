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

@ApplicationScoped
@Transactional
public class AuthResourceImpl implements AuthResource {

    private final OperatorAuthService operatorAuthService;

    private final OperatorRightsService operatorRightsService;

    private final RightsSummaryService rightsSummaryService;

    private final SessionService sessionService;

    private final RoutingContext routingContext;

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

    @Override
    public Response logout() {
        String sessionId = sessionService.readSessionCookie(routingContext);
        sessionService.invalidate(sessionId);
        return Response.noContent().cookie(clearSessionCookie()).build();
    }

    @Override
    public Response changePassword(ChangePasswordRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        operatorAuthService.changePassword(
                operator, request.getCurrentPassword(), request.getNewPassword());
        return Response.noContent().build();
    }

    @Override
    public RightsSummaryResponse getMyRights() {
        return RightsApiMapper.toRightsSummaryResponse(rightsSummaryService.getMyRights());
    }

    private static NewCookie clearSessionCookie() {
        return new NewCookie.Builder(SessionService.COOKIE_NAME)
                .value("")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
    }

    private static int sessionMaxAgeSeconds() {
        return 8 * 60 * 60;
    }
}
