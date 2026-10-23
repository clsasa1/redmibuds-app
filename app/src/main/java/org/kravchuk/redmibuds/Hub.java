package org.kravchuk.redmibuds;

import java.util.concurrent.CopyOnWriteArrayList;

/** Last-known state of the buds; all writes happen on the main thread. */
final class Hub {
    interface Ui { void refresh(); }
    static final Hub I = new Hub();
    static final BudsConnection conn = new BudsConnection();

    final CopyOnWriteArrayList<Ui> uis = new CopyOnWriteArrayList<>();
    android.bluetooth.BluetoothDevice device;

    String state = "Нет связи";
    boolean connected, authed;
    String fw = "";
    int left = -1, right = -1, box = -1;          // +1000 = charging
    int anc = -1;                                  // 0 off, 1 ANC, 2 transparency
    int ancDepth = -1, trLevel = -1;               // 0..0x13, 0..2
    int eq = -1, adaptive = -1, dual = -1, autoAnswer = -1, ear = -1; // ear: 1 = on
    int position = -1;                             // cfg 0x0C bits
    /** gestures[type][side]: type 1 double,2 triple,3 long,4 single,5 other; side 0 L,1 R */
    final int[][] gestures = new int[6][2];
    int longL = -1, longR = -1;                    // long-press cycle
    int find = 0;                                  // 0 none, else side bits

    Hub() { for (int[] g : gestures) { g[0] = -1; g[1] = -1; } }

    void changed() { for (Ui u : uis) try { u.refresh(); } catch (Throwable e) { Log2.add("UI", "refresh: " + e); } }
}
