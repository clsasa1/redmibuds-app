/* SPDX-License-Identifier: AGPL-3.0-or-later. Protocol logic derived from Gadgetbridge (redmibuds), (C) Gadgetbridge contributors. */
package org.kravchuk.redmibuds;

import java.io.ByteArrayOutputStream;

/** Frame: FE DC BA | type | opcode | len(2, BE) | [status 00 if response] | seq | payload | EF */
final class Proto {
    static final byte PHONE_REQUEST = (byte) 0xC4, RESPONSE = 0x04, EARBUDS_REQUEST = (byte) 0xC0, EARBUDS_NOTIFY = (byte) 0xC7;
    static final byte OP_DEVICE_INFO = 0x02, OP_ANC = 0x08, OP_RUN_INFO = 0x09, OP_REPORT_STATUS = 0x0E,
            OP_AUTH_CHALLENGE = 0x50, OP_AUTH_CONFIRM = 0x51, OP_SET_CONFIG = (byte) 0xF2, OP_GET_CONFIG = (byte) 0xF3, OP_NOTIFY_CONFIG = (byte) 0xF4;

    static boolean isRequest(byte type) { return (type & 0x40) != 0; }

    static byte[] encode(byte type, byte opcode, byte seq, byte[] payload) {
        int extra = isRequest(type) ? 1 : 2;
        int len = payload.length + extra;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0xFE); o.write(0xDC); o.write(0xBA); o.write(type); o.write(opcode);
        o.write((len >> 8) & 0xFF); o.write(len & 0xFF);
        if (!isRequest(type)) o.write(0);
        o.write(seq); o.write(payload, 0, payload.length); o.write(0xEF);
        return o.toByteArray();
    }

    static final class Msg {
        byte type, opcode, seq; byte[] payload; byte status;
    }

    /** Parse one complete frame (header..trailer). Returns null if malformed. */
    static Msg parse(byte[] f) {
        if (f.length < 8) return null;
        Msg m = new Msg();
        m.type = f[3]; m.opcode = f[4];
        int off = 7;
        if (!isRequest(m.type)) { m.status = f[off]; off++; }
        if (off >= f.length) return null;
        m.seq = f[off]; off++;
        int plen = f.length - off - 1;
        if (plen < 0) return null;
        m.payload = new byte[plen];
        System.arraycopy(f, off, m.payload, 0, plen);
        return m;
    }

    static String hex(byte[] b) { return hex(b, 0, b.length); }
    static String hex(byte[] b, int s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = s; i < s + n; i++) sb.append(String.format("%02X ", b[i] & 0xFF));
        return sb.toString().trim();
    }
    static byte[] unhex(String s) {
        s = s.replaceAll("[^0-9A-Fa-f]", "");
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return r;
    }
}
