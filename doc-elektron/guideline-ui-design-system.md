# Elektron Net: `elektron-net-mobile` UI Design System

- **Version:** 0.6 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, UI designers, translators, security reviewers
- **Reference implementation:** [`elektron-net-wp`](https://github.com/kutlusoy/elektron-net-wp) `osclass/osclasselektron/css/style.css` (palette source, commit `37aae603`), `design/palettes.json` and `design/check-contrast.py` in this repository (machine readable palettes and the legibility gate)
- **Related repositories:** [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (branding), [`elektron-net-wp`](https://github.com/kutlusoy/elektron-net-wp) (color themes)
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (sections §3.3, §3.5), [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md) (testnet flavor)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Decisions

The app looks cyberpunk, but calm: dark surfaces, neon accents, controlled glow. Reading balances, addresses and security warnings always comes first.

| Topic | Decision |
|---|---|
| Artwork | None. The app ships no illustrations, mascots or background images. The look comes from color, type, shape and light only. The one exception is the official Elektron Net logo (below), which is the project's own mark. This also removes any third-party artwork question. |
| Logo | The official Elektron Net logo, `src/qt/res/src/bitcoin-elektron.svg` in `elektron-net`, copied unchanged to `design/logo/bitcoin-elektron.svg` and converted to Android vector drawables by `design/gen-logo.py`. |
| Color themes | The ten themes developed for the Osclass theme, taken from `elektron-net-wp`, plus Elektron Teal (§2). |
| Style | Cyberpunk without overload: neon accent, glow on a few key elements, flat dark surfaces. |
| Legibility | Hard rule. Effects never reduce contrast of text, and a CI gate checks every theme (§4). |
| Ticker | ELEK only. No other ticker is shown anywhere in the UI, in store listings or in strings. |

## 2. Color Themes

The themes are stored once, in `design/palettes.json`, with the tokens `accent`, `hover`, `accent-2`, `on-accent`, `bg`, `surface`, `surface-2`, `border`, `text`, `muted`, `link`, in a dark and a light variant. The app generates its Compose color schemes from this file at build time, so the website and the app stay in sync.

| Key | Name | Accent | Second accent | Character |
|---|---|---|---|---|
| `teal` | Elektron Teal | `#20d9f1` | `#b9f3ff` | default, deep teal surfaces |
| `blue` | Elektron Blue | `#2da4fa` | `#22d3ee` | brand blue |
| `neon` | Neon Grid | `#00f0ff` | `#ff2a6d` | cyan and magenta |
| `synthwave` | Synthwave | `#ff3cac` | `#ffb347` | pink and amber |
| `matrix` | Matrix | `#00ff88` | `#b6ff00` | green on near black |
| `rust` | Rust | `#ff7a1a` | `#e0b04a` | warm industrial |
| `iron` | Iron | `#9db7cf` | `#ffb020` | restrained steel, least glow |
| `violet` | Violet Night | `#bd93f9` | `#ff79c6` | soft purple |
| `red` | Hacker Red | `#ff2a3d` | `#ff5c8a` | red on black |
| `frost` | Frost | `#9be7ff` | `#d5f3ff` | icy, light accents |
| `terminal` | Terminal | `#5af78e` | `#ffb86c` | green terminal |

- **MUST:** the default theme is Elektron Teal in dark mode. Light mode exists for every theme and is used when the user chooses it.
- **MUST:** the theme picker (Settings, Appearance) lists all eleven themes with a four color swatch, a live preview card with balance, a button, a warning and a link, a mode choice (system, light, dark) and an effect choice (Off, Subtle, Full). Changes apply at once and are stored in app preferences. The first start uses Elektron Teal, dark, Subtle.
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

### 3.1 Brand surfaces (v0.4)

The default design is active from the first screen on, before any wallet exists. The first-run screens and the main app use the same building blocks, so the app looks like itself from the first second:

- **Brand background:** the theme background with a very soft vertical tint and two radial glows of the theme's accent colors (top center, bottom right). The glow strength follows the effect level (Off: none, Subtle: 22 percent, Full: 34 percent). The window background before the first frame is the theme background, so there is no white flash.
- **Glass cards:** panels use the theme surface color at 72 percent opacity, a 1 dp border with a gradient from the accent color to the border color, and rounded corners. Text always sits on the card surface, never on the glow, and the translucency is limited so that the contrast rules in §4 still hold.
- **Logo and word mark:** the official logo as a vector (`logo_mark.xml`, generated by `design/gen-logo.py`, dark grey in the testnet flavor) with a soft glow, and the app name in capital letters in Orbitron with wide letter spacing. The welcome screen shows the large version, the following steps a compact header.
- **Hero card:** the balance and node cards use the same glass look.

## 4. Legibility Rules and CI Gate

Contrast is measured with the WCAG relative luminance formula. `design/check-contrast.py` checks every theme in both modes and fails the build if a pair is below its minimum.

| Pair | Minimum ratio (dark) | Minimum ratio (light) |
|---|---|---|
| `text` on `bg`, `surface`, `surface-2` | 7.0 | 7.0 |
| `muted` on `bg`, `surface`, `surface-2` | 5.5 | 5.0 |
| `accent` on `bg` (accent as text or icon) | 4.5 | not used as text |
| `link` on `surface` | 4.5 | 4.5 |
| `on-accent` on `accent` (dark) or `link` (light), label on a filled button | 4.5 | 4.5 |
| status colors on `bg`, `surface`, `surface-2` | 4.5 | 4.5 |

Current result: 385 of 385 checks pass for the eleven themes. Any new or changed theme MUST pass before it is merged.

Further rules:

- **MUST:** minimum text size 12 sp, body 16 sp, and the layout survives system font scaling up to 200 percent.
- **MUST:** state is never conveyed by color alone (icon or text accompanies it).
- **MUST:** touch targets are at least 48 dp.
- **MUST:** addresses are shown in a monospaced style with grouped characters and a contrast of at least 7.0, and the first and last 6 characters are visually emphasized (§8.5 of the plan).
- **SHOULD:** the screenshot tests run once per theme in dark and light with Effects at Full, to prove that the strongest setting is still readable.

## 5. Typography and Scripts

- **Implemented in v0.4:** Orbitron (Medium and Bold) for titles and the hero balance and Share Tech Mono for addresses, hashes and the log, bundled as TrueType in `res/font` (Latin subsets of the `@fontsource` packages 5.3.0, SIL Open Font License 1.1, license texts in `design/fonts`). Characters outside the Latin subset fall back to the system font.
- Latin display (titles, hero balance): Orbitron or a similar geometric face, used sparingly. Technical text (addresses, hashes, log, console): Share Tech Mono or the system monospace. Both are open licensed fonts bundled with the app (licenses recorded in the repository).
- All other text and all non-Latin scripts use the system fonts (Noto on Android), so Chinese, Japanese, Korean, Arabic, Hindi and Cyrillic render correctly without bundled font weight.
- The display face is applied only where the active language is Latin script. For other scripts the title style falls back to the system font with the same weight and letter spacing zero.
- Amounts, heights and addresses always use Latin digits (0-9) and the node's address alphabet, in every language, to avoid ambiguity. Surrounding text follows the locale.
- Right-to-left (Arabic): mirror layout through standard `start/end` attributes. Addresses, amounts, hashes and console text are wrapped in left-to-right isolates (Unicode FSI/PDI) so they never reorder.
- The glow of an element never changes its layout size, so text expansion in German or French does not shift effects.

## 6. Flavors and Visual Distinction

Mainnet and testnet are separate builds with separate application IDs (see [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md)).

- **MUST:** the testnet build shows a permanent, non-dismissable "TESTNET, coins have no value" bar in a fixed warning color on every screen, and uses a different launcher icon color and app name ("Elektron (T)", the mainnet app is called "Elektron").
- **MUST:** the testnet build uses its own default theme (Hacker Red or Synthwave is suggested) so it cannot be mistaken for the mainnet app in screenshots.
- The launcher icon is the official logo as an adaptive icon, drawn as vector drawables (no raster files), scaled into the safe zone. The mainnet icon keeps the logo colors (blue gradient, white mark) on the dark theme background. The testnet icon uses the same shape in dark grey on a light grey background, so the two apps are told apart at a glance on the home screen and in the app switcher. A monochrome layer for themed icons and the notification icon are derived from the same coin shape.
- `design/gen-logo.py` regenerates all three drawables from the SVG. Changing the logo means replacing the SVG and running the script, no hand editing.

## 7. Implementation Notes

- Tokens live in one Kotlin object generated from `palettes.json`; no hex value appears elsewhere in app code (lint rule).
- Theme key, mode and effect level are stored in app preferences (non-sensitive) and loaded before the first frame, so the first screens already show the chosen look. When battery saver is on, the effective effect level is Off, the stored choice is kept.
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
