# Elektron Net: `elektron-net-mobile` Release m0.1.6

- **Version:** m0.1.6
- **Date:** October 24, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.6` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.5.md`](./CHANGELOG-Release-m0.1.5.md), [`guideline-escrow-psbt-and-tools.md`](./guideline-escrow-psbt-and-tools.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.1.6`.

## Send

- A payment can be created as a PSBT instead of being sent. At the review step of Send, a switch "Only create a PSBT, do not send" turns the payment into a PSBT, and a second switch decides whether the PSBT is signed with the key of the wallet or handed over unsigned. The result is shown as text with Copy, Share and a QR code. The PSBT can be checked, signed, combined and sent under Tools, PSBT, on this phone or on another device.
