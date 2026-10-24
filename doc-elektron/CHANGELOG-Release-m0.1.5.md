# Elektron Net: `elektron-net-mobile` Release m0.1.5

- **Version:** m0.1.5
- **Date:** October 24, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.5` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.4.md`](./CHANGELOG-Release-m0.1.4.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.1.5`.

## Settings

- Number format under "Unit for amounts": a switch for the thousands separator (on by default) and a choice of the character for thousands and for decimals. The default follows the language and the country of the app, for example `1,234,567.89` in English and `1.234.567,89` in German. The user can override both characters (comma, period, space, apostrophe for thousands, comma or period for decimals) and sees an example. The two characters never collide.
- The format applies to every amount shown in the app: balance, history, details, Send, Receive, escrows, fiat values and the sums in the storage overview. Text fields, payment links and CSV exports keep the plain form (`1234.5`) so that they can be read back.

## Escrow

- "Deposit from my wallet" on the escrow page opens Send with the deposit address filled in, so coins go from the active wallet into the escrow in one step.
- "Pay out of the escrow" (before "Create a payment") is explained on the page and in the Send screen: it spends the coins that are already in the escrow and creates a payment request that needs the approvals of the others.
