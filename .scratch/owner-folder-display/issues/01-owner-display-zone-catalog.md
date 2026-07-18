# 01 — Owner display on zone catalog

**What to build:** The zone catalog Owner column shows the bare owner identifier with a tinted Person or Group icon (by owner kind) instead of the `.u` / `.g` suffix. Hovering reveals the exact owner folder. Malformed owner folders stay as raw text with no icon. A shared display helper and label power this, with helper coverage at the agreed seam. Create/transfer form fields still use full owner folder strings.

**Blocked by:** None — can start immediately

**Status:** done

- [x] Zone catalog Owner column hides `.u` / `.g` and shows bare identifier
- [x] User vs group owner kind is shown with a leading tinted Material icon
- [x] Tooltip / title exposes the exact owner folder string
- [x] Malformed owner folder strings render unchanged with no invented kind
- [x] Forms and API values still use full owner folder strings
- [x] Display helper is covered for user, group, and malformed owner folder inputs
