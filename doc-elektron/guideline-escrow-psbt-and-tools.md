# Elektron Net: `elektron-net-mobile` Escrow, PSBT, Key Sweep and Message Tools

- **Version:** 0.2 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, security reviewers, release engineers, support staff who help users with shared wallets
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy)), Milio aka 1000mio ([@spiroflex](https://x.com/spiroflex)) 🇪🇸
- **Reference implementation:** `app/src/main/java/org/elektronnet/mobile/wallet/EscrowManager.kt`, `PsbtTools.kt`, `SweepEngine.kt`, `MessageTools.kt`, `SendEngine.kt` and `app/src/test/java/org/elektronnet/mobile/wallet/ToolsIntegrationTest.kt` in this repository. Node behavior: [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/wallet/rpc/` (`gethdkeys`, `importdescriptors`, `walletprocesspsbt`, `signmessage`), `src/rpc/rawtransaction.cpp` (`decodepsbt`, `analyzepsbt`, `combinepsbt`, `finalizepsbt`, `signrawtransactionwithkey`), `src/rpc/blockchain.cpp` (`scantxoutset`) and `src/common/signmessage.cpp`
- **Related repositories:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) (node), [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (message format compatibility)
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (§8, §9), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md), [`guideline-entropy-and-randomness.md`](./guideline-entropy-and-randomness.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Scope and Decisions

The wallet offers four advanced tools, reachable from the Wallet tab under "Tools". They use only features that the Elektron Net node already has. No consensus change and no new node feature is needed.

| Tool | What it is | Node features used |
|---|---|---|
| Escrow | A shared wallet with M of N approvals (default 2 of 3: buyer, seller, arbiter). The escrow is a plain multisig wallet. The node has no escrow-specific code. | `importdescriptors` with `wsh(sortedmulti())`, descriptor wallets, `walletcreatefundedpsbt` |
| PSBT tools | Check, sign, combine, finalize and broadcast payment files (BIP-174) | `decodepsbt`, `analyzepsbt`, `walletprocesspsbt`, `combinepsbt`, `finalizepsbt`, `sendrawtransaction` |
| Key sweep | Move all coins of one or more private keys into the wallet, like the sweep function of Electrum | `scantxoutset`, `createrawtransaction`, `signrawtransactionwithkey` |
| Messages | Sign a message with an address of the wallet and verify signatures of others | `signmessage`, `verifymessage` |

Decisions of October 18, 2026:

- The escrow is built from standard descriptors, so any other wallet that supports PSBT and descriptors can take part in an escrow, and the funds can be recovered without this app.
- Keys are never stored in the app. All private keys stay in node wallets that are encrypted with the user's passphrase.
- Importing single private keys into the wallet is not offered. Imported keys are not covered by the recovery phrase, which makes silent fund loss likely. Sweeping moves the coins into the seed-backed wallet instead.
- Time-locked refund paths (miniscript) and hardware signers are not part of v1.

## 2. Escrow (multisig)

### 2.1 Roles and keys

- Each participant uses a **dedicated escrow key** derived from their own recovery phrase at `m/48'/1370'/<a>'/2'` (BIP-48, script type P2WSH; coin type 1 on testnet). `<a>` is a new account number per escrow, so keys are never reused between deals.
- The participant shares only the **public key line** `[fingerprint/48h/1370h/<a>h/2h]xpub...`. It lets the others include them and cannot be used to spend.
- The threshold M and the number of participants N are chosen by the creator (2 <= M <= N <= 7). The default for a deal with an arbiter is 2 of 3.
- Roles are free text labels stored locally. The arbiter is a normal participant who holds a key.

### 2.2 Setup flow

```text
Each person:  create escrow  ->  app creates a node wallet "escrow-<id>" and an inactive wpkh key descriptor
              -> the node reports the public key line -> person shares it
Each person:  paste the key lines of the others, choose M
              -> importdescriptors: wsh(sortedmulti(M, own xprv path, other xpubs ...)) for /0/* (receive, active) and /1/* (change, active)
              -> app shows the check code and the first deposit address
All persons:  compare check code and deposit address (MUST) before any coins are sent
```

- **MUST:** `sortedmulti` is used, so the order of the keys does not matter and every participant derives the same addresses. The public descriptor text is written with the keys in a canonical order, so its checksum is the same for everybody. The app calls this checksum the **check code**.
- **MUST:** the UI tells all participants to compare the check code and the deposit address (out of band, for example by voice) before the first deposit. A wrong key line would otherwise create a wallet whose funds the others cannot spend.
- **MUST:** a pasted key is rejected if it is not a well-formed extended public key for the right network, if it appears twice, if it equals the participant's own key, or if the threshold is below 2 or above N.
- **MUST:** the escrow wallet is encrypted with the participant's passphrase and is locked except during the short signing window (S4).
- The master private key is read from the main wallet (`gethdkeys` with private keys) only inside a 10 second unlock and only to build the import request. It is not stored.

### 2.3 Paying out of an escrow

```text
Proposer:  Create a payment (same screens as Send, signs with the Sign button instead of Send)
           -> walletcreatefundedpsbt in the escrow wallet -> checks (S5) -> own signature added -> PSBT text
Others:    Payment files tool -> Check (outputs, fee, net effect, signatures 1 of 2) -> Sign
Anyone:    Combine the signed copies -> status "Ready to send" -> Send to the network
```

- **MUST:** a signer sees every recipient, the amount, the fee and the net effect on their escrow wallet before signing, with a clear marker for outputs that return to the escrow (change) and for outputs that go elsewhere.
- **MUST:** a payment cannot be broadcast with fewer than M signatures. The node enforces this, and the UI shows "Needs signatures: 1 of 2".
- **MUST:** signing the same file twice, or signing with a wallet that has no key for it, fails with "Nothing to sign" and does not unlock longer than 10 seconds.

### 2.4 Backup and recovery

- A backup text is created from the public descriptor and the key lines. It contains no private key and can be stored with the recovery phrase or sent by mail.
- The recovery phrase alone cannot restore an escrow, because the keys of the others are not derived from it. **MUST:** the UI says so.
- Restore: paste the backup text. The app finds its own key by the master fingerprint and the path `48h/<coin>h/<a>h/2h`, derives the same account, and rebuilds the wallet. The restored wallet starts with a recent rescan. A full rescan (`rescanblockchain` from the prune height, plus `rescanutxoset` when the node has it) is available as "Rescan" for older funds, with the same pruning limits as the main wallet (plan §8.3).
- Removing an escrow is refused while it holds or expects coins.

### 2.5 Limits

- An escrow protects against theft by a single party. It does not decide disputes, and a lost key can block funds if fewer than M keys remain. A 2 of 3 escrow tolerates the loss of one key.
- v1 has no automatic refund after a time-out. If one participant disappears, the other two still can spend in a 2 of 3 escrow.
- Fee bumping an unconfirmed escrow payment is not in v1.

## 3. PSBT Tools

- **Input:** base64 text (starts with `cHNidP8`). Whitespace is ignored. Anything else is rejected before it reaches the node. A QR scanner reads single-code files and escrow key lines. A file shown below 1,800 characters also appears as a QR code. Animated multi-part QR codes and file import are planned (§8).
- **Check view:** inputs and who owns them (main wallet or an escrow), recipients with amounts, fee and fee rate, signature count against the required count, status (needs signatures, ready to send, complete), and the net effect on each of the user's wallets.
- **Warnings:** unusually high fee (rate above 1000 lep/vB or a fee above 5 percent of the amount and above 0.001 ELEK), amounts missing from the file, no wallet of the user can sign.
- **Sign:** only with a wallet that owns an unsigned input, with the passphrase, an unlock of at most 10 seconds and `walletlock` in a `finally` path.
- **Combine:** merges signed copies of the same payment (`combinepsbt`). Copies of different payments are refused by the node.
- **Broadcast:** `finalizepsbt` and `sendrawtransaction`. A warned high fee needs an extra confirmation.
- **MUST:** the app never signs without first showing the check view for the exact file that is signed.

## 4. Key Sweep

- **Input:** one or more private keys in WIF format (compressed or uncompressed), one per line. An optional prefix limits the script type (`p2pkh:`, `p2wpkh:`, `p2wpkh-p2sh:`), as in Electrum. Taproot keys are not supported, because the node cannot sign them from a bare key.
- **Network check:** the key prefix must match the build (mainnet or testnet). Because the WIF format equals Bitcoin's, the screen warns that the same key also exists on Bitcoin and that the search looks only at the Elektron network.
- **Scan:** `scantxoutset` over the descriptors `pkh(WIF)`, `wpkh(WIF)` and `sh(wpkh(WIF))` for compressed keys, `pkh(WIF)` for uncompressed keys. It reads the current set of unspent outputs, so it also works on a pruned node, but only when the node is synchronized. It can take minutes, and it can be cancelled.
- **Transaction:** all found outputs are spent into one fresh address of the user's wallet. The fee is estimated from the input types and the chosen rate (Normal). Dust results are refused.
- **Signing:** `signrawtransactionwithkey` with the keys, the script of each output and, for wrapped SegWit outputs, the redeem script. The keys exist only in the request and are not stored. The text field is cleared after the attempt.
- **MUST:** no key is logged, shown again or kept after the sweep. The screen blocks screenshots.
- **MUST:** a sweep never sends to an address other than a new address of the user's own wallet in v1.

## 5. Message Signing and Verification

- **Format:** the signature is the standard "Bitcoin Signed Message" compact signature in base64, the same format Electrum and the node use. For native SegWit addresses (`be1q...`) the header byte is shifted by 8 (values 39 to 42, BIP-137), which is what Electrum produces for such addresses.
- **Signing:** the wallet holds, next to its SegWit descriptors, an inactive `pkh()` descriptor for the same receive keys. The node can then sign for the matching legacy address with `signmessage`, and the app converts the header. Only addresses of the user's own main wallet can sign. The passphrase unlocks for at most 10 seconds.
- **Verification:** for a `be1q...` address the app builds the legacy address with the same key hash, converts the header back and lets the node verify. For a legacy address the node verifies directly. Other address types are reported as unsupported. A wrong header range for the address type is reported as a format error.
- A signature proves control of the key at the time of signing. It is not a payment and cannot be used to spend.
- Escrow (multisig) addresses cannot sign messages in v1.

## 6. Security Requirements

| # | Requirement | Level |
|---|---|---|
| S18 | Escrow and PSBT screens block screenshots and ignore touches from overlays. Passphrases are used for one unlock of at most 10 seconds and the wallet is locked afterwards in a `finally` path | MUST |
| S19 | Every signature is preceded by the check view of the exact file. Outputs are marked as own change or external. Unusual fees warn | MUST |
| S20 | Escrow setup shows a check code and the first address and tells all participants to compare them before any deposit | MUST |
| S21 | Private keys of a sweep are never stored, logged or displayed again. The destination is always an own address | MUST |
| S22 | Imported single private keys are not added to the wallet | MUST |

## 7. Tests

All tests run against a real `elektrond` on regtest (`ELEKTROND_BIN`), with up to four independent wallets simulating separate devices.

- **Escrow:** three participants create keys, activate a 2 of 3 escrow and derive the same descriptor, check code and address; funds arrive; one proposes a payment and signs; another checks and signs; the copies are combined and broadcast; one signature alone cannot be sent; a wrong passphrase signs nothing; signing twice is refused; backup text restores the same escrow after removal and the rescan finds the funds; removal with funds is refused; validation rejects wrong thresholds, duplicate keys, malformed keys, keys of the wrong network and non-members.
- **PSBT:** garbage and truncated input are rejected before the node is asked.
- **Sweep:** keys of three script types and an uncompressed key are funded, found, spent into the wallet with an effective fee rate within the chosen range, and a second scan finds nothing; invalid, wrong-network and dust cases are rejected.
- **Messages:** sign and verify for a SegWit address and a legacy address, tampered message and wrong address invalid, bad format and unsupported address detected, foreign address and wrong passphrase refused, the wallet is locked afterwards.
- **Not yet covered:** interoperability with Electrum and Bitcoin Core as the other escrow participants, hardware signers, and the Android screens.

## 8. Open Questions

| # | Question | Recommendation |
|---|---|---|
| E1 | Offer a time-locked refund path (miniscript) so that funds return to the buyer after a deadline? | Phase 4. The node supports miniscript descriptors, but the signing flow and the explanation need their own design. |
| E2 | Transfer large PSBTs by animated QR and files | Phase 4. Single QR codes (up to about 1,800 characters), text copy and share work in v1. |
| E3 | Hardware wallet or air-gapped signers as participants | Phase 5, after the QR transfer exists. |
| E4 | A multisig participant without this app (Electrum, Bitcoin Core) | Test in Phase 4. The descriptors follow BIP-48 and standard `sortedmulti`, so it should work. |
| E5 | Sign messages with escrow addresses | Not planned for v1. |

## 9. Checklist

- [x] Escrow create, activate, address, backup, restore, remove
- [x] PSBT check, sign, combine, finalize, broadcast
- [x] Key sweep for three script types
- [x] Message sign and verify with SegWit and legacy addresses
- [x] Integration tests against a real node
- [ ] Interoperability test with Electrum and Bitcoin Core
- [x] Single-code QR scan and display
- [ ] Animated QR transfer and file import of PSBTs
- [ ] Time-locked refund option (E1)
- [ ] Independent review of the escrow setup and the sweep
