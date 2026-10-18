# Elektron Net: `elektron-net-mobile` UI Design System

- **Version:** 0.1 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, UI designers, translators, security reviewers
- **Reference implementation:** [`elektron-net-wp`](https://github.com/kutlusoy/elektron-net-wp) `osclass/osclasselektron/css/style.css` (palette source, commit `37aae603`), `mobile/design/palettes.json` and `mobile/design/check-contrast.py` in this repository (machine readable palettes and the legibility gate)
- **Related repositories:** [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (branding), [`elektron-net-wp`](https://github.com/kutlusoy/elektron-net-wp) (color themes)
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (sections §3.3, §3.5), [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md) (testnet flavor)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Decisions

The app looks cyberpunk, but calm: dark surfaces, neon accents, controlled glow. Reading balances, addresses and security warnings always comes first.

| Topic | Decision |
|---|---|
| Artwork | None. The app ships no illustrations, mascots or background images. The look comes from color, type, shape and light only. This also removes any third-party artwork question. |
| Color themes | The ten themes developed for the Osclass theme, taken from `elektron-net-wp` (§2). |
| Style | Cyberpunk without overload: neon accent, glow on a few key elements, flat dark surfaces. |
| Legibility | Hard rule. Effects never reduce contrast of text, and a CI gate checks every theme (§4). |
| Ticker | ELEK only. No other ticker is shown anywhere in the UI, in store listings or in strings. |

## 2. Color Themes

The themes are stored once, in `mobile/design/palettes.json`, with the tokens `accent`, `hover`, `accent-2`, `on-accent`, `bg`, `surface`, `surface-2`, `border`, `text`, `muted`, `link`, in a dark and a light variant. The app generates its Compose color schemes from this file at build time, so the website and the app stay in sync.

| Key | Name | Accent | Second accent | Character |
|---|---|---|---|---|
| `blue` | Elektron Blue | `#2da4fa` | `#22d3ee` | default, brand color |
| `neon` | Neon Grid | `#00f0ff` | `#ff2a6d` | cyan and magenta |
| `synthwave` | Synthwave | `#ff3cac` | `#ffb347` | pink and amber |
| `matrix` | Matrix | `#00ff88` | `#b6ff00` | green on near black |
| `rust` | Rust | `#ff7a1a` | `#e0b04a` | warm industrial |
| `iron` | Iron | `#9db7cf` | `#ffb020` | restrained steel, least glow |
| `violet` | Violet Night | `#bd93f9` | `#ff79c6` | soft purple |
| `red` | Hacker Red | `#ff2a3d` | `#ff5c8a` | red on black |
| `frost` | Frost | `#9be7ff` | `#d5f3ff` | icy, light accents |
| `terminal` | Terminal | `#5af78e` | `#ffb86c` | green terminal |

- **MUST:** the default theme is Elektron Blue in dark mode. Light mode exists for every theme and follows the system setting unless the user chooses a mode.
- **SHOULD:** the theme picker shows a live preview card with balance, a button and a warning, so the user sees legibility before choosing.
- **MUST:** the red status color for errors is fixed per mode and independent from the theme. In the `red` theme the accent is red, so destructive and error states additionally use an icon and a text label, never color alone.
- Status colors (success, danger, warning) are shared by all themes (see `palettes.json`, key `status`).
- The Material 3 dynamic color (wallpaper based) is not used, to keep the brand and the legibility gate deterministic.

## 3. Effects

Three levels, selectable in settings: **Off**, **Subtle** (default), **Full**. The level is applied by one `Effects` object so no screen decides on its own.

| Effect | Where | Off | Subtle | Full |
|---|---|---|---|---|
| Accent glow (soft shadow behind the accent color) | primary button, active tab indicator, sync progress, node status dot | none | small radius, low alpha | larger radius, medium alpha |
| Neon edge (1 dp accent border) | hero card on each screen | plain border color | accent at 50 percent | accent at 100 percent |
| Text glow | only the hero balance figure and the screen title | none | none | faint, never on body text |
| Scanline or grid texture | none | none | none | very low contrast grid on the app background only, never behind text blocks |
| Motion | status dot pulse, progress shimmer | static | slow pulse | pulse and shimmer |

Rules:

- **MUST:** no glow, blur or texture is drawn behind or over body text, addresses, amounts below the hero figure, form fields or warnings. Text sits on flat `surface` colors.
- **MUST:** effects honor the system setting "Remove animations" and Android battery saver: motion is off, glow falls back to Off when battery saver is on.
- **MUST:** glow is drawn with a cached `Modifier.drawBehind` shadow on a few elements. No full-screen blur, no per-frame shader on lists. Scrolling lists (history, peers, log) draw no glow.
- **SHOULD:** the budget is at most two glow layers per screen. The Node log and the Expert console use Off regardless of the setting, since they are dense reading surfaces.
- The warning screens for the recovery phrase, the Base58 collision and Danger-tier commands use the status colors without glow.

## 4. Legibility Rules and CI Gate

Contrast is measured with the WCAG relative luminance formula. `mobile/design/check-contrast.py` checks every theme in both modes and fails the build if a pair is below its minimum.

| Pair | Minimum ratio (dark) | Minimum ratio (light) |
|---|---|---|
| `text` on `bg`, `surface`, `surface-2` | 7.0 | 7.0 |
| `muted` on `bg`, `surface`, `surface-2` | 5.5 | 5.0 |
| `accent` on `bg` (accent as text or icon) | 4.5 | not used as text |
| `link` on `surface` | 4.5 | 4.5 |
| `on-accent` on `accent` (dark) or `link` (light), label on a filled button | 4.5 | 4.5 |
| status colors on `bg`, `surface`, `surface-2` | 4.5 | 4.5 |

Current result: 350 of 350 checks pass for the ten themes. Any new or changed theme MUST pass before it is merged.

Further rules:

- **MUST:** minimum text size 12 sp, body 16 sp, and the layout survives system font scaling up to 200 percent.
- **MUST:** state is never conveyed by color alone (icon or text accompanies it).
- **MUST:** touch targets are at least 48 dp.
- **MUST:** addresses are shown in a monospaced style with grouped characters and a contrast of at least 7.0, and the first and last 6 characters are visually emphasized (§8.5 of the plan).
- **SHOULD:** the screenshot tests run once per theme in dark and light with Effects at Full, to prove that the strongest setting is still readable.

## 5. Typography and Scripts

- Latin display (titles, hero balance): Orbitron or a similar geometric face, used sparingly. Technical text (addresses, hashes, log, console): Share Tech Mono or the system monospace. Both are open licensed fonts bundled with the app (licenses recorded in the repository).
- All other text and all non-Latin scripts use the system fonts (Noto on Android), so Chinese, Japanese, Korean, Arabic, Hindi and Cyrillic render correctly without bundled font weight.
- The display face is applied only where the active language is Latin script. For other scripts the title style falls back to the system font with the same weight and letter spacing zero.
- Amounts, heights and addresses always use Latin digits (0-9) and the node's address alphabet, in every language, to avoid ambiguity. Surrounding text follows the locale.
- Right-to-left (Arabic): mirror layout through standard `start/end` attributes. Addresses, amounts, hashes and console text are wrapped in left-to-right isolates (Unicode FSI/PDI) so they never reorder.
- The glow of an element never changes its layout size, so text expansion in German or French does not shift effects.

## 6. Flavors and Visual Distinction

Mainnet and testnet are separate builds with separate application IDs (see [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md)).

- **MUST:** the testnet build shows a permanent, non-dismissable "TESTNET, coins have no value" bar in a fixed warning color on every screen, and uses a different launcher icon color and app name ("Elektron Testnet").
- **MUST:** the testnet build uses its own default theme (Hacker Red or Synthwave is suggested) so it cannot be mistaken for the mainnet app in screenshots.
- The launcher icon is a simple geometric mark (a bolt in a rounded square) drawn with vector shapes and the theme color, no raster artwork.

## 7. Implementation Notes

- Tokens live in one Kotlin object generated from `palettes.json`; no hex value appears elsewhere in app code (lint rule).
- `Effects` level, theme key and mode are stored in app preferences (non-sensitive).
- Compose previews exist for each theme and mode, used by the screenshot tests.

## 8. Tests

- `check-contrast.py` runs in CI on every change to `palettes.json` and on every build.
- Screenshot tests per theme, mode and effect level for Home, Send, Receive, Node status and the recovery phrase screen.
- Pseudo-localization and the RTL build (Arabic) are run with the strongest effect level.
- Manual check on a low-end device that Full effects keep scrolling smooth; otherwise lower the Full defaults.

## 9. Checklist

- [ ] Palettes generated from `palettes.json`, no stray hex values
- [ ] Contrast gate green for all themes
- [ ] Effects Off/Subtle/Full implemented in one place
- [ ] No effect behind text; battery saver and reduce-motion honored
- [ ] Non-Latin scripts use system fonts; RTL verified
- [ ] Testnet bar and icon distinct
- [ ] No artwork, no third-party images in the repository
