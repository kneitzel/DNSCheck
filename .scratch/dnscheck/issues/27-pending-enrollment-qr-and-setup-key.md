# 27 — Pending enrollment QR and setup key

**What to build:** While MFA enrollment is pending, the account security panel shows a scannable QR code (rendered in the browser from the existing otpauth URI) and the manual setup key, without displaying the raw otpauth URI string. Confirming enrollment is unchanged.

**Blocked by:** None — can start immediately

**Status:** done

- [x] Pending MFA enrollment UI shows a client-rendered QR from the otpauth URI
- [x] Pending UI shows the manual setup key for typing into an authenticator app
- [x] Pending UI does not show the raw otpauth URI string
- [x] Confirm enrollment and MFA status behavior remain unchanged
