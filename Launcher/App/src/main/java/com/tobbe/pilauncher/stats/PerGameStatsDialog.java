package com.tobbe.pilauncher.stats;

import com.tobbe.pilauncher.MainActivity;
import com.tobbe.pilauncher.R;
import com.tobbe.pilauncher.SettingsProvider;
import com.tobbe.pilauncher.ui.AppsAdapter;
import com.tobbe.pilauncher.platforms.AbstractPlatform;

import android.app.AlertDialog;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class PerGameStatsDialog {

    private final Context context;
    private final String packageName;

    private static final int BG_DARK      = Color.parseColor("#2D2D2D");
    private static final int CARD_BG      = Color.parseColor("#1E1E1E");
    private static final int ACCENT       = Color.parseColor("#F2D89F");
    private static final int TEXT_PRIMARY = Color.parseColor("#EAEAEA");
    private static final int TEXT_MUTED   = Color.parseColor("#8899AA");
    private static final int DIVIDER      = Color.parseColor("#2A2A4A");

    private int bgColor = BG_DARK;
    private int cardColor = CARD_BG;
    private int accentColor = ACCENT;
    private int dividerColor = DIVIDER;
    private int textPrimaryColor = TEXT_PRIMARY;
    private int textMutedColor = TEXT_MUTED;
    private final Runnable onPermissionChanged;
    private static final long MAX_SESSION_DURATION = 2L * 60L * 60L * 1000L;

    public PerGameStatsDialog(Context context, String packageName, Runnable onPermissionChanged) {
        this.context = context;
        this.packageName = packageName;
        this.onPermissionChanged = onPermissionChanged;
        loadGameColors();
    }

    public static boolean shouldShowStatsButton(Context context) {
        boolean hasPermission = AppsAdapter.hasUsageStatsPermission(context);

        boolean declined = MainActivity.sharedPreferences.getBoolean(
                SettingsProvider.KEY_STATS_DECLINED,
                false
        );

        if (hasPermission) {
            MainActivity.sharedPreferences.edit()
                    .putBoolean(SettingsProvider.KEY_STATS_DECLINED, false)
                    .apply();

            return true;
        }

        return !declined;
    }

    private void loadGameColors() {
        try {
            Bitmap bitmap = null;

            int style = MainActivity.sharedPreferences.getInt(
                    SettingsProvider.KEY_CUSTOM_STYLE,
                    MainActivity.DEFAULT_STYLE
            );

            File iconFile = AbstractPlatform.pkg2path(
                    context,
                    MainActivity.STYLES[style] + "." + packageName
            );

            if (iconFile.exists()) {
                bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            }

            if (bitmap == null) {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);

                Drawable icon = ai.loadIcon(pm);
                bitmap = drawableToBitmap(icon);
            }

            if (bitmap == null) {
                return;
            }

            int dominant = AppsAdapter.getDominantColor(bitmap);

            bgColor = dominant;
            cardColor = AppsAdapter.darkenColor(dominant, 0.35f);
            dividerColor = AppsAdapter.darkenColor(dominant, 0.55f);

            if (isDarkColor(cardColor)) {
                textPrimaryColor = Color.parseColor("#F4F4F4");
                textMutedColor = Color.parseColor("#C8D0D8");
                accentColor = brightenColor(dominant, 2.50f);
            } else {
                textPrimaryColor = Color.parseColor("#151515");
                textMutedColor = Color.parseColor("#404040");
                accentColor = AppsAdapter.darkenColor(dominant, 0.45f);
            }

        } catch (Exception ignored) {
            bgColor = BG_DARK;
            cardColor = CARD_BG;
            accentColor = ACCENT;
            dividerColor = DIVIDER;
        }
    }

    private int brightenColor(int color, float factor) {
        return Color.rgb(
                Math.min(255, (int) (Color.red(color) * factor)),
                Math.min(255, (int) (Color.green(color) * factor)),
                Math.min(255, (int) (Color.blue(color) * factor))
        );
    }

    public void show() {
        if (!AppsAdapter.hasUsageStatsPermission(context)) return;

        UsageStatsStore store = new UsageStatsStore(context);
        store.syncFromAndroidUsageStats();

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        long now = System.currentTimeMillis();
        long day = 24L * 60 * 60 * 1000;
        long start30 = now - 30L * day;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();

        long totalToday = store.getPlaytime(packageName, 1);

        long stored30 = store.getStoredPlaytime(packageName, 30);
        long storedToday = store.getStoredPlaytime(packageName, 1);

        long total30 = stored30 - storedToday + totalToday;
        long usStatsTotalTime = total30;

        int launchesToday = 0;
        int launches30 = 0;

        ArrayList<UsageStatsStore.StoredStat> todayStats = store.getStats(1);
        for (UsageStatsStore.StoredStat s : todayStats) {
            if (packageName.equals(s.packageName)) {
                launchesToday = s.launches;
                break;
            }
        }

        ArrayList<UsageStatsStore.StoredStat> monthStats = store.getStats(30);
        for (UsageStatsStore.StoredStat s : monthStats) {
            if (packageName.equals(s.packageName)) {
                launches30 = s.launches;
                break;
            }
        }

        long lastSessionDur = 0;
        long longestSession = 0;
        long lastSessionStart = 0;
        long lastSessionEnd = 0;

        Map<String, Long> packageSessionStart = new HashMap<>();
        Map<String, String> instanceToPackage = new HashMap<>();
        Map<String, Integer> packageActiveCount = new HashMap<>();

        UsageEvents events = usm.queryEvents(start30, now);
        UsageEvents.Event ev = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(ev);

            if (!packageName.equals(ev.getPackageName())) continue;

            int type = ev.getEventType();
            long time = ev.getTimeStamp();

            boolean isStartEvent =
                    type == UsageEvents.Event.ACTIVITY_RESUMED
                            || type == UsageEvents.Event.MOVE_TO_FOREGROUND;

            boolean isEndEvent =
                    type == UsageEvents.Event.ACTIVITY_PAUSED
                            || type == UsageEvents.Event.ACTIVITY_STOPPED
                            || type == UsageEvents.Event.MOVE_TO_BACKGROUND;

            String key = getEventKey(ev);

            if (isStartEvent) {
                int count = packageActiveCount.getOrDefault(packageName, 0);

                if (count == 0) {
                    packageSessionStart.put(packageName, time);
                }

                instanceToPackage.put(key, packageName);
                packageActiveCount.put(packageName, count + 1);
            }

            if (isEndEvent) {
                String ownerPkg = instanceToPackage.remove(key);

                if (ownerPkg != null) {
                    int count = packageActiveCount.getOrDefault(ownerPkg, 0) - 1;

                    if (count <= 0) {
                        Long start = packageSessionStart.remove(ownerPkg);

                        if (start != null) {
                            long dur = time - start;

                            if (dur > 0L) {
                                dur = Math.min(dur, MAX_SESSION_DURATION);

                                longestSession = Math.max(longestSession, dur);
                                lastSessionDur = dur;
                                lastSessionStart = start;
                                lastSessionEnd = start + dur;
                            }
                        }

                        packageActiveCount.remove(ownerPkg);
                    } else {
                        packageActiveCount.put(ownerPkg, count);
                    }
                }
            }
        }

        Long openStart = packageSessionStart.get(packageName);
        if (openStart != null) {
            long dur = now - openStart;

            if (dur > 0L) {
                dur = Math.min(dur, MAX_SESSION_DURATION);

                longestSession = Math.max(longestSession, dur);
                lastSessionDur = dur;
                lastSessionStart = openStart;
                lastSessionEnd = openStart + dur;
            }
        }

        long lastUsed = lastSessionEnd > 0 ? lastSessionEnd : 0;
        long firstStamp = start30;
        long lastStamp = now;

        int activeDays = store.getActiveDays(packageName, 30);

        long avgPerDay = activeDays > 0
                ? total30 / activeDays
                : 0;
        long avgPerLaunch = launches30 > 0 ? total30 / launches30 : 0;

        String appName = getAppName();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackground(createRootBackground());

        TextView header = new TextView(context);
        header.setText("📊 " + appName);
        header.setTextColor(accentColor);
        header.setTextSize(22f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, dp(22));
        root.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        addSectionCard(content, "🎮 " + context.getString(R.string.stats_playtime),
                new String[][]{
                        {context.getString(R.string.stats_last30), formatDuration(total30)},
                        {context.getString(R.string.stats_today), formatDuration(totalToday)},
                        {context.getString(R.string.stats_total), formatDuration(usStatsTotalTime)}
                });

        addSectionCard(content, "▶️ " + context.getString(R.string.stats_starts),
                new String[][]{
                        {context.getString(R.string.stats_last30), String.valueOf(launches30)},
                        {context.getString(R.string.stats_today), String.valueOf(launchesToday)}
                });

        addSectionCard(content, "⏱ " + context.getString(R.string.stats_sessions),
                new String[][]{
                        {context.getString(R.string.stats_lastsession), formatDuration(lastSessionDur)},
                        {context.getString(R.string.stats_longestsession), formatDuration(longestSession)},
                        {context.getString(R.string.stats_perday), formatDuration(avgPerDay)},
                        {context.getString(R.string.stats_perstart), formatDuration(avgPerLaunch)}
                });

        addSectionCard(content, "🕘 " + context.getString(R.string.stats_timestamp),
                new String[][]{
                        {context.getString(R.string.stats_sessionstart), formatDate(lastSessionStart)},
                        {context.getString(R.string.stats_sessionend), formatDate(lastSessionEnd)},
                        {context.getString(R.string.stats_lastusage), formatDate(lastUsed)}
                });

        Button close = createCloseButton();

        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.topMargin = dp(12);
        root.addView(close, closeParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.95f);
            dialog.getWindow().setLayout(width, height);
        }
    }

    private GradientDrawable createRootBackground() {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        bgColor,
                        AppsAdapter.darkenColor(bgColor, 0.35f)
                }
        );

        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return bg;
    }

    private String getAppName() {
        PackageManager pm = context.getPackageManager();

        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);

            return SettingsProvider.getAppDisplayName(
                    context,
                    packageName,
                    ai.loadLabel(pm)
            );
        } catch (Exception e) {
            return packageName;
        }
    }

    private Button createCloseButton() {
        Button close = new Button(context);
        close.setText(context.getString(R.string.stats_close));
        close.setTextColor(accentColor);
        close.setTextSize(12f);
        close.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        close.setBackground(bg);

        return close;
    }

    private void addSectionCard(LinearLayout parent, String title, String[][] rows) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);

        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        card.setBackground(bg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));

        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(accentColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.08f);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, dp(10));
        card.addView(tv, titleParams);

        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View div = new View(context);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                );
                divParams.setMargins(0, dp(6), 0, dp(6));
                div.setBackgroundColor(dividerColor);
                card.addView(div, divParams);
            }

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView label = new TextView(context);
            label.setText(rows[i][0]);
            label.setTextColor(textMutedColor);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

            row.addView(label, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView value = new TextView(context);
            value.setText(rows[i][1]);
            value.setTextColor(textPrimaryColor);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            value.setTypeface(value.getTypeface(), Typeface.BOLD);
            value.setGravity(Gravity.END);

            row.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            card.addView(row);
        }

        parent.addView(card, cardParams);
    }

    private int dp(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "0 min";
        }

        long minutes = millis / 60000;
        long hours = minutes / 60;

        if (hours > 0) {
            return hours + " h " + (minutes % 60) + " min";
        } else {
            return minutes + " min";
        }
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return "—";
        }

        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(new Date(millis));
    }
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 128;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 128;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
    private boolean isDarkColor(int color) {
        double luminance =
                (0.299 * Color.red(color) +
                        0.587 * Color.green(color) +
                        0.114 * Color.blue(color));

        return luminance < 140;
    }
    private String getEventKey(UsageEvents.Event ev) {
        String pkg = ev.getPackageName();
        String cls = ev.getClassName();

        return pkg + "#" + cls;
    }
}
