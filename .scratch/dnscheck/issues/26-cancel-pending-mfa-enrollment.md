# 26 — Cancel pending MFA enrollment

**What to build:** An operator with MFA enrollment still pending can abandon that enrollment: after a short confirmation, the pending second factor and any recovery codes from that enrollment are cleared, MFA status returns to disabled, and the operator can start enrollment again. Cancel must not disable active MFA (that remains out of scope / admin MFA reset).

**Blocked by:** None — can start immediately

**Status:** done

- [x] Operators can cancel MFA enrollment only while status is pending
- [x] Cancel clears the pending second factor and recovery material from that enrollment and returns MFA status to disabled
- [x] Cancel while MFA is disabled or active is rejected without changing state
- [x] Account security UI offers cancel with a confirmation step before applying
- [x] After cancel, the operator can start MFA enrollment again
- [x] Behavior is covered at the Account Security / Auth API seam (including contract conformance for the cancel operation)
