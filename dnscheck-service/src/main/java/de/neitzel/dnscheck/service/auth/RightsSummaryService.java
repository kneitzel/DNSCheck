package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class RightsSummaryService {

    @Inject
    OperatorRightsService operatorRightsService;

    @Transactional
    public RightsSummaryBuilder.RightsSummary getMyRights() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return RightsSummaryBuilder.forOperator(operator, true);
    }

    @Transactional
    public RightsSummaryBuilder.RightsSummary getOperatorRights(Long operatorId) {
        operatorRightsService.requireAdmin();
        return RightsSummaryBuilder.forOperator(findOperator(operatorId), false);
    }

    @Transactional
    public RightsSummaryBuilder.RightsSummary getGroupRights(Long groupId) {
        operatorRightsService.requireAdmin();
        return RightsSummaryBuilder.forGroup(findGroup(groupId));
    }

    private static Operator findOperator(Long operatorId) {
        Operator operator = Operator.findById(operatorId);
        if (operator == null) {
            throw new NotFoundException();
        }
        return operator;
    }

    private static Group findGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new NotFoundException();
        }
        return group;
    }
}
