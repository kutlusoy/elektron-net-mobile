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

- The Send screen stays simple: a switch "Advanced payment" under the address field (off by default) shows the extra options only when it is on: choosing coins, subtracting the fee from the amount, a custom fee rate and creating a PSBT instead of sending. Switching it off clears these choices again.
- A payment can be created as a PSBT instead of being sent. With "Advanced payment" on, at the review step of Send, a switch "Only create a PSBT, do not send" turns the payment into a PSBT, and a second switch decides whether the PSBT is signed with the key of the wallet or handed over unsigned. The result is shown as text with Copy, Share and a QR code. The PSBT can be checked, signed, combined and sent under Tools, PSBT, on this phone or on another device.

## Escrow

- The deposit address of an escrow is kept until a signed payment request or a signature is saved in the message history of that escrow. Only then the next deposit address is created. Sharing the first message or receiving funds no longer changes it.
- A new escrow never reuses the key account of an earlier one, also not after the earlier one was deleted. A new deal with the same partners therefore has its own keys, address and check code, and no longer shows amounts of a finished and deleted deal. Funds that still sit on the old deal belong to its old keys and can be reached by restoring that escrow from its backup.

## Node screen

- The buttons under the live log and the Tor and I2P logs wrap onto a second line when they do not fit, instead of squeezing the last button into a vertical text.

## Number format

- The default separators follow the device language and region on a fresh install. Choosing the app language that equals the device language keeps the device region (for example German in Switzerland keeps the apostrophe).
- Languages with their own digits (Arabic) no longer mix their separators with Latin digits: they use comma and period.
