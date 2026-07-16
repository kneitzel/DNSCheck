# 24 — MFA login challenge

**What to build:** Active MFA is required to establish a new session, login fails cleanly when the second factor is missing or wrong, and existing sessions keep working until they naturally expire.

**Blocked by:** 23 — MFA enrollment and status

**Status:** ready-for-agent

- [ ] Login requires the second verification factor after MFA enrollment is active
- [ ] Existing sessions remain valid after MFA is enabled
