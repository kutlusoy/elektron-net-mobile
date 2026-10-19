#!/usr/bin/env python3
import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("usage: rescanutxoset.py /path/to/elektron-net")
root = Path(sys.argv[1])
path = root / "src" / "wallet" / "rpc" / "wallet.cpp"
text = path.read_text(encoding="utf-8")

if 'static RPCMethod rescanutxoset()' not in text:
    anchor = 'static RPCMethod setwalletflag()\n'
    if anchor not in text:
        raise SystemExit(f"patch anchor not found in {path}: setwalletflag")
    function = r'''static RPCMethod rescanutxoset()
{
    return RPCMethod{
        "rescanutxoset",
        "Elektron Net: scan the current chain UTXO set for outputs owned by this wallet. "
        "This is intended for seed recovery on mandatory-pruned nodes where historical "
        "funding transactions are no longer available. The node must be fully synchronized.\n",
        {},
        RPCResult{RPCResult::Type::OBJ, "", "", {
            {RPCResult::Type::BOOL, "success", "true when the UTXO scan completed"},
        }},
        RPCExamples{HelpExampleCli("rescanutxoset", "") + HelpExampleRpc("rescanutxoset", "")},
        [](const RPCMethod& self, const JSONRPCRequest& request) -> UniValue
        {
            std::shared_ptr<CWallet> const pwallet = GetWalletForJSONRPCRequest(request);
            if (!pwallet) return UniValue::VNULL;

            pwallet->BlockUntilSyncedToCurrentChain();
            LOCK(pwallet->cs_wallet);
            if (pwallet->chain().isInitialBlockDownload()) {
                throw JSONRPCError(RPC_WALLET_ERROR, "Node must be fully synchronized before scanning the UTXO set");
            }

            bilingual_str error;
            if (!pwallet->ScanUTXOSet(error)) {
                throw JSONRPCError(
                    RPC_WALLET_ERROR,
                    error.empty() ? "Failed to scan current UTXO set" : error.original
                );
            }

            UniValue result(UniValue::VOBJ);
            result.pushKV("success", true);
            return result;
        },
    };
}

'''
    text = text.replace(anchor, function + anchor, 1)

command_anchor = '        {"wallet", &rescanblockchain},\n'
if '{"wallet", &rescanutxoset},' not in text:
    if command_anchor not in text:
        raise SystemExit(f"patch anchor not found in {path}: rescanblockchain command")
    text = text.replace(command_anchor, '        {"wallet", &rescanutxoset},\n' + command_anchor, 1)

path.write_text(text, encoding="utf-8")
print(f"Applied the rescanutxoset wallet RPC patch to {path}")
