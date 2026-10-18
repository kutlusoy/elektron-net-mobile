# Elektron Net: `elektron-net-mobile` Expert Mode and Node Console Guideline

- **Version:** 0.1 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, security reviewers, translators of the app
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/rpc/` and `src/wallet/rpc/` (RPC surface), `src/init.cpp` (RPC arguments), treat the node's `help` output as ground truth for command names and arguments
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (§3.3 sections, §3.4 node transparency, §4.2 supervisor, §8.3 recovery, §9 security), [`native-build/contract/contract.json`](../native-build/contract/contract.json)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Scope

Expert mode gives advanced users a console to the node of the phone, comparable to the debug console of the desktop wallet: type an RPC command, see the answer. It lives in the Node section of the app, is off by default, and is fully opt-in.

The same console is also the tool the developers use to inspect the node on a device, so it is built early (read-only first) and hardened before it is exposed to users.

**Not in scope:** a general shell, access to files outside the app, and a bundled `elektron-cli`. The mobile build ships `elektrond` only. The console speaks JSON-RPC to the local node through the app's own RPC client.

## 2. Findings From the Node

Checked against a host build of `elektron-net@main` (`447c069`, October 18, 2026) with the contract check tooling.

- The node lists 151 RPC commands in eight groups on regtest: Blockchain 38, Control 6, Mining 7, Network 15, Rawtransactions 21, Signer 1, Util 8, Wallet 55. Some commands are hidden from that list but still callable, for example `invalidateblock`, `reconsiderblock` and the regtest block generation commands. The classification (§5) therefore cannot rely on the `help` list alone, and any command that is not classified, hidden or not, defaults to the Confirm tier.
- Console support commands exist: `help` (command list and per-command help), `getrpcinfo` (active commands), `abortrescan`, `uptime`, `logging`, `getmemoryinfo`.
- There is no `dumpprivkey`, `importprivkey`, `dumpwallet`, `sethdseed` or `importmulti`. Private keys of a descriptor wallet leave the wallet through `listdescriptors` with `private=true` (and possibly `gethdkeys`, **verify**), and through `walletpassphrase` followed by signing commands.
- Commands that move money exist outside the app's PSBT review flow: `sendtoaddress`, `send`, `sendmany`, `sendall`, `sendrawtransaction`.
- Commands that can alter or replace chain state exist: `invalidateblock`, `reconsiderblock`, `preciousblock`, `loadtxoutset`, `pruneblockchain`, `setnetworkactive`, `stop`.
- Because the app always builds against current Core (plan §11.1), the command set changes over time. New commands MUST NOT silently become available as harmless (§5).

## 3. Access and Session Rules

- **MUST:** off by default. Enabling it needs an explicit confirmation screen, shown in the user's language, that explains the risks in plain words: commands can spend funds and reveal keys, nobody legitimate asks for commands to be pasted, a pasted command from a stranger is a scam.
- **MUST:** device authentication (biometric or device credential) when opening the console, and again after the app was in the background longer than a short timeout.
- **MUST:** the session is temporary. Expert mode switches itself off when the app process ends or after a period of inactivity. It is never active after a cold start without the confirmation again.
- **SHOULD:** a visible "Expert" indicator on every screen while the console is unlocked.
- **MUST:** the console screen sets `FLAG_SECURE` (no screenshots, no recent-apps preview). The Node section's other screens keep their own rules.
- **MUST:** leaving the console, backgrounding the app, or locking the device sends `walletlock` for every loaded wallet, because a console command may have unlocked one (§6).

## 4. Console Behavior

- **Input:** a single command line with history navigation. Soft keyboard settings: no autocorrect, no suggestions, no capitalization, with an extra key row for `{ } [ ] " : ,` and a paste button. Hardware keyboards get the usual shortcuts.
- **Syntax:** the format used by the desktop console and `bitcoin-cli`: `command arg1 arg2`, JSON values for arrays and objects, quotes for strings with spaces, named arguments as `name=value`. Nested calls and other conveniences are optional.
- **Autocomplete:** command names come from the node's own `help` list at runtime, so the list always matches the running binary. Argument names come from the per-command help.
- **Help viewer:** `help <command>` renders the node's help text in a scrollable monospace view. The help text stays in English (§8).
- **Wallet selection:** a selector for the target wallet, sent as the `/wallet/<name>` endpoint, so multiple wallets work without a `-rpcwallet` argument.
- **Output:** pretty-printed JSON with collapsible nodes. Large results (for example `listunspent`, `getrawmempool`, `getblock` at high verbosity) are paged or truncated with a clear marker, and a hard memory limit protects the app.
- **Long-running commands:** `rescanblockchain`, `dumptxoutset`, `scantxoutset` and similar run asynchronously with a visible running state and a Cancel action (`abortrescan` for rescans, request abort for others). Timeouts are explicit and shown.
- **Concurrency:** the console uses its own low-priority lane in the app's RPC client. A running console command MUST NOT starve the supervisor's health polling (plan §4.2) or the wallet's own calls.
- **Errors:** RPC errors show the raw node message and code, plus a short localized hint line for the codes the app knows (plan §3.5).
- **Copy and export:** copy of one result is allowed. Export of the whole session follows the log hygiene rule (plan §3.4): masked by default, raw only after explicit confirmation.

