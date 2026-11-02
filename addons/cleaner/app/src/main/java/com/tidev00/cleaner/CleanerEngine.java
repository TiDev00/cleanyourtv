package com.tidev00.cleaner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Core business logic and safety checks for Cleaner addon.
 * Filters safe packages, parses memory counters, and calculates metrics.
 */
public final class CleanerEngine {

    private static final Set<String> PROTECTED_EXACT = new HashSet<>(Arrays.asList(
        "android",
        "com.android.bluetooth",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.shell",
        "com.android.phone",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.gathin.tvhome",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.katniss",
        "com.google.android.gms",
        "com.android.vending",
        "com.google.android.leanbackime",
        "com.google.android.tungsten.setupwraith",
        "android.ext.services",
        "android.ext.shared",
        "com.google.android.ext.services",
        "com.google.android.ext.shared"
    ));

    private static final String[] PROTECTED_PREFIXES = {
        "com.android.providers.",
        "com.google.android.inputmethod",
        "com.android.inputmethod",
        "com.google.android.overlay.modules"
    };

    private CleanerEngine() {}

    /**
     * Determines whether a package is safe to close in the background.
     * Core OS components, launchers, IMEs, Bluetooth (remotes), and this app are protected.
     */
    public static boolean isKillable(String packageName, String myPackageName) {
        if (packageName == null) return false;
        String pkg = packageName.trim();
        if (pkg.isEmpty()) return false;
        if (myPackageName != null && pkg.equalsIgnoreCase(myPackageName.trim())) return false;

        String lower = pkg.toLowerCase(Locale.ROOT);
        if (PROTECTED_EXACT.contains(lower)) return false;

        for (String prefix : PROTECTED_PREFIXES) {
            if (lower.startsWith(prefix)) return false;
        }
        return true;
    }

    /**
     * Filters an input list of candidate packages down to safe killable ones.
     */
    public static List<String> filterKillable(List<String> packages, String myPackageName) {
        List<String> result = new ArrayList<>();
        if (packages == null) return result;
        for (String pkg : packages) {
            if (isKillable(pkg, myPackageName)) {
                result.add(pkg);
            }
        }
        return result;
    }

    /**
     * Parses memory statistics from /proc/meminfo in megabytes: [availableMb, totalMb].
     * Returns [-1, -1] on parse or read failure.
     */
    public static long[] parseMemInfo(Reader source) throws IOException {
        if (source == null) return new long[]{-1, -1};
        BufferedReader reader = new BufferedReader(source);
        long total = -1, available = -1, free = -1, buffers = -1, cached = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 3 || !"kB".equalsIgnoreCase(fields[2])) continue;
            long value;
            try {
                value = Long.parseLong(fields[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (value < 0) continue;
            switch (fields[0]) {
                case "MemTotal:": total = value; break;
                case "MemAvailable:": available = value; break;
                case "MemFree:": free = value; break;
                case "Buffers:": buffers = value; break;
                case "Cached:": cached = value; break;
            }
        }
        if (available < 0 && free >= 0 && buffers >= 0 && cached >= 0) {
            try {
                available = Math.addExact(Math.addExact(free, buffers), cached);
            } catch (ArithmeticException ignored) {
                available = -1;
            }
        }
        if (available >= 0 && total > 0) {
            available = Math.min(available, total);
        }
        return new long[]{
            available < 0 ? -1 : available / 1024,
            total < 0 ? -1 : total / 1024
        };
    }

    /**
     * Formats available and total memory in MB.
     */
    public static String formatMemory(long availableMb, long totalMb) {
        if (availableMb < 0) return "";
        return availableMb + (totalMb > 0 ? "/" + totalMb : "") + " MB";
    }

    /**
     * Formats available and total storage in GB.
     */
    public static String formatStorage(long availableBytes, long totalBytes) {
        if (availableBytes < 0) return "";
        double gb = 1024.0 * 1024.0 * 1024.0;
        String avail = String.format(Locale.US, "%.1f", availableBytes / gb);
        return avail + (totalBytes > 0 ? "/" + String.format(Locale.US, "%.1f", totalBytes / gb) : "") + " GB";
    }

    /**
     * Calculates reclaimed memory in MB, clamped at 0.
     */
    public static long calculateReclaimed(long beforeMb, long afterMb) {
        return Math.max(0, afterMb - beforeMb);
    }

    /**
     * Calculates percentage of used memory [0-100].
     */
    public static int calculateUsedPercent(long availableMb, long totalMb) {
        if (availableMb < 0 || totalMb <= 0) return 0;
        long used = Math.max(0, totalMb - availableMb);
        int pct = (int) ((used * 100) / totalMb);
        return Math.max(0, Math.min(100, pct));
    }
}
