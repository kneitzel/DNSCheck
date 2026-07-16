# Operator login MFA

Status: ready-for-agent

## Problem Statement

Operators can protect DNSCheck only with a username and password plus a server-side session. That is sufficient for basic access control, but it leaves operator accounts exposed if a password is stolen, reused, or phished. DNSCheck needs a way for operators to opt into stronger sign-in security without changing the existing session-based auth model for everyone.

## Solution

Add optional multi-factor authentication for operator sign-in. An operator can enroll a second verification factor, confirm it through a staged setup flow, and then use that factor on future logins. The first version is login-only: MFA is required to establish a new session, but it does not add step-up checks for later privileged actions. Existing sessions remain valid until they expire, and an admin can reset an operator's MFA enrollment if access is lost.

## User Stories

1. As an operator, I want to enable MFA for my account, so that my DNSCheck sign-in is harder to compromise.
2. As an operator, I want MFA to be optional, so that I can choose the level of protection that fits my deployment.
3. As an operator, I want to start MFA enrollment in a pending state, so that I can confirm the second factor before it becomes active.
4. As an operator, I want to verify the new second factor during enrollment, so that I know the factor actually works before I depend on it.
5. As an operator, I want recovery codes when I enroll MFA, so that I can regain access if my second factor is unavailable.
6. As an operator, I want to see whether MFA enrollment is active or pending, so that I understand my current sign-in state.
7. As an operator, I want to sign in with my username, password, and second factor, so that a stolen password alone is not enough to enter DNSCheck.
8. As an operator, I want login to fail cleanly when the second factor is required but missing or wrong, so that I can tell whether I mistyped my password or my second factor.
9. As an operator, I want existing sessions to remain valid after I enable MFA, so that turning on MFA does not kick me out unexpectedly.
10. As an operator, I want newly created sessions to require MFA after enrollment is active, so that future logins use the stronger sign-in path.
11. As an operator, I want to use a recovery code when I cannot access my second factor, so that I can still get back into my account.
12. As an operator, I want to rotate or replace my recovery codes, so that old recovery material can be retired after use or exposure.
13. As an operator, I want to disable MFA from my account when policy allows it, so that I can return to password-only sign-in if needed.
14. As an administrator, I want to reset an operator's MFA enrollment, so that I can recover an account when the operator has lost access to the second factor.
15. As an administrator, I want an MFA reset to clear the active enrollment and recovery material, so that the operator must enroll again before MFA protects sign-in.
16. As an administrator, I want MFA reset to leave the operator account intact, so that recovery does not require deleting and recreating the account.
17. As an operator, I want MFA to protect only login in v1, so that the feature can ship without changing the existing rights and session model for privileged actions.
18. As an operator, I want the sign-in flow to stay aligned with the existing Auth API and session cookie model, so that the UI and API do not need a second authentication system.
19. As an operator, I want the account security settings to be visible in the current operator account area, so that I can manage MFA where I already manage personal security controls.
20. As an operator, I want the service to reject protected API requests when I have no valid session, so that MFA does not weaken existing session enforcement.
21. As an operator, I want the login flow to remain understandable in the UI, so that I can tell when I need a password, a second factor, or a recovery code.
22. As an operator, I want MFA changes to survive service restarts, so that sign-in security is durable and not held only in memory.

## Implementation Decisions

- Keep MFA inside the existing session-based authentication model rather than introducing a separate identity provider.
- Use the current Auth API and session-cookie boundary as the single end-to-end seam for the feature.
- Add a staged enrollment state so MFA is not active until the second factor has been verified.
- Keep MFA login-only in v1; do not add step-up authentication for privileged actions in this iteration.
- Preserve existing sessions when MFA is enabled, so the feature only affects new logins.
- Add admin reset behavior that clears an operator's MFA enrollment and recovery material without deleting the operator account.
- Store MFA state durably alongside the operator account data so the enrollment survives restarts.
- Surface MFA status and controls through the current operator account/security area rather than a separate administration surface.
- Extend the OpenAPI contract first, then regenerate the server and client surfaces from it, so the API remains the source of truth.

## Testing Decisions

- Good tests exercise only external behavior: login, logout, enrollment, recovery, reset, and protected-request outcomes.
- Test the Auth API/session boundary end to end, because that is the seam already used by the current authentication flow.
- Add coverage for session behavior before and after MFA enrollment so the feature does not silently invalidate existing sessions.
- Add coverage for admin reset and operator recovery flows so account lockout can be resolved safely.
- Prior art exists in the current auth tests and OpenAPI contract tests, which already verify login, protected access, logout, and generated-contract alignment.

## Out of Scope

- Step-up authentication for sensitive actions after login.
- Mandatory MFA for all operators.
- External single sign-on or third-party identity provider integration.
- Changing the current rights model, zone model, or reconciliation behavior.
- Replacing server-side sessions with stateless tokens.

## Further Notes

The agreed domain language treats MFA as the canonical term, with 2FA as shorthand. The feature was deliberately scoped to login-only MFA because that fits the current session model and avoids coupling the first release to privileged-action re-authentication.