## 5. Command Tiers

Every command belongs to exactly one tier. The classification lives in a versioned file in this repository, and an unknown command is treated as tier **Confirm** until it is classified.

| Tier | Behavior | Commands (initial classification) |
|---|---|---|
| **Open** | Runs immediately | Read-only commands: `getblockchaininfo`, `getnetworkinfo`, `getpeerinfo`, `getmempoolinfo`, `getmininginfo`, `getblock`, `getblockheader`, `getrawtransaction`, `gettxout`, `gettxoutsetinfo`, `getchaintips`, `getmemoryinfo`, `getrpcinfo`, `uptime`, `getbalances`, `listunspent`, `listtransactions`, `gettransaction`, `getaddressinfo`, `validateaddress`, `decodepsbt`, `decoderawtransaction`, `estimatesmartfee`, `help`, and the rest of the read-only groups |
| **Confirm** | Shows the exact command and requires one confirmation tap | State-changing commands that do not move funds or reveal keys: `addnode`, `setban`, `clearbanned`, `disconnectnode`, `setnetworkactive`, `logging`, `savemempool`, `getblockfrompeer`, `pruneblockchain`, `preciousblock`, `rescanblockchain`, `abortrescan`, `setlabel`, `keypoolrefill`, `lockunspent`, `abandontransaction`, `bumpfee`, `psbtbumpfee`, `createwallet`, `loadwallet`, `unloadwallet`, `backupwallet`, `importdescriptors`, `ping` |
| **Danger** | Shows the command, requires typing a confirmation word, and device authentication again | Commands that move funds: `sendtoaddress`, `send`, `sendmany`, `sendall`, `sendrawtransaction`, `submitpackage`. Commands that reveal or handle keys: `listdescriptors` with `private=true`, `gethdkeys` with private output, `walletpassphrase`, `walletpassphrasechange`, `encryptwallet`, `signrawtransactionwithwallet`, `walletprocesspsbt` with `sign`, `signmessage`, `signmessagewithprivkey`. Commands that can damage chain state or the wallet: `invalidateblock`, `reconsiderblock`, `dumptxoutset`, `migratewallet`, `restorewallet` |
| **Blocked** | Refused with an explanation | `stop` (use the Node controls so the supervisor knows), `loadtxoutset` (snapshots are managed by the node's own automatic mechanism, manual loading bypasses its checks), test-only commands |

- **Danger tier availability:** the Danger tier is behind a second switch in Expert mode ("Allow commands that move funds or reveal keys"), off by default, and not available at all in the first release if the open question in §9 is decided that way.
- **Moving funds from the console** bypasses the app's PSBT review (plan §8.4). The confirmation screen therefore shows the raw command in full and the typed confirmation word, and recommends the normal Send screen.
- **Argument-dependent tiers:** the tier can depend on arguments (for example `listdescriptors` is Open without `private=true` and Danger with it). The classification file supports argument rules.
- **New Core versions:** when the node reports a command that is not in the classification file, it appears as Confirm, and the contract check (plan §11.2) reports it as unclassified so that the file is updated in the same change that adopts the new Core.

## 6. Secrets and Wallet State

- **MUST:** command history is kept in memory only and cleared when Expert mode ends. Persistent history is an opt-in setting and never stores Danger-tier commands.
- **MUST:** arguments of `walletpassphrase`, `walletpassphrasechange`, `encryptwallet` and any argument named `passphrase` or `privkey` are masked in the visible history and are never written to a log, a crash report, a notification or the clipboard history.
- **MUST:** results of Danger-tier commands that reveal key material are shown once, are not kept in the scrollback after leaving the screen, and are not exported.
- **MUST:** the app tracks which wallets the console unlocked and relocks them (§3). A `walletpassphrase` timeout typed in the console is capped by the app policy (the app may shorten it by sending `walletlock` earlier, it never extends it).
- **SHOULD:** a notice after any unlock performed through the console, so the user does not forget an unlocked wallet.
- Clipboard: copy of results uses the same auto-clear rule as addresses (plan S6).

## 7. Interaction With the Supervisor and the Wallet UI

- The console is just another client of the node. It cannot change the supervisor's state, and commands that would (`stop`, `setnetworkactive` false) are Blocked or Confirm with an explanation that the node status will change.
- The wallet UI reacts to changes made through the console (a wallet loaded or unloaded, a rescan started) by refreshing from the node instead of assuming its own state.
- When the node is not running, the console shows that state and offers to start it, it does not queue commands.

## 8. Internationalization

- Console chrome (buttons, dialogs, the risk confirmation, tier explanations, error hints) is fully localized in every shipped language (plan §3.5), and the risk confirmation text is part of the native speaker review gate.
- Command names, arguments, JSON output and the node's help text stay in English. They are technical identifiers and are not translated.
- The confirmation word for the Danger tier is localized in the dialog, but the typed word is a fixed, language-independent token to avoid keyboard layout problems.

## 9. Open Questions

1. Does the Danger tier ship in the first release, or does Expert mode start with Open, Confirm and Blocked only and add Danger later after a security review?
2. Device authentication on every console open, or once per Expert session?
3. Persistent history as an opt-in setting, or in-memory only without exception?
4. Is the console available in all release builds, or only in a separate "expert" distribution flavor?
5. Which wallet commands accept a file path (`backupwallet`, `restorewallet`, `dumptxoutset`)? Paths outside the app's private directory should be refused or mapped to the Storage Access Framework.

## 10. Test Strategy

- **Parser:** quoting, JSON arrays and objects, named arguments, invalid input, very long lines.
- **Classification:** a test that every command in the node's `help` output has a tier, and a test that unknown and hidden commands resolve to Confirm. The contract check reports unclassified commands for the Core revision used by the build.
- **Gating:** Blocked commands cannot be sent through any code path of the console, Danger commands need the typed confirmation and the second switch.
- **Secrets:** masking of `passphrase` arguments in history, no sensitive text in logs and crash reports, `FLAG_SECURE` on the console screen, relock of every console-unlocked wallet when leaving the console or backgrounding the app.
- **Robustness:** a very large result does not exhaust memory, cancel works for `rescanblockchain`, a console command does not delay the supervisor's polling.
- **Localization:** pseudo-locale run and screenshots of the risk confirmation in the longest locale.

## 11. Roadmap and Checklist

- Phase 1: a developer console in debug builds only, Open tier only, no persistence (inspection tool for the team).
- Phase 3: Expert mode for users with the session rules of §3, the Open, Confirm and Blocked tiers, the classification file and its contract check report.
- Phase 4: Danger tier (if decided in §9), external security review of the console, native speaker review of its texts.

- [ ] Classification file with argument rules, generated from the node's `help` list and reviewed
- [ ] Contract check reports unclassified commands
- [ ] Expert mode gating (confirmation, device authentication, session timeout)
- [ ] Console UI with autocomplete, help viewer, wallet selector, paging, cancel
- [ ] Secrets handling and relock behavior verified
- [ ] Localization of all console texts in the launch locales
- [ ] Security review of the console before it ships
