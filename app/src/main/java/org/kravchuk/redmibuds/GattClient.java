/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.*;

/**
 * BLE GATT diagnostics + Redmi protocol probe.
 * All state is touched on the main thread only; callbacks just post. One GATT op at a time, 3 s timeout each.
 */
@SuppressLint("MissingPermission")
final class GattClient {
    static final UUID CCCD = uuid16("2902"), BATTERY_LEVEL = uuid16("2a19");
    static UUID uuid16(String s) { return UUID.fromString("0000" + s + "-0000-1000-8000-00805f9b34fb"); }
    private static final long OP_TIMEOUT = 3000, PROBE_GAP = 4000;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothDevice target;
    private boolean scanned;
    final List<BluetoothGattCharacteristic> chars = new ArrayList<>();
    private final Map<String, String> readValues = new LinkedHashMap<>();
    private BluetoothGattCharacteristic protoWrite, lastProbe;
    private List<BluetoothGattCharacteristic> probeList = new ArrayList<>();

    // ---- op queue ----
    private interface Op { int start(); /* 0 started, 1 failed, 2 busy (retry) */ }
    static final java.util.Set<String> LE_AUDIO = new java.util.HashSet<>(java.util.Arrays.asList("1844","1846","184d","184e","184f","1850","1853","1855"));
    private static final class Q { final String name; final Op op; int tries; Q(String n, Op o) { name = n; op = o; } }
    private final ArrayDeque<Q> queue = new ArrayDeque<>();
    private Q current;
    private final Runnable opTimeout = () -> { log("ТАЙМАУТ операции: " + (current == null ? "?" : current.name)); finishOp(); };

    GattClient(Context c) { ctx = c.getApplicationContext(); }

    private static void log(String s) { Log2.add("GATT", s); }
    private void state(String s) { Hub.conn.state(s); }
    private void main(Runnable r) { h.post(() -> { try { r.run(); } catch (Throwable e) { log("исключение: " + e); } }); }

    static String shortName(UUID u) {
        String s = u.toString();
        return s.endsWith("-0000-1000-8000-00805f9b34fb") && s.startsWith("0000") ? s.substring(4, 8) : s;
    }
    static boolean standard(UUID u) { String sh = shortName(u); return sh.length() == 4 && sh.startsWith("18"); }
    static String props(int p) {
        StringBuilder b = new StringBuilder();
        if ((p & 0x02) != 0) b.append("R ");
        if ((p & 0x04) != 0) b.append("WNR ");
        if ((p & 0x08) != 0) b.append("W ");
        if ((p & 0x10) != 0) b.append("N ");
        if ((p & 0x20) != 0) b.append("I ");
        if ((p & 0x40) != 0) b.append("SIG ");
        return b.toString().trim();
    }
    static String ascii(byte[] v) {
        StringBuilder b = new StringBuilder();
        for (byte x : v) b.append(x >= 32 && x < 127 ? (char) x : '.');
        return b.toString();
    }
    private static String key(BluetoothGattCharacteristic c) { return shortName(c.getService().getUuid()) + "/" + shortName(c.getUuid()) + "#" + c.getInstanceId(); }

    private void enqueue(String name, Op op) { queue.addLast(new Q(name, op)); pump(); }
    private void pump() {
        while (current == null && !queue.isEmpty()) {
            current = queue.pollFirst();
            int rc;
            try { rc = gatt == null ? 1 : current.op.start(); }
            catch (SecurityException e) { log("SecurityException " + current.name + ": " + e.getMessage()); rc = 1; }
            catch (Throwable e) { log("ошибка " + current.name + ": " + e); rc = 1; }
            if (rc == 0) { h.postDelayed(opTimeout, OP_TIMEOUT); return; }
            if (rc == 2 && current.tries < 5) {
                final Q q = current; q.tries++;
                log("занято (busy), повтор " + q.tries + "/5: " + q.name);
                current = null;
                h.postDelayed(() -> { queue.addFirst(q); pump(); }, 200);
                return;
            }
            log("операция не выполнена: " + current.name);
            current = null;
        }
    }
    private void finishOp() { h.removeCallbacks(opTimeout); current = null; pump(); }

