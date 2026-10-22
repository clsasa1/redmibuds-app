package org.kravchuk.redmibuds;

/** Keeps last-known state and forwards events to the visible screen. */
final class Hub implements BudsConnection.Listener {
    static final Hub I = new Hub();
    static final BudsConnection conn = new BudsConnection(I);
    String state = "Не подключено", info = "", method = "";
    int left = -1, right = -1, box = -1, anc = -1, ancLevel = -1, trLevel = -1, eq = -1;
    BudsConnection.Listener ui;
    GattClient gatt;
    final java.util.List<Integer> ble = new java.util.ArrayList<>();
    /** Battery Level 2A19 values (one per GATT battery char). */
    void bleBattery(int v) {
        if (ble.isEmpty() || ble.size() < 2 && !ble.contains(v)) ble.add(v); else ble.set(ble.size() - 1, v);
        info = "Батарея LE (2A19): " + ble + "%";
        if (ui != null) ui.onInfo(info);
        if (left < 0 && ui != null) ui.onBattery(-1, -1, -1);
    }
    android.bluetooth.BluetoothDevice device;

    @Override public void onState(String s) { state = s; if (ui != null) ui.onState(s); }
    @Override public void onBattery(int l, int r, int c) { if (l >= 0) left = l; if (r >= 0) right = r; if (c >= 0) box = c; if (ui != null) ui.onBattery(left, right, box); }
    @Override public void onAnc(int m, int s) {
        anc = m;
        if (s >= 0) { if (m == 1) ancLevel = s; else if (m == 2) trLevel = s; }
        if (ui != null) ui.onAnc(m, s);
    }
    @Override public void onEq(int p) { eq = p; if (ui != null) ui.onEq(p); }
    @Override public void onInfo(String s) { info = s; if (ui != null) ui.onInfo(s); }
    @Override public void onMethod(String m) { method = m; if (ui != null) ui.onMethod(m); }
}
