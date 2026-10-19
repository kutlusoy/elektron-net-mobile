# Elektron Net — `elektron-net-mobile` UTXO Recovery After Pruning Fix Report

- **Version:** 0.1 (draft)
- **Date:** October 20, 2026
- **Audience:** Node developers, Android/Kotlin developers, security reviewers, wallet vendors that restore seeds on pruned nodes
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy)), Milio aka 1000mio ([@spiroflex](https://x.com/spiroflex)) 🇪🇸
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/wallet/wallet.cpp` (`CWallet::ScanUTXOSet()`, `CWallet::CreditUTXOFromChain()`, `CWallet::AddToWallet()`), `native-build/patches/rescanutxoset.py` in this repository, `app/src/test/java/org/elektronnet/mobile/wallet/UtxoRecoveryIntegrationTest.kt`
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) §8.3 and open item X1, [`WHITEPAPER.md`](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md)

---

## 1. Summary

A wallet restored from its recovery phrase on a pruned node recovers outputs that are still inside the retained blocks through `rescanblockchain`. Outputs created before the pruning boundary are recovered from the current UTXO set by `CWallet::ScanUTXOSet()`. Two defects were found in that second path. The first is fixed by a build patch. The second is open in the node and is the reason this report exists.

| # | Defect | Status |
|---|---|---|
| D1 | `CreditUTXOFromChain()` builds a partial transaction that holds only the recovered output. Two recovered outputs with the same script, value and index produce the same partial transaction, so the second `AddToWallet()` trips `TxStateSerializedBlockHash(wtx.m_state) == TxStateSerializedBlockHash(state)`. | Fixed in our native build by `rescanutxoset.py` (a dummy input naming the outpoint keeps every partial transaction distinct). Still present in the node source. |
| D2 | The partial transaction cannot have the original txid, so the wallet lists the recovered coin under a synthetic outpoint. The balance is correct, but a payment built from that coin spends an outpoint that does not exist in the UTXO set and is not accepted by the mempool. | Open in the node. Worked around in the app by a PSBT path that uses the real outpoints (section 4). |

## 2. Reproduction (regtest, verified against the patched native build)

1. Create a wallet `w1`, fund two addresses with 5 ELEK each, mine one block.
2. Mine 400 more blocks so that the funding block is below `pruneheight` (verified: pruneheight 214 at height 511).
3. Create a blank wallet `w2` and import the descriptors of `w1` with a timestamp in the future, so no block rescan can find anything. `w2` shows balance 0.
4. Run `rescanutxoset` on `w2`.

Observed: `getbalances` shows 10 ELEK, `listunspent` lists two coins, but their txids are not the txids of the funding transactions, `gettxout` returns nothing for them, and `sendtoaddress` returns a txid while the transaction never reaches the mempool.

Control: `scantxoutset` with the public descriptors of `w1` returns exactly the two real outpoints and the total of 10 ELEK.

## 3. What is affected

| Component | Affected | Why |
|---|---|---|
| Node (`elektrond`, Elektron-Qt) | Yes, D1 and D2 | `ScanUTXOSet()` runs automatically at wallet load and after a snapshot (`AttachChain`, `MaybeRescanUTXOSetAfterSnapshot`) and creates the same synthetic entries. The `rescanutxoset` RPC is only an additional trigger from our build. |
| This mobile wallet | D2 for coins that are only recoverable from the UTXO set | It ships the node with the D1 patch. Balances are right after a restore, spending those coins through the normal send flow does not work until the PSBT recovery path is built. Coins inside the retained window are not affected. |
| Other wallets built on the same node source | Yes | Same code path. |

## 4. Working recovery path (tested)

`UtxoRecoveryIntegrationTest` proves the following steps against a real node, with the coin recovered only from the UTXO set:

1. `scantxoutset start` with the public descriptors of the wallet returns the real outpoints, amounts and scripts.
2. Build a PSBT with `createpsbt` from a real `txid:vout`.
3. Add the UTXO data with `utxoupdatepsbt` and the descriptors.
4. Sign with `walletprocesspsbt` (the keys stay inside the node), then `finalizepsbt`.
5. `testmempoolaccept` returns `allowed = true`, then `sendrawtransaction`.

The same flow is the planned recovery spend in the app. Rules: outputs already spent or in the mempool are rechecked against the UTXO set and not counted twice, the passphrase is entered once and the wallet is locked again at the end, and the result is shown in the normal review screen with fee and destination.

## 5. Proposal for the node

- **MUST:** `CreditUTXOFromChain()` stops inserting a fabricated transaction under a foreign txid.
- **SHOULD:** a persistent record for a recovered output (outpoint, amount, script, height, derivation) that takes part in the balance, coin selection, PSBT creation and spent tracking, stored in `wallet.dat` next to the normal transactions. The spent state follows the chainstate.
- **MAY:** until then, `ScanUTXOSet()` could skip wallet insertion and only report the outputs, so no misleading balance appears.

## 6. Checklist

- [x] D1 fixed in the native build, regression covered by the contract check
- [x] D2 reproduced and documented, test added
- [ ] Recovery spend through PSBT built in the app (review screen, passphrase, re-check against the UTXO set)
- [ ] Node issue filed with the reproduction above and the proposal in section 5
- [ ] Remove the build patch and the known-defect assertion of the test when the node keys recovered outputs by their real outpoint

## 7. Open Questions

1. Should the app show recovered coins in a separate list until the node fix exists, so the user can tell them apart from normal coins?
2. Should the recovery spend be offered as "Spend recovered coins" in the wallet details, or happen automatically when the normal flow cannot fund a payment?