    // ---- connect ----
    void connect(BluetoothDevice dev) {
        close();
        if (dev == null) return;
        target = dev; scanned = false; Hub.I.ble.clear();
        state("BLE: подключение GATT к " + dev.getAddress() + "…");
        try { gatt = dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE); }
        catch (SecurityException e) { state("BLE: нет разрешения BLUETOOTH_CONNECT"); return; }
        h.postDelayed(connTimeout, 12000);
    }
    private final Runnable connTimeout = () -> {
        log("таймаут прямого подключения");
        if (!scanned) scanAndConnect(); else state("BLE: не удалось подключиться");
    };

    private ScanCallback scanCb;
    private void scanAndConnect() {
        scanned = true;
        closeGattOnly();
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        BluetoothLeScanner sc = ad == null ? null : ad.getBluetoothLeScanner();
        if (sc == null || target == null) { state("BLE: сканер недоступен"); return; }
        String nm; try { nm = target.getName(); } catch (SecurityException e) { nm = null; }
        final String name = nm == null ? "" : nm;
        state("BLE: сканирование (" + name + ")…");
        final Set<String> seen = new HashSet<>();
        scanCb = new ScanCallback() {
            @Override public void onScanResult(int t, ScanResult r) {
                main(() -> {
                    if (scanCb == null) return;
                    BluetoothDevice d = r.getDevice();
                    String n = r.getScanRecord() != null ? r.getScanRecord().getDeviceName() : null;
                    if (seen.add(d.getAddress())) log("скан: " + d.getAddress() + " name=" + n + " rssi=" + r.getRssi() + " uuids=" + (r.getScanRecord() == null ? "" : r.getScanRecord().getServiceUuids()));
                    boolean match = d.getAddress().equals(target.getAddress()) || (n != null && !name.isEmpty() && (n.equals(name) || n.contains("Buds")));
                    if (match) {
                        log("совпадение: " + d.getAddress() + " (" + n + ") — подключаюсь");
                        stopScan();
                        gatt = d.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE);
                        h.postDelayed(connTimeout, 12000);
                    }
                });
            }
            @Override public void onScanFailed(int e) { main(() -> state("BLE: ошибка сканирования " + e)); }
        };
        try { sc.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb); }
        catch (SecurityException e) { scanCb = null; state("BLE: нет разрешения на сканирование"); return; }
        h.postDelayed(() -> { if (scanCb != null) { stopScan(); state("BLE: устройство не найдено при сканировании"); } }, 15000);
    }
    private void stopScan() {
        try { BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner().stopScan(scanCb); } catch (Throwable ignored) {}
        scanCb = null;
    }
    private void closeGattOnly() {
        if (gatt != null) { try { gatt.disconnect(); } catch (Throwable ignored) {} try { gatt.close(); } catch (Throwable ignored) {} }
        gatt = null;
    }

    void close() {
        h.removeCallbacksAndMessages(null);
        if (scanCb != null) stopScan();
        closeGattOnly();
        queue.clear(); current = null; chars.clear(); readValues.clear(); protoWrite = null; lastProbe = null; probeList = new ArrayList<>();
        Hub.conn.altSender = null;
    }

    boolean ready() { return gatt != null && !chars.isEmpty(); }

    // ---- ops ----
    private void write(BluetoothGattCharacteristic c, byte[] v, boolean noResp) {
        enqueue("write " + shortName(c.getUuid()), () -> {
            int type = noResp ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            int rc = gatt.writeCharacteristic(c, v, type);
            Log2.add("TX", "[" + shortName(c.getUuid()) + (noResp ? " NR" : " W") + "] " + Proto.hex(v) + " rc=" + rc);
            if (rc == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY) return 2;
            if (rc != BluetoothStatusCodes.SUCCESS) return 1;
            if (noResp) h.postDelayed(this::finishOp, 30); // no-response write: no reliable callback wait
            return 0;
        });
    }
    void writeHex(int idx, byte[] v, boolean noResp) {
        if (idx < 0 || idx >= chars.size()) { log("нет такой характеристики"); return; }
        write(chars.get(idx), v, noResp);
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int ns) {
            main(() -> {
                log("connectionState status=" + status + " newState=" + ns);
                if (g != gatt) { log("событие от старого GATT — игнор"); return; }
                if (ns == BluetoothProfile.STATE_CONNECTED) {
                    h.removeCallbacks(connTimeout);
                    state("BLE: подключено, MTU…");
                    if (!g.requestMtu(512)) g.discoverServices();
                } else if (ns == BluetoothProfile.STATE_DISCONNECTED) {
                    Hub.conn.altSender = null;
                    queue.clear(); h.removeCallbacks(opTimeout); current = null;
                    if (status != 0 && !scanned && chars.isEmpty()) { h.removeCallbacks(connTimeout); scanAndConnect(); }
                    else state("BLE: отключено (status " + status + ")");
                }
            });
        }
        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            main(() -> { log("MTU=" + mtu + " status=" + status); if (g == gatt && chars.isEmpty()) { state("BLE: поиск сервисов…"); g.discoverServices(); } });
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            main(() -> { log("servicesDiscovered status=" + status); if (g == gatt) afterDiscovery(g); });
        }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] v, int status) {
            final byte[] val = v == null ? new byte[0] : v.clone();
            main(() -> {
                if (current == null || !current.name.startsWith("read")) log("неожиданный onCharacteristicRead");
                String s = "hex=" + Proto.hex(val) + " ascii=\"" + GattClient.ascii(val) + "\"" + (status != 0 ? " status=" + status : "");
                log("READ [" + key(c) + "] " + s);
                readValues.put(key(c), s);
                if (BATTERY_LEVEL.equals(c.getUuid()) && val.length > 0 && status == 0) Hub.I.bleBattery(val[0] & 0xFF);
                finishOp();
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            main(() -> {
                log("write [" + shortName(c.getUuid()) + "] status=" + status);
                if (current != null && current.name.startsWith("write")) finishOp();
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            main(() -> { log("CCCD [" + shortName(d.getCharacteristic().getUuid()) + "] status=" + status); if (current != null && current.name.startsWith("cccd")) finishOp(); });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] v) {
            final byte[] val = v == null ? new byte[0] : v.clone();
            main(() -> {
                Log2.add("RX", "[" + shortName(c.getUuid()) + "] " + Proto.hex(val) + "  \"" + GattClient.ascii(val) + "\"");
                if (BATTERY_LEVEL.equals(c.getUuid()) && val.length > 0) Hub.I.bleBattery(val[0] & 0xFF);
                boolean redmi = Proto.hex(val).contains("FE DC BA");
                if (redmi && protoWrite == null && lastProbe != null) adoptProto(lastProbe);
                if (redmi || (protoWrite != null && !standard(c.getService().getUuid()))) {
                    try { Hub.conn.feed(val); } catch (Throwable e) { log("feed: " + e); }
                }
            });
        }
    };

    private void adoptProto(BluetoothGattCharacteristic w) {
        protoWrite = w;
        log("Ответ в формате Redmi (FE DC BA) — канал записи: " + key(w));
        final boolean nr = lastProbeNr || (w.getProperties() & 0x08) == 0;
        log("тип записи: " + (nr ? "WRITE_NR" : "WRITE"));
        Hub.conn.altSender = data -> main(() -> write(w, data, nr));
        state("BLE: канал управления найден (" + shortName(w.getUuid()) + "), аутентификация…");
        Hub.I.onMethod("BLE GATT " + shortName(w.getService().getUuid()) + "/" + shortName(w.getUuid()));
    }

    private void afterDiscovery(BluetoothGatt g) {
        chars.clear();
        List<BluetoothGattCharacteristic> notify = new ArrayList<>(), vendorWrite = new ArrayList<>();
        for (BluetoothGattService s : g.getServices()) {
            boolean vendor = !standard(s.getUuid());
            String sh = shortName(s.getUuid());
            if (LE_AUDIO.contains(sh)) log("  (LE Audio сервис — чтение пропущено, нужен BLUETOOTH_PRIVILEGED)");
            log("SERVICE " + s.getUuid() + (vendor ? "  <-- нестандартный" : "") + (sh.equals("fe95") ? " (Xiaomi fe95)" : "") + (sh.equals("fd2d") ? " (Xiaomi fd2d)" : ""));
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                chars.add(c);
                log("  CHAR " + c.getUuid() + " [" + props(c.getProperties()) + "]");
                for (BluetoothGattDescriptor d : c.getDescriptors()) log("    DESC " + d.getUuid());
                int p = c.getProperties();
                if ((p & 0x02) != 0 && !LE_AUDIO.contains(sh)) enqueue("read " + shortName(c.getUuid()), () -> gatt.readCharacteristic(c) ? 0 : 1);
                if ((p & 0x30) != 0 && (vendor || BATTERY_LEVEL.equals(c.getUuid()))) notify.add(c);
                if ((p & 0x0C) != 0 && vendor) vendorWrite.add(c);
            }
        }
        state("BLE: " + g.getServices().size() + " сервисов, " + chars.size() + " характеристик");
        notify.sort((a, b) -> priority(a) - priority(b));
        for (BluetoothGattCharacteristic c : notify) {
            enqueue("cccd " + shortName(c.getUuid()), () -> {
                BluetoothGattDescriptor d = c.getDescriptor(CCCD);
                if (d == null) { log("нет CCCD у " + shortName(c.getUuid())); return 1; }
                gatt.setCharacteristicNotification(c, true);
                byte[] val = (c.getProperties() & 0x10) != 0 ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                int rc = gatt.writeDescriptor(d, val);
                return rc == BluetoothStatusCodes.SUCCESS ? 0 : rc == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY ? 2 : 1;
            });
        }
        log("вендорских write-характеристик: " + vendorWrite.size() + ", notify: " + notify.size());
        vendorWrite.sort((a, b) -> priority(a) - priority(b));
        probeList = vendorWrite;
        // start probing only after the read/CCCD queue drained
        enqueue("probe-start", () -> { h.post(() -> { finishOp(); probe(0); }); return 0; });
    }

    /** Lower = earlier. Known Xiaomi/Redmi candidates first. */
    static int priority(BluetoothGattCharacteristic c) {
        String cs = shortName(c.getUuid()).toLowerCase(), ss = shortName(c.getService().getUuid()).toLowerCase();
        String[] order = {"fd2d", "ff10", "ff11", "ff12", "ff13", "cf08", "cf07", "af08", "02000200", "97979797"};
        for (int i = 0; i < order.length; i++) if (cs.startsWith(order[i]) || ss.startsWith(order[i])) return i;
        return 100;
    }

    private static final long PROBE_STEP = 1500;
    private void probe(int i) {
        if (gatt == null || protoWrite != null) return;
        if (i >= probeList.size()) {
            log("Проба завершена. Ответов FE DC BA не получено. Батарея 2A19: " + Hub.I.ble);
            state(Hub.I.ble.isEmpty() ? "BLE: канал управления не найден" : "BLE: подключено (батарея 2A19), канал управления не найден");
            return;
        }
        if (!queue.isEmpty() || current != null) { h.postDelayed(() -> probe(i), 300); return; }
        BluetoothGattCharacteristic w = probeList.get(i);
        lastProbe = w;
        log("ПРОБА " + (i + 1) + "/" + probeList.size() + " протокола Redmi через " + key(w) + " [" + props(w.getProperties()) + "]");
        state("BLE: проба " + (i + 1) + "/" + probeList.size() + " (" + shortName(w.getUuid()) + ")");
        // 1) WRITE (with response)
        lastProbeNr = false;
        write(w, Hub.conn.authStartFrame(), false);
        h.postDelayed(() -> { if (protoWrite == null && gatt != null) write(w, Hub.conn.deviceInfoReq(), false); }, PROBE_STEP);
        // 2) WRITE_NR
        h.postDelayed(() -> { if (protoWrite == null && gatt != null) { lastProbeNr = true; write(w, Hub.conn.authStartFrame(), true); } }, 2 * PROBE_STEP);
        h.postDelayed(() -> { if (protoWrite == null && gatt != null) write(w, Hub.conn.deviceInfoReq(), true); }, 3 * PROBE_STEP);
        h.postDelayed(() -> probe(i + 1), 4 * PROBE_STEP);
    }
    private boolean lastProbeNr;

    /** Compact text map of the GATT database with read values. */
    String exportMap() {
        StringBuilder b = new StringBuilder();
        b.append("GATT карта ").append(target == null ? "" : target.getAddress()).append("  ").append(new Date()).append('\n');
        b.append("Свойства: R=read W=write WNR=write-no-resp N=notify I=indicate\n\n");
        if (gatt == null) return b.append("GATT не подключён\n").toString();
        for (BluetoothGattService s : gatt.getServices()) {
            b.append(standard(s.getUuid()) ? "S " : "S* ").append(s.getUuid()).append('\n');
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                b.append("   C ").append(shortName(c.getUuid())).append(" [").append(props(c.getProperties())).append("]");
                String v = readValues.get(key(c));
                if (v != null) b.append("  ").append(v);
                b.append('\n');
            }
        }
        b.append("\nПротокол Redmi: ").append(protoWrite == null ? "не найден" : key(protoWrite)).append('\n');
        b.append("Батарея 2A19: ").append(Hub.I.ble).append('\n');
        return b.toString();
    }
}
