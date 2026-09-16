# Palette audit

This audit records the required pre-refactor literal inventory. Runtime CSS is generated from the tokenized templates; the templates retain semantic placeholders rather than independent color values.

| File | Line | Literal | Matches token | Current source |
|---|---:|---|---|---|
| src/main/java/com/parking/gui/LightDesignTokens.java | 7 | #F3F6FA | LIGHT_BG | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 8 | #FFFFFF | WHITE, LIGHT_CARD | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 9 | #D7DEE8 | LIGHT_BORDER | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 10 | #F7F9FC | LIGHT_INPUT | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 11 | #EEF8F5 | LIGHT_HOVER | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 12 | #172033 | USER_LIGHT_TEXT, LIGHT_TEXT | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 13 | #526176 | USER_LIGHT_MUTED, LIGHT_MUTED | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 14 | #F1F5F9 | LIGHT_TABLE_HEADER | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 15 | #F7FAFC | LIGHT_ANALYTICS_HOVER | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 16 | #00B894 | TEAL_DARK, LIGHT_ACCENT | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 17 | #008F74 | LIGHT_ACCENT_TEXT | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 18 | #3F4B5E | LIGHT_TEXT_STRONG | canonical constant |
| src/main/java/com/parking/gui/LightDesignTokens.java | 19 | rgba(20,27,45,0.45) | SHADOW_DARK_SURFACE_45, LIGHT_SHADOW | canonical constant |
| src/main/java/com/parking/gui/UserManagementView.java | 963 | rgba(" + rgb(color) | UNMATCHED — new/one-off color | Java expression |
| src/main/java/com/parking/gui/UserManagementView.java | 1105 | rgb(String hex) | UNMATCHED — new/one-off color | Java expression |
| src/main/java/com/parking/gui/ParkingApplication.java | 429 | #FFFFFF | WHITE, LIGHT_CARD | Java expression |
| src/main/java/com/parking/gui/ParkingApplication.java | 3219 | #1A3A2A | SPOT_AVAILABLE_BG | Java expression |
| src/main/java/com/parking/gui/AnalyticsReportingView.java | 810 | #FFFFFF | WHITE, LIGHT_CARD | Java expression |
| src/main/java/com/parking/gui/DesignTokens.java | 11 | #0A0E27 | BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 12 | #141B2D | CARD | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 13 | #1A2332 | HOVER | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 14 | #00D4AA | TEAL | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 15 | #00B894 | TEAL_DARK, LIGHT_ACCENT | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 16 | #FF6B35 | ORANGE | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 17 | #FFB800 | YELLOW | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 18 | #FF4757 | RED | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 19 | #2ED573 | GREEN | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 20 | #3498DB | BLUE | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 21 | #9B59B6 | PURPLE | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 22 | #E8ECF1 | TEXT | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 23 | #FFFFFF | WHITE, LIGHT_CARD | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 24 | #000000 | BLACK | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 25 | #7B8BA3 | MUTED | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 26 | #2A3548 | BORDER | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 29 | #0F152A | SURFACE_HEADER | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 30 | #202B3D | SURFACE_COMMAND | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 31 | #202A40 | SKELETON_BLOCK | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 32 | #AAB6C8 | HEADER_MUTED | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 33 | #2DE2BC | TEAL_BRIGHT | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 34 | #E5503B | ORANGE_DARK | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 35 | #1A3A2A | SPOT_AVAILABLE_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 36 | #2A1A1A | SPOT_OCCUPIED_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 37 | #3A2A0A | SPOT_MAINTENANCE_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 38 | #1A1A3A | SPOT_RESERVED_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 39 | #0A2A2A | SPOT_EV_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 40 | #252A35 | SPOT_OUT_OF_SERVICE_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 41 | #18382B | SPOT_AVAILABLE_DARK_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 42 | #143B3A | SPOT_EV_DARK_BG | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 43 | rgba(255,255,255,0.07) | SKELETON_SHEEN | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 44 | #CBD5E1 | PRINT_BORDER | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 47 | rgba(0,0,0,0.28) | SHADOW_BLACK_28 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 48 | rgba(0,0,0,0.30) | SHADOW_BLACK_30 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 49 | rgba(0,0,0,0.35) | SHADOW_BLACK_35 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 50 | rgba(0,0,0,0.40) | SHADOW_BLACK_40 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 51 | rgba(0,0,0,0.42) | SHADOW_BLACK_42 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 52 | rgba(0,0,0,0.45) | SHADOW_BLACK_45 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 53 | rgba(0,0,0,0.48) | SHADOW_BLACK_48 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 54 | rgba(0,0,0,0.50) | SHADOW_BLACK_50 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 55 | rgba(0,0,0,0.5) | SHADOW_BLACK_5 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 56 | rgba(0,0,0,0.4) | SHADOW_BLACK_4 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 57 | rgba(0,0,0,0.55) | SHADOW_BLACK_55 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 58 | rgba(0,0,0,0.68) | SHADOW_BLACK_68 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 59 | rgba(20,27,45,0.45) | SHADOW_DARK_SURFACE_45, LIGHT_SHADOW | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 60 | rgba(10,14,39,0.96) | OVERLAY_DARK | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 61 | rgba(20,27,45,0.96) | OVERLAY_SURFACE | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 62 | rgba(0,212,170,0.03) | GLOW_TEAL_03 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 63 | rgba(0,212,170,0.20) | GLOW_TEAL_20 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 64 | rgba(0,212,170,0.26) | GLOW_TEAL_26 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 65 | rgba(0,212,170,0.35) | GLOW_TEAL_35 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 66 | rgba(255,107,53,0.35) | GLOW_ORANGE_35 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 67 | rgba(255,184,0,.16) | GLOW_YELLOW_16 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 68 | rgba(255,71,87,.16) | GLOW_RED_16 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 69 | rgba(46,213,115,.16) | GLOW_GREEN_16 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 70 | rgba(52,152,219,.16) | GLOW_BLUE_16 | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 71 | rgba(0,212,170,0.10) | GLOW_TEAL_10 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 72 | rgba(0,212,170,0.12) | GLOW_TEAL_12 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 73 | rgba(0,212,170,0.13) | GLOW_TEAL_13 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 74 | rgba(52,152,219,0.10) | GLOW_BLUE_10 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 75 | rgba(52,152,219,0.13) | GLOW_BLUE_13 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 76 | rgba(255,107,53,0.10) | GLOW_ORANGE_10 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 77 | rgba(255,107,53,0.13) | GLOW_ORANGE_13 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 78 | rgba(255,71,87,0.13) | GLOW_RED_13 | canonical constant (Phase 1) |
| src/main/java/com/parking/gui/DesignTokens.java | 79 | rgba(5,8,24,0.72) | MODAL_BACKDROP | canonical constant |
| src/main/java/com/parking/gui/DesignTokens.java | 80 | rgba(5,8,27,0.78) | USER_MODAL_BACKDROP | canonical constant |

Total audited occurrences: **84** (76 pre-refactor + 8 new Phase 1 glow tokens).

## Explicit findings

- Existing dark values remain in `DesignTokens.java`; CSS duplicates are now generated from tokenized templates.
- Light-only values are named in `LightDesignTokens.java`: `LIGHT_BG`, `LIGHT_CARD`, `LIGHT_BORDER`, `LIGHT_INPUT`, `LIGHT_HOVER`, `LIGHT_TEXT`, `LIGHT_MUTED`, `LIGHT_TABLE_HEADER`, `LIGHT_ANALYTICS_HOVER`, `LIGHT_ACCENT`, `LIGHT_ACCENT_TEXT`, `LIGHT_TEXT_STRONG`, and `LIGHT_SHADOW`.
- CSS-only dark surface/status/shadow values are named in `DesignTokens.java`; print border is `PRINT_BORDER`.
- Phase 1 (token consolidation) resolved the two UNMATCHED rows: `UserManagementView.rgb()` helper deleted, `roleBadge()` now uses `role-badge-*` CSS classes backed by `GLOW_*_13` tokens; `TicketPaymentView.alpha()` helper deleted, all callers use `GLOW_TEAL_10` or CSS classes.
- Dark glow tints (`GLOW_*_10/12/13`) work on light backgrounds as-is for badges/pills; no `LIGHT_*` variants needed. Documented here per Step 1.6.

### Pre-refactor CSS duplicate groups

The initial scan found hardcoded duplicates in the toast success/error/warning/info
rules, toast icons, notification dots, scrollbar thumb and hover rules, focus
rings, and the spot/status rules. They now resolve through generated output from
the named constants above. The light-only values were the `.theme-light`
background/card/input/hover/border/text/table-header/analytics rules; those are
covered by the `LIGHT_*` constants. The unmatched CSS-only values were named as
`SURFACE_*`, `SKELETON_*`, `HEADER_MUTED`, `TEAL_BRIGHT`, `ORANGE_DARK`,
`SPOT_*`, `PRINT_BORDER`, the shadow/overlay tokens, and the glow/backdrop
tokens in `DesignTokens.java`.
