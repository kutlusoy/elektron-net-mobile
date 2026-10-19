package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerInfoTest {
    @Test
    fun parsesAndOrdersPeers() {
        val raw = Json.parse(
            """[{"id":3,"addr":"1.2.3.4:8333","inbound":true,"network":"ipv4","subver":"/Elektron:4.0.5/","pingtime":0.082,"startingheight":-1},
               {"id":2,"addr":"kutlusoy.duckdns.org:8333","inbound":false,"network":"ipv4","subver":"/Elektron:4.0.5/","pingtime":0.004,"connection_type":"outbound-full-relay"},
               {"noid":true}]""",
        )
        val peers = PeerInfo.parse(raw)
        assertEquals(2, peers.size)
        assertEquals(2L, peers[0].id)
        assertEquals(4.0, peers[0].pingMs!!, 0.001)
        assertEquals(true, peers[1].inbound)
    }

    @Test
    fun missingPingStaysUnknown() {
        val peers = PeerInfo.parse(Json.parse("""[{"id":1,"addr":"1.2.3.4:8333"}]"""))
        assertNull(peers[0].pingMs)
    }

    @Test
    fun garbageGivesEmptyList() {
        assertEquals(0, PeerInfo.parse(null).size)
        assertEquals(0, PeerInfo.parse("x").size)
    }
}
