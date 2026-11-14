package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

/**
 * Loads effective global rights and supplemental zone grants for the signed-in operator and, for an admin, for
 * any operator or group.
 */
@ApplicationScoped
public class RightsSummaryService {

    /**
     * Resolves the signed-in operator and enforces the admin global right for summaries of other accounts.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Returns the signed-in operator's effective rights, with grants marked not editable.
     *
     * @return The operator's global rights and supplemental zone grants, including rights inherited from groups.
     * @throws jakarta.ws.rs.NotAuthorizedException When no operator session is present.
     */
    @Transactional
    public RightsSummaryBuilder.RightsSummary getMyRights() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return RightsSummaryBuilder.forOperator(operator, true);
    }

    /**
     * Returns one operator's effective rights for an admin. Direct grants are marked editable.
     *
     * @param operatorId Persisted identifier of the operator.
     * @return That operator's global rights and supplemental zone grants, including rights inherited from groups.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no operator has {@code operatorId}.
     */
    @Transactional
    public RightsSummaryBuilder.RightsSummary getOperatorRights(Long operatorId) {
        operatorRightsService.requireAdmin();
        return RightsSummaryBuilder.forOperator(findOperator(operatorId), false);
    }

    /**
     * Returns one group's directly assigned global rights and supplemental zone grants for an admin.
     *
     * @param groupId Persisted identifier of the group.
     * @return The group's rights summary.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no group has {@code groupId}.
     */
    @Transactional
    public RightsSummaryBuilder.RightsSummary getGroupRights(Long groupId) {
        operatorRightsService.requireAdmin();
        return RightsSummaryBuilder.forGroup(findGroup(groupId));
    }

    /**
     * Loads an operator or rejects the call when the identifier is unknown.
     *
     * @param operatorId Persisted identifier of the operator.
     * @return The matching operator.
     * @throws NotFoundException When no operator has {@code operatorId}.
     */
    private static Operator findOperator(Long operatorId) {
        Operator operator = Operator.findById(operatorId);
        if (operator == null) {
            throw new NotFoundException();
        }
        return operator;
    }

    /**
     * Loads a group or rejects the call when the identifier is unknown.
     *
     * @param groupId Persisted identifier of the group.
     * @return The matching group.
     * @throws NotFoundException When no group has {@code groupId}.
     */
    private static Group findGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new NotFoundException();
        }
        return group;
    }
}
