package org.elektronnet.mobile.console

import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun parsed(line: String): ParsedCommand = (CommandParser.parse(line) as ParseResult.Ok).command

class CommandParserTest {
    @Test
    fun parsesPositionalValues() {
        val c = parsed("""getblock 00ab 2 true null 1.5 [1,2] {"a":1} "two words"""")
        assertEquals("getblock", c.method)
        assertEquals(listOf<Any?>("00ab", 2L, true, null, BigDecimal("1.5"), listOf(1L, 2L), mapOf("a" to 1L), "two words"), c.positional)
    }

    @Test
    fun longDigitStringsStayStrings() {
        val txid = "1234567890123456789012345678901234567890123456789012345678901234"
        assertEquals(listOf<Any?>(txid, "007"), parsed("gettransaction $txid 007").positional)
    }

    @Test
    fun parsesNamedArguments() {
        val c = parsed("""createwallet wallet_name=test passphrase="a b" blank=true""")
        assertEquals(emptyList<Any?>(), c.positional)
        assertEquals(mapOf<String, Any?>("wallet_name" to "test", "passphrase" to "a b", "blank" to true), c.named)
    }

    @Test
    fun base64PaddingIsNotANamedArgument() {
        val c = parsed("decodepsbt cHNidP8BAAA=")
        assertEquals(listOf<Any?>("cHNidP8BAAA="), c.positional)
    }

    @Test
    fun keepsBracketsTogetherAcrossSpaces() {
        val c = parsed("""importdescriptors [{"desc": "wpkh(x)", "timestamp": "now"}]""")
        assertEquals(1, c.positional.size)
    }

    @Test
    fun reportsProblems() {
        assertTrue(CommandParser.parse("") is ParseResult.Error)
        assertTrue(CommandParser.parse("getblock \"open") is ParseResult.Error)
        assertTrue(CommandParser.parse("getblock [1,2") is ParseResult.Error)
        assertTrue(CommandParser.parse("getblock [1,") is ParseResult.Error)
        assertTrue(CommandParser.parse("Get-Block") is ParseResult.Error)
        assertTrue(CommandParser.parse("createwallet name=x positional") is ParseResult.Error)
        assertTrue(CommandParser.parse("x ".repeat(CommandParser.MAX_LINE)) is ParseResult.Error)
    }
}

class TiersTest {
    private val tiers = Tiers.parse(File("../native-build/contract/console-tiers.json").readText())

    private fun tier(line: String) = tiers.classify(parsed(line))

    @Test
    fun basicTiers() {
        assertEquals(Tier.Open, tier("getblockchaininfo"))
        assertEquals(Tier.Confirm, tier("addnode 1.2.3.4 add"))
        assertEquals(Tier.Danger, tier("sendtoaddress be1q 1"))
        assertEquals(Tier.Blocked, tier("stop"))
        assertEquals(Tier.Blocked, tier("generatetoaddress 1 x"))
        assertEquals(Tier.Danger, tier("invalidateblock 00"))
    }

    @Test
    fun unknownCommandsDefaultToConfirm() {
        assertEquals(Tier.Confirm, tier("someshinynewcommand 1"))
    }

    @Test
    fun argumentRulesChangeTheTier() {
        assertEquals(Tier.Open, tier("listdescriptors"))
        assertEquals(Tier.Open, tier("listdescriptors false"))
        assertEquals(Tier.Danger, tier("listdescriptors true"))
        assertEquals(Tier.Danger, tier("listdescriptors private=true"))
        assertEquals(Tier.Open, tier("gethdkeys"))
        assertEquals(Tier.Danger, tier("""gethdkeys {"private":true}"""))
        assertEquals(Tier.Danger, tier("walletprocesspsbt cHNidA=="))
        assertEquals(Tier.Open, tier("walletprocesspsbt cHNidA== false"))
        assertEquals(Tier.Blocked, tier("dumptxoutset out.dat rollback"))
        assertEquals(Tier.Danger, tier("dumptxoutset out.dat latest"))
    }

