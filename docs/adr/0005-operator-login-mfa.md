# Operator login MFA

DNSCheck will support optional multi-factor authentication for operator sign-in. The first version is login-only, uses a staged enrollment flow, keeps existing sessions valid until they expire, and allows admins to reset an operator's MFA enrollment if access is lost. We are not adding step-up authentication for privileged actions in this iteration, which keeps the auth model aligned with the current session-based login flow while covering the main security gap.
