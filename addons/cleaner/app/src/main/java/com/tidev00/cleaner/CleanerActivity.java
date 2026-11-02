package com.tidev00.cleaner;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TV Leanback UI for Cleaner addon.
 * Displays live memory/storage dashboard cards with visual progress, provides
 * one-click background app cleanup, and direct shortcuts to TV settings.
 */
public final class CleanerActivity extends Activity {

    private static final class StatsSnapshot {
        final long availMb;
        final long totalMb;
        final long availStorage;
        final long totalStorage;

        StatsSnapshot(long availMb, long totalMb, long availStorage, long totalStorage) {
            this.availMb = availMb;
            this.totalMb = totalMb;
            this.availStorage = availStorage;
            this.totalStorage = totalStorage;
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private volatile Handler background;

    private TextView badgeView;
    private TextView ramMetricView;
    private TextView ramSubtextView;
    private FrameLayout ramTrack;
    private View ramFill;

    private TextView storageMetricView;
    private TextView storageSubtextView;
    private FrameLayout storageTrack;
    private View storageFill;

    private Button cleanButton;
    private TextView statusView;

    private volatile boolean isCleaning = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(10, 17, 14));
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) dp(56), (int) dp(40), (int) dp(56), (int) dp(40));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        scroll.addView(root);
        setContentView(scroll);

        // Header: App Title, Subtitle & Status Badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1.0f));

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(36);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(0, 230, 118));
        titleCol.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.app_subtitle));
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(128, 203, 196));
        subtitle.setPadding(0, (int) dp(2), 0, 0);
        titleCol.addView(subtitle);

        badgeView = new TextView(this);
        badgeView.setText(getString(R.string.system_ready));
        badgeView.setTextSize(13);
        badgeView.setTypeface(null, Typeface.BOLD);
        badgeView.setTextColor(Color.rgb(0, 230, 118));
        badgeView.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));
        badgeView.setBackground(createCardDrawable(Color.rgb(22, 46, 34), Color.rgb(35, 80, 59), 1, 16));
        header.addView(badgeView, new LinearLayout.LayoutParams(-2, -2));

        // Spacing
        addSpacing(root, 28);

        // Dashboard Cards: RAM & Storage
        LinearLayout cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(cardsRow, new LinearLayout.LayoutParams(-1, -2));

        // RAM Card
        LinearLayout ramCard = createCardContainer(Color.rgb(20, 34, 27), Color.rgb(36, 59, 45));
        LinearLayout.LayoutParams ramLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        ramLp.setMarginEnd((int) dp(14));
        cardsRow.addView(ramCard, ramLp);

        TextView ramTitle = new TextView(this);
        ramTitle.setText(getString(R.string.ram_header));
        ramTitle.setTextSize(13);
        ramTitle.setTypeface(null, Typeface.BOLD);
        ramTitle.setTextColor(Color.rgb(105, 240, 174));
        ramCard.addView(ramTitle);

        ramMetricView = new TextView(this);
        ramMetricView.setText(getString(R.string.loading));
        ramMetricView.setTextSize(26);
        ramMetricView.setTypeface(null, Typeface.BOLD);
        ramMetricView.setTextColor(Color.WHITE);
        ramMetricView.setPadding(0, (int) dp(6), 0, (int) dp(4));
        ramCard.addView(ramMetricView);

        ramSubtextView = new TextView(this);
        ramSubtextView.setText("");
        ramSubtextView.setTextSize(13);
        ramSubtextView.setTextColor(Color.rgb(160, 185, 175));
        ramCard.addView(ramSubtextView);

        ramTrack = createProgressTrack();
        ramFill = new View(this);
        ramTrack.addView(ramFill, new FrameLayout.LayoutParams(0, -1));
        ramCard.addView(ramTrack);

        // Storage Card
        LinearLayout storageCard = createCardContainer(Color.rgb(20, 34, 27), Color.rgb(36, 59, 45));
        LinearLayout.LayoutParams storageLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        storageLp.setMarginStart((int) dp(14));
        cardsRow.addView(storageCard, storageLp);

        TextView storageTitle = new TextView(this);
        storageTitle.setText(getString(R.string.storage_header));
        storageTitle.setTextSize(13);
        storageTitle.setTypeface(null, Typeface.BOLD);
        storageTitle.setTextColor(Color.rgb(0, 229, 255));
        storageCard.addView(storageTitle);

        storageMetricView = new TextView(this);
        storageMetricView.setText(getString(R.string.loading));
        storageMetricView.setTextSize(26);
        storageMetricView.setTypeface(null, Typeface.BOLD);
        storageMetricView.setTextColor(Color.WHITE);
        storageMetricView.setPadding(0, (int) dp(6), 0, (int) dp(4));
        storageCard.addView(storageMetricView);

        storageSubtextView = new TextView(this);
        storageSubtextView.setText("");
        storageSubtextView.setTextSize(13);
        storageSubtextView.setTextColor(Color.rgb(160, 185, 175));
        storageCard.addView(storageSubtextView);

        storageTrack = createProgressTrack();
        storageFill = new View(this);
        storageTrack.addView(storageFill, new FrameLayout.LayoutParams(0, -1));
        storageCard.addView(storageTrack);

        // Spacing
        addSpacing(root, 32);

        // Hero Action: Clean Button
        cleanButton = new Button(this);
        cleanButton.setText(getString(R.string.clean_button));
        cleanButton.setTextSize(19);
        cleanButton.setTypeface(null, Typeface.BOLD);
        cleanButton.setPadding((int) dp(28), (int) dp(18), (int) dp(28), (int) dp(18));
        applyHeroFocus(cleanButton, false);
        cleanButton.setOnFocusChangeListener((v, hasFocus) -> applyHeroFocus(cleanButton, hasFocus));
        cleanButton.setOnClickListener(v -> performCleanup());
        cleanButton.setFocusable(true);
        root.addView(cleanButton, new LinearLayout.LayoutParams(-1, -2));
        cleanButton.requestFocus();

        // Status Feedback View
        statusView = new TextView(this);
        statusView.setText("");
        statusView.setTextSize(16);
        statusView.setTypeface(null, Typeface.BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextColor(Color.rgb(105, 240, 174));
        statusView.setPadding(0, (int) dp(14), 0, (int) dp(14));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        // Quick Settings Actions Row
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER);
        actionsRow.setClipChildren(false);
        actionsRow.setClipToPadding(false);
        root.addView(actionsRow, new LinearLayout.LayoutParams(-1, -2));

        Button storageBtn = createSecondaryButton(getString(R.string.settings_storage_button), () -> openSettings(
            Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            Settings.ACTION_SETTINGS
        ));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        sLp.setMarginEnd((int) dp(10));
        actionsRow.addView(storageBtn, sLp);

        Button appsBtn = createSecondaryButton(getString(R.string.settings_apps_button), () -> openSettings(
            Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS
        ));
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        aLp.setMarginStart((int) dp(5));
        aLp.setMarginEnd((int) dp(5));
        actionsRow.addView(appsBtn, aLp);

        Button exitBtn = createSecondaryButton(getString(R.string.back), this::finish);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        eLp.setMarginStart((int) dp(10));
        actionsRow.addView(exitBtn, eLp);

        // Spacing & Tip Note
        addSpacing(root, 22);

        TextView tipView = new TextView(this);
        tipView.setText(getString(R.string.tip_note));
        tipView.setTextSize(13);
        tipView.setTextColor(Color.rgb(120, 145, 135));
        tipView.setGravity(Gravity.CENTER);
        root.addView(tipView, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout createCardContainer(int bgColor, int strokeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) dp(24), (int) dp(20), (int) dp(24), (int) dp(20));
        card.setBackground(createCardDrawable(bgColor, strokeColor, 1, 16));
        return card;
    }

    private FrameLayout createProgressTrack() {
        FrameLayout track = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) dp(8));
        lp.setMargins(0, (int) dp(14), 0, 0);
        track.setLayoutParams(lp);
        track.setBackground(createCardDrawable(Color.rgb(32, 54, 43), 0, 0, 4));
        return track;
    }

    private Button createSecondaryButton(String text, Runnable action) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setPadding((int) dp(16), (int) dp(14), (int) dp(16), (int) dp(14));
        applySecondaryFocus(btn, false);
        btn.setOnFocusChangeListener((v, hasFocus) -> applySecondaryFocus(btn, hasFocus));
        btn.setOnClickListener(v -> action.run());
        btn.setFocusable(true);
        return btn;
    }

    private void applyHeroFocus(Button btn, boolean hasFocus) {
        if (hasFocus) {
            btn.setBackground(createCardDrawable(Color.rgb(0, 230, 118), 0, 0, 14));
            btn.setTextColor(Color.rgb(10, 24, 17));
            btn.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start();
        } else {
            btn.setBackground(createCardDrawable(Color.rgb(28, 48, 38), Color.rgb(0, 230, 118), 2, 14));
            btn.setTextColor(Color.WHITE);
            btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private void applySecondaryFocus(Button btn, boolean hasFocus) {
        if (hasFocus) {
            btn.setBackground(createCardDrawable(Color.WHITE, 0, 0, 12));
            btn.setTextColor(Color.rgb(15, 23, 19));
            btn.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
        } else {
            btn.setBackground(createCardDrawable(Color.rgb(24, 38, 31), Color.rgb(42, 66, 54), 1, 12));
            btn.setTextColor(Color.rgb(236, 239, 241));
            btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private GradientDrawable createCardDrawable(int bgColor, int strokeColor, int strokeWidth, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) {
            gd.setStroke((int) dp(strokeWidth), strokeColor);
        }
        return gd;
    }

    private void addSpacing(LinearLayout parent, int dp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(-1, (int) dp(dp)));
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }

    private void openSettings(String primaryAction, String fallbackAction) {
        try {
            startActivity(new Intent(primaryAction));
        } catch (Exception ignoredPrimary) {
            try {
                startActivity(new Intent(fallbackAction));
            } catch (Exception ignoredFallback) {
                // Settings activity unavailable on this device
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        worker = new HandlerThread("addon-cleaner");
        worker.start();
        Handler current = new Handler(worker.getLooper());
        background = current;

        current.post(new Runnable() {
            @Override
            public void run() {
                if (background != current) return;
                sampleStats();
                if (background == current) {
                    current.postDelayed(this, 5000);
                }
            }
        });
    }

    @Override
    public void onPause() {
        background = null;
        if (worker != null) {
            worker.quitSafely();
            worker = null;
        }
        ui.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private StatsSnapshot sampleStatsBackground() {
        long[] mem = readMemory();
        long availStorage = -1;
        long totalStorage = -1;
        try {
            File data = Environment.getDataDirectory();
            availStorage = data.getFreeSpace();
            totalStorage = data.getTotalSpace();
        } catch (Exception ignored) {}
        return new StatsSnapshot(mem[0], mem[1], availStorage, totalStorage);
    }

    private void applyStatsToUi(StatsSnapshot stats) {
        if (isFinishing() || isDestroyed()) return;

        if (stats.availMb >= 0 && stats.totalMb > 0) {
            int ramUsedPct = CleanerEngine.calculateUsedPercent(stats.availMb, stats.totalMb);
            long usedMb = Math.max(0, stats.totalMb - stats.availMb);
            ramMetricView.setText(getString(R.string.ram_metric, formatMb(usedMb), ramUsedPct));
            ramSubtextView.setText(getString(R.string.ram_subtext, formatMb(stats.availMb), formatMb(stats.totalMb)));
            updateProgress(ramTrack, ramFill, ramUsedPct, Color.rgb(0, 230, 118));
        }

        if (stats.availStorage >= 0 && stats.totalStorage > 0) {
            long usedStorage = Math.max(0, stats.totalStorage - stats.availStorage);
            int storageUsedPct = (int) ((usedStorage * 100) / stats.totalStorage);
            storageMetricView.setText(getString(R.string.storage_metric, formatBytes(usedStorage)));
            storageSubtextView.setText(getString(R.string.storage_subtext, formatBytes(stats.availStorage), formatBytes(stats.totalStorage)));
            updateProgress(storageTrack, storageFill, storageUsedPct, Color.rgb(0, 229, 255));
        }
    }

    private void sampleStats() {
        StatsSnapshot stats = sampleStatsBackground();
        ui.post(() -> applyStatsToUi(stats));
    }

    private String formatMb(long mb) {
        if (mb >= 1024) {
            return String.format(Locale.US, "%.1f GB", mb / 1024.0);
        }
        return mb + " MB";
    }

    private String formatBytes(long bytes) {
        double gb = 1024.0 * 1024.0 * 1024.0;
        return String.format(Locale.US, "%.1f GB", bytes / gb);
    }

    private void updateProgress(FrameLayout track, View fill, int percent, int fillColor) {
        fill.setBackground(createCardDrawable(fillColor, 0, 0, 4));
        track.post(() -> {
            int trackWidth = track.getWidth();
            if (trackWidth > 0) {
                int fillWidth = Math.max((int) dp(4), (trackWidth * percent) / 100);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                lp.width = Math.min(trackWidth, fillWidth);
                fill.setLayoutParams(lp);
            }
        });
    }

    private long[] readMemory() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                if (mi.totalMem > 0) {
                    return new long[]{mi.availMem / (1024 * 1024), mi.totalMem / (1024 * 1024)};
                }
            }
        } catch (Exception ignored) {}

        try (FileReader reader = new FileReader("/proc/meminfo")) {
            return CleanerEngine.parseMemInfo(reader);
        } catch (Exception ignored) {
            return new long[]{-1, -1};
        }
    }

    private void performCleanup() {
        if (isCleaning) return;
        isCleaning = true;
        // Keep button enabled to preserve Android TV remote D-pad focus
        cleanButton.setText(getString(R.string.cleaning));
        badgeView.setText(getString(R.string.system_busy));
        badgeView.setTextColor(Color.rgb(255, 215, 64));
        statusView.setText(getString(R.string.cleaning));
        statusView.setTextColor(Color.rgb(255, 215, 64));

        Handler current = background;
        if (current == null) {
            isCleaning = false;
            cleanButton.setText(getString(R.string.clean_button));
            return;
        }

        current.post(() -> {
            long beforeAvail = readMemory()[0];

            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            Set<String> packagesToKill = new HashSet<>();

            // 1. Enumerate candidate user/non-system apps
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (ApplicationInfo app : apps) {
                    boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (!isSystem && CleanerEngine.isKillable(app.packageName, getPackageName())) {
                        packagesToKill.add(app.packageName);
                    }
                }
            } catch (Exception ignored) {}

            // 2. Also check running processes if available
            if (am != null) {
                try {
                    List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                    if (procs != null) {
                        for (ActivityManager.RunningAppProcessInfo proc : procs) {
                            if (proc.pkgList != null) {
                                for (String pkg : proc.pkgList) {
                                    if (CleanerEngine.isKillable(pkg, getPackageName())) {
                                        packagesToKill.add(pkg);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                for (String pkg : packagesToKill) {
                    try {
                        am.killBackgroundProcesses(pkg);
                    } catch (Exception ignored) {}
                }
            }

            System.gc();

            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {}

            long afterAvail = readMemory()[0];
            long reclaimed = CleanerEngine.calculateReclaimed(beforeAvail, afterAvail);
            final int count = packagesToKill.size();
            final StatsSnapshot updatedStats = sampleStatsBackground();

            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                isCleaning = false;
                cleanButton.setText(getString(R.string.clean_button));
                badgeView.setText(getString(R.string.system_ready));
                badgeView.setTextColor(Color.rgb(0, 230, 118));
                applyStatsToUi(updatedStats);

                if (count > 0 && reclaimed > 0) {
                    statusView.setText(getString(R.string.clean_done, String.valueOf(reclaimed), count));
                    statusView.setTextColor(Color.rgb(105, 240, 174));
                } else if (count > 0) {
                    statusView.setText(getString(R.string.clean_done, "0", count));
                    statusView.setTextColor(Color.rgb(105, 240, 174));
                } else {
                    statusView.setText(getString(R.string.clean_noop));
                    statusView.setTextColor(Color.rgb(105, 240, 174));
                }
            });
        });
    }
}
