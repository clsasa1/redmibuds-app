package org.kravchuk.redmibuds;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Challenge/response pairs from HCI capture (PROTOCOL.md §3). */
public class AuthenticationTest {
    static byte[] h(String s) { byte[] r = new byte[s.length() / 2]; for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16); return r; }
    static String x(byte[] b) { StringBuilder s = new StringBuilder(); for (byte v : b) s.append(String.format("%02x", v)); return s.toString(); }
    @Test public void loggedPairs() {
        assertEquals("25721bd38bd8685e0c96843f6ca2eb2b", x(new Authentication().computeChallengeResponse(h("abb2cdc69bb454110e827441213ddc87"))));
        assertEquals("bd8dd81133b3f22080b918f8122d9e59", x(new Authentication().computeChallengeResponse(h("1edd21d1aefcdfabc31ca55d806cc8aa"))));
    }
}