    @Test
    fun heavyCommands() {
        assertTrue(tiers.isHeavy("gettxoutsetinfo"))
        assertFalse(tiers.isHeavy("getblockcount"))
        assertTrue(tiers.names.size > 150)
    }
}

class MaskingTest {
    @Test
    fun masksSecretsButNothingElse() {
        assertEquals("walletpassphrase *** 60", Masking.mask(parsed("walletpassphrase hunter2 60")))
        assertEquals("walletpassphrasechange *** ***", Masking.mask(parsed("walletpassphrasechange old new")))
        assertEquals("createwallet w false false *** true", Masking.mask(parsed("createwallet w false false secret true")))
        assertEquals("createwallet wallet_name=w passphrase=***", Masking.mask(parsed("createwallet wallet_name=w passphrase=secret")))
        assertEquals("signmessagewithprivkey *** hello", Masking.mask(parsed("signmessagewithprivkey KxAbc hello")))
        assertEquals("getblockcount", Masking.mask(parsed("getblockcount")))
    }
}

class PrettyTest {
    @Test
    fun formatsAndTruncates() {
        val text = Pretty.format(mapOf("a" to listOf(1L, 2L), "b" to mapOf("c" to "x"))).text
        assertTrue(text.contains("\"a\": [\n    1,\n    2\n  ]"))
        val big = Pretty.format((1..10_000).toList(), limit = 500)
        assertTrue(big.truncated && big.text.length <= 500)
        assertEquals(Formatted("plain help text", false), Pretty.format("plain help text"))
    }
}

class SessionTest {
    @Test
    fun gatingFollowsTierAndSession() {
        var now = 0L
        val s = ConsoleSession({ now }, timeoutMs = 1_000)
        assertEquals(Decision.SessionExpired, Gate.decide(Tier.Open, s))
        s.unlock()
        assertEquals(Decision.Run, Gate.decide(Tier.Open, s))
        assertEquals(Decision.NeedsConfirm, Gate.decide(Tier.Confirm, s))
        assertEquals(Decision.DangerSwitchOff, Gate.decide(Tier.Danger, s))
        assertEquals(Decision.Blocked, Gate.decide(Tier.Blocked, s))
        s.enableDanger()
        assertEquals(Decision.NeedsDangerConfirm, Gate.decide(Tier.Danger, s))
        now = 900
        s.touch()
        now = 1_800
        assertTrue(s.active)
        now = 2_000
        assertFalse(s.active)
        assertEquals(Decision.SessionExpired, Gate.decide(Tier.Open, s))
        s.unlock()
        assertFalse(s.dangerEnabled)
    }
}

class FileGuardTest {
    private val root = Files.createTempDirectory("exports").toFile()
    private val guard = FileGuard(root)

    private fun refused(r: Resolved) = (r as Resolved.Refused).reason

    @Test
    fun acceptsNamesInsideTheFolder() {
        val ok = guard.resolve("backup.dat") as Resolved.Ok
        assertEquals(File(root.canonicalFile, "backup.dat").path, ok.file.path)
        assertTrue(guard.resolve("sub/dir/x.dat") is Resolved.Ok)
        assertTrue(guard.resolve(File(root.canonicalFile, "abs.dat").path) is Resolved.Ok)
        assertTrue(guard.resolve("x.dat", subdir = "dumps") is Resolved.Ok)
    }

    @Test
    fun refusesEscapes() {
        assertEquals(FileRefusal.Traversal, refused(guard.resolve("../x")))
        assertEquals(FileRefusal.Traversal, refused(guard.resolve("a/../../x")))
        assertEquals(FileRefusal.Outside, refused(guard.resolve("/etc/passwd")))
        assertEquals(FileRefusal.Outside, refused(guard.resolve(root.canonicalFile.path + "-evil/x")))
        assertEquals(FileRefusal.Empty, refused(guard.resolve("  ")))
        assertEquals(FileRefusal.BadName, refused(guard.resolve("a\\b")))
        assertEquals(FileRefusal.BadName, refused(guard.resolve("dir/")))
    }

    @Test
    fun refusesSymlinksAndExistingFiles() {
        val outside = Files.createTempDirectory("outside").toFile()
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertEquals(FileRefusal.Symlink, refused(guard.resolve("link/x.dat")))
        File(root, "have.dat").writeText("x")
        assertEquals(FileRefusal.Exists, refused(guard.resolve("have.dat", mustBeNew = true)))
        assertTrue(guard.resolve("have.dat", mustExist = true) is Resolved.Ok)
        assertEquals(FileRefusal.Missing, refused(guard.resolve("none.dat", mustExist = true)))
    }
}

class HistoryTest {
    private class XorSealer : Sealer {
        override fun seal(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun open(sealed: ByteArray) = sealed.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private fun history(file: File, clock: () -> Long) = ConsoleHistory(file, XorSealer(), clock)

    @Test
    fun storesSealedAndReloads() {
        val file = Files.createTempFile("hist", ".bin").toFile()
        file.delete()
        var now = 1_000L
        val h = history(file) { now }
        h.add("getblockcount")
        now = 2_000
        h.add("walletpassphrase *** 60")
        assertFalse(file.readText().contains("getblockcount"))
        val again = history(file) { now }
        assertEquals(listOf("walletpassphrase *** 60", "getblockcount"), again.all().map { it.text })
    }

    @Test
    fun rotationAndDelete() {
        val file = Files.createTempFile("hist", ".bin").toFile()
        file.delete()
        var now = 0L
        val h = history(file) { now }
        h.add("old")
        now = 10 * ConsoleHistory.DAY_MS
        h.add("recent")
        h.prune(7)
        assertEquals(listOf("recent"), h.all().map { it.text })
        h.remove(h.all().first().id)
        assertEquals(0, h.all().size)
        h.add("x")
        h.prune(0)
        assertEquals(0, h.all().size)
        assertFalse(file.exists())
    }

    @Test
    fun dumpsOlderThanSevenDaysAreDeleted() {
        val exports = Files.createTempDirectory("exp").toFile()
        val dumps = File(exports, "dumps").apply { mkdirs() }
        val old = File(dumps, "old.dat").apply { writeText("x"); setLastModified(0) }
        val fresh = File(dumps, "fresh.dat").apply { writeText("x") }
        val backup = File(exports, "wallet.bak").apply { writeText("x"); setLastModified(0) }
        assertEquals(listOf("old.dat"), ExportsHousekeeping.cleanup(exports, System.currentTimeMillis()))
        assertFalse(old.exists())
        assertTrue(fresh.exists() && backup.exists())
    }
}
