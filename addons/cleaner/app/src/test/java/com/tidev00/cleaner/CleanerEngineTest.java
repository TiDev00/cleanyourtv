package com.tidev00.cleaner;

import org.junit.Test;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CleanerEngineTest {

    @Test
    public void testParseMemInfoStandard() throws Exception {
        String data = "MemTotal: 2097152 kB\n" +
                      "MemFree: 524288 kB\n" +
                      "MemAvailable: 1048576 kB\n" +
                      "Buffers: 32768 kB\n" +
                      "Cached: 262144 kB\n";
        long[] mem = CleanerEngine.parseMemInfo(new StringReader(data));
        assertEquals(1024, mem[0]); // 1048576 kB / 1024 = 1024 MB
        assertEquals(2048, mem[1]); // 2097152 kB / 1024 = 2048 MB
    }

    @Test
    public void testParseMemInfoFallbackWithoutMemAvailable() throws Exception {
        String data = "MemTotal: 1048576 kB\n" +
                      "MemFree: 102400 kB\n" +
                      "Buffers: 51200 kB\n" +
                      "Cached: 102400 kB\n";
        long[] mem = CleanerEngine.parseMemInfo(new StringReader(data));
        // Available estimated as Free + Buffers + Cached = 256000 kB / 1024 = 250 MB
        assertEquals(250, mem[0]);
        assertEquals(1024, mem[1]);
    }

    @Test
    public void testParseMemInfoCorruptReturnsNegative() throws Exception {
        String data = "Not a valid meminfo file\n";
        long[] mem = CleanerEngine.parseMemInfo(new StringReader(data));
        assertEquals(-1, mem[0]);
        assertEquals(-1, mem[1]);
    }

    @Test
    public void testIsKillableProtectsSystemAndEssentialPackages() {
        String myPkg = "com.tidev00.cleaner";

        // Never kill self
        assertFalse(CleanerEngine.isKillable(myPkg, myPkg));

        // Never kill core OS or system UI
        assertFalse(CleanerEngine.isKillable("android", myPkg));
        assertFalse(CleanerEngine.isKillable("com.android.systemui", myPkg));
        assertFalse(CleanerEngine.isKillable("com.android.settings", myPkg));
        assertFalse(CleanerEngine.isKillable("com.android.tv.settings", myPkg));
        assertFalse(CleanerEngine.isKillable("com.android.shell", myPkg));
        assertFalse(CleanerEngine.isKillable("com.android.packageinstaller", myPkg));
        assertFalse(CleanerEngine.isKillable("com.google.android.packageinstaller", myPkg));

        // Never kill CleanYourTV launcher or stock launchers
        assertFalse(CleanerEngine.isKillable("com.gathin.tvhome", myPkg));
        assertFalse(CleanerEngine.isKillable("com.google.android.tvlauncher", myPkg));
        assertFalse(CleanerEngine.isKillable("com.google.android.apps.tv.launcherx", myPkg));

        // Never kill TV Bluetooth (remote controls) or SetupWraith (Wi-Fi)
        assertFalse(CleanerEngine.isKillable("com.android.bluetooth", myPkg));
        assertFalse(CleanerEngine.isKillable("com.google.android.tungsten.setupwraith", myPkg));

        // Never kill keyboard / IME
        assertFalse(CleanerEngine.isKillable("com.google.android.inputmethod.latin", myPkg));
        assertFalse(CleanerEngine.isKillable("com.google.android.leanbackime", myPkg));

        // Never kill null or blank
        assertFalse(CleanerEngine.isKillable(null, myPkg));
        assertFalse(CleanerEngine.isKillable("", myPkg));
        assertFalse(CleanerEngine.isKillable("   ", myPkg));
    }

    @Test
    public void testIsKillableAllowsThirdPartyAndMediaApps() {
        String myPkg = "com.tidev00.cleaner";

        assertTrue(CleanerEngine.isKillable("com.netflix.ninja", myPkg));
        assertTrue(CleanerEngine.isKillable("org.videolan.vlc", myPkg));
        assertTrue(CleanerEngine.isKillable("com.spotify.tv.android", myPkg));
        assertTrue(CleanerEngine.isKillable("com.amazon.amazonvideo.livingroom", myPkg));
        assertTrue(CleanerEngine.isKillable("com.plexapp.android", myPkg));
    }

    @Test
    public void testFilterKillablePackages() {
        String myPkg = "com.tidev00.cleaner";
        List<String> list = Arrays.asList(
            "com.android.systemui",
            "com.netflix.ninja",
            "com.gathin.tvhome",
            "org.videolan.vlc",
            myPkg
        );
        List<String> filtered = CleanerEngine.filterKillable(list, myPkg);
        assertEquals(2, filtered.size());
        assertTrue(filtered.contains("com.netflix.ninja"));
        assertTrue(filtered.contains("org.videolan.vlc"));
    }

    @Test
    public void testFormatMemory() {
        assertEquals("1024/2048 MB", CleanerEngine.formatMemory(1024, 2048));
        assertEquals("512 MB", CleanerEngine.formatMemory(512, -1));
        assertEquals("", CleanerEngine.formatMemory(-1, 2048));
    }

    @Test
    public void testFormatStorage() {
        long gb = 1024L * 1024L * 1024L;
        assertEquals("4.0/16.0 GB", CleanerEngine.formatStorage(4 * gb, 16 * gb));
        assertEquals("0.5/8.0 GB", CleanerEngine.formatStorage(gb / 2, 8 * gb));
        assertEquals("", CleanerEngine.formatStorage(-1, 8 * gb));
    }

    @Test
    public void testCalculateReclaimed() {
        assertEquals(150, CleanerEngine.calculateReclaimed(1000, 1150));
        assertEquals(0, CleanerEngine.calculateReclaimed(1150, 1000));
        assertEquals(0, CleanerEngine.calculateReclaimed(1000, 1000));
    }

    @Test
    public void testCalculateUsedPercent() {
        assertEquals(50, CleanerEngine.calculateUsedPercent(1000, 2000));
        assertEquals(75, CleanerEngine.calculateUsedPercent(250, 1000));
        assertEquals(0, CleanerEngine.calculateUsedPercent(2000, 2000));
        assertEquals(100, CleanerEngine.calculateUsedPercent(0, 2000));
        assertEquals(0, CleanerEngine.calculateUsedPercent(-1, 2000));
        assertEquals(0, CleanerEngine.calculateUsedPercent(500, 0));
        assertEquals(0, CleanerEngine.calculateUsedPercent(500, -1));
    }
}
