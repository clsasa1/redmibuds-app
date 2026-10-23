package org.kravchuk.redmibuds;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;

/** Picks the CLASSIC (BR/EDR) address of the buds; never an LE-only entry. */
@SuppressLint("MissingPermission")
final class Devices {
    static final String HINT_MAC = "24:B2:31:DF:71:14";

    static BluetoothAdapter adapter(Context c) {
        BluetoothManager bm = c.getSystemService(BluetoothManager.class);
        return bm == null ? null : bm.getAdapter();
    }
    static List<BluetoothDevice> bonded(Context c) {
        List<BluetoothDevice> l = new ArrayList<>();
        BluetoothAdapter a = adapter(c);
        if (a == null) return l;
        try { l.addAll(a.getBondedDevices()); } catch (SecurityException ignored) {}
        return l;
    }
    static String type(BluetoothDevice d) {
        try {
            switch (d.getType()) {
                case BluetoothDevice.DEVICE_TYPE_CLASSIC: return "CLASSIC";
                case BluetoothDevice.DEVICE_TYPE_LE: return "LE";
                case BluetoothDevice.DEVICE_TYPE_DUAL: return "DUAL";
                default: return "UNKNOWN";
            }
        } catch (SecurityException e) { return "?"; }
    }
    static String name(BluetoothDevice d) { try { String n = d.getName(); return n == null ? "" : n; } catch (SecurityException e) { return ""; } }
    static String uuids(BluetoothDevice d) {
        StringBuilder sb = new StringBuilder();
        try { ParcelUuid[] u = d.getUuids(); if (u != null) for (ParcelUuid p : u) sb.append(p.toString(), 4, 8).append(' '); } catch (SecurityException ignored) {}
        return sb.toString().trim();
    }
    static boolean hasFd2d(BluetoothDevice d) { return (" " + uuids(d) + " ").contains(" fd2d "); }
    static String describe(BluetoothDevice d) { return name(d) + " " + d.getAddress() + " [" + type(d) + "] uuids: " + uuids(d); }

    static void logAll(Context c) {
        List<BluetoothDevice> l = bonded(c);
        Log2.add("DEV", "сопряжённых: " + l.size());
        for (BluetoothDevice d : l) Log2.add("DEV", describe(d));
    }

    /** Manual MAC (if set) > bonded with fd2d > bonded CLASSIC/DUAL named *buds* > remote device for hint MAC. */
    static BluetoothDevice choose(Context c) {
        BluetoothAdapter a = adapter(c);
        String manual = c.getSharedPreferences("p", Context.MODE_PRIVATE).getString("mac", "").trim().toUpperCase();
        if (a != null && BluetoothAdapter.checkBluetoothAddress(manual)) { Log2.add("DEV", "выбран ручной MAC " + manual); return a.getRemoteDevice(manual); }
        BluetoothDevice cur = Hub.I.device;
        if (cur != null && isRedmi(cur) && rfcommCapable(cur)) { Log2.add("DEV", "выбран (пользователь): " + describe(cur)); return cur; }
        BluetoothDevice best = null;
        for (BluetoothDevice d : bonded(c)) if (hasFd2d(d)) { best = d; break; }
        if (best == null) for (BluetoothDevice d : bonded(c)) {
            String t = type(d);
            if ((t.equals("CLASSIC") || t.equals("DUAL")) && isRedmiName(d)) { best = d; break; }
        }
        if (best == null && a != null) {
            try { best = a.getRemoteDevice(HINT_MAC); Log2.add("DEV", "классический не найден среди сопряжённых, пробую " + HINT_MAC); } catch (Throwable ignored) {}
        }
        if (best != null) Log2.add("DEV", "выбран: " + describe(best));
        return best;
    }
    static boolean isRedmiName(BluetoothDevice d) { String n = name(d).toLowerCase(); return n.contains("redmi"); }
    static boolean isRedmi(BluetoothDevice d) { return hasFd2d(d) || isRedmiName(d) || d.getAddress().equalsIgnoreCase(HINT_MAC); }
    static boolean rfcommCapable(BluetoothDevice d) { return !type(d).equals("LE"); }
}
