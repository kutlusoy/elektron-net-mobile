# Elektron Net: `elektron-net-mobile` Release m0.1.8

- **Version:** m0.1.8
- **Date:** October 26, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.8` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.7.md`](./CHANGELOG-Release-m0.1.7.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.1.8`. It continues from `m0.1.7`, the wallet version is 0.1.8.

## Text in a block

- On the last screen before "Send" a switch "Embed text in block" opens a text field. The text is written into the payment as one `OP_RETURN` output (value 0) and ends up in the block together with the payment. The length limit is 1000 bytes, a counter shows the use. The text is public and can be read by anyone, which the screen says. It costs a small extra fee that the review already includes.
- The text is applied with a button, which prepares the payment again. "Send" stays disabled while the field differs from the applied text. The text is shown again on the confirmation screen. The switch is not offered for escrow payments.
- Under the text field the screen shows an estimate of the extra fee (size of the data output times the chosen rate), which updates while typing. The real fee follows after "Apply text". The estimate is checked against the real fee in a test.
- A sanitizer cleans the text before it is signed (`EmbeddedMessage`): control characters, line and paragraph separators, format characters (direction overrides, zero-width characters, byte order marks), private-use and unassigned code points and lone surrogates are removed. Text is normalised to NFC, all kinds of space become one plain space, line breaks become `\n` with at most one blank line, and more than three stacked combining marks are dropped. The length is counted in UTF-8 bytes and is cut on a character boundary. The cleaning is idempotent. The engine refuses any text that is not already clean.
- The payment is checked again after the node has built and after it has signed it: exactly one data output with exactly the expected script and value 0 must be present, or none if no text was chosen. A changed or removed text is a mismatch and nothing is sent.
- Because the chain only sees bytes, markup or code in the text is harmless on chain. The sanitizer protects whoever displays the text later, and the same function is meant for showing received texts.

### Checked on a two-node regtest

Before the feature was built, `OpReturnConsensusIntegrationTest` ran two real nodes (`elektrond` 4.0.6), with the attestation active:

- messages of 80 up to 99,000 bytes in one block are accepted by the second node, the attestation stays valid, no invalid chain tip appears;
- six chained transactions with two data outputs each in one block (the intra-block case) are valid;
- invalidating and reconsidering a block with messages keeps both nodes on the same chain, and the chain grows on;
- with the standard policy (`-acceptnonstdtxn=0`) 99,000 bytes are relayed and 100,100 bytes are refused, which is the node limit of about 100,000 bytes per transaction;
- three outputs of 30,000 bytes in one transaction are accepted.

`OP_RETURN` outputs never enter the UTXO set and the attestation is read only from the coinbase, so a text cannot disturb it.
