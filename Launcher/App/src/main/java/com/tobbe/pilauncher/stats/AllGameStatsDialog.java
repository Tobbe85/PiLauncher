package com.tobbe.pilauncher.stats;

import com.tobbe.pilauncher.MainActivity;
import com.tobbe.pilauncher.R;
import com.tobbe.pilauncher.SettingsProvider;
import com.tobbe.pilauncher.platforms.AbstractPlatform;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

public class AllGameStatsDialog {

    private static final int GOLD_COLOR = Color.parseColor("#F2D89F");
    private static final int DARKER_BG = Color.parseColor("#1E1E1E");

    private final Context context;
    private final PackageManager packageManager;
    private final Runnable onPermissionChanged;

    private AlertDialog dialog;
    private LinearLayout listContainer;
    private TextView summaryText;

    public enum Range {
        TODAY,
        WEEK,
        MONTH
    }

    public AllGameStatsDialog(Context context, Runnable onPermissionChanged) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.onPermissionChanged = onPermissionChanged;
    }

    public void show() {
        if (!hasUsageStatsPermission(context)) return;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackground(new RoundedDrawable(getLauncherBackground(), dp(20)));

        TextView title = new TextView(context);
        title.setText("📊 " + context.getString(R.string.allstats_statistics));
        title.setTextColor(GOLD_COLOR);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 22);
        root.addView(title);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, 0, 0, 18);

        Button today = createRangeButton(context.getString(R.string.allstats_today));
        Button week = createRangeButton(context.getString(R.string.allstats_week));
        Button month = createRangeButton(context.getString(R.string.allstats_month));

        buttonRow.addView(today);
        buttonRow.addView(week);
        buttonRow.addView(month);

        root.addView(buttonRow);

        summaryText = new TextView(context);
        summaryText.setTextColor(GOLD_COLOR);
        summaryText.setTextSize(15f);
        summaryText.setPadding(0, 0, 0, 18);
        root.addView(summaryText);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setMinimumHeight(dp(420));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button close = createRangeButton(context.getString(R.string.stats_ok));
        close.setOnClickListener(v -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        closeParams.topMargin = 12;

        root.addView(close, closeParams);

        dialog = new AlertDialog.Builder(context)
                .setView(root)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });

        today.setOnClickListener(v -> loadRange(Range.TODAY));
        week.setOnClickListener(v -> loadRange(Range.WEEK));
        month.setOnClickListener(v -> loadRange(Range.MONTH));

        dialog.show();

        if (dialog.getWindow() != null) {

            int width = (int) (context.getResources()
                    .getDisplayMetrics().widthPixels * 0.92f);

            int height = (int) (context.getResources()
                    .getDisplayMetrics().heightPixels * 0.95f);

            dialog.getWindow().setLayout(width, height);
        }

        loadRange(Range.TODAY);
    }

    private Button createRangeButton(String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(GOLD_COLOR);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setBackgroundColor(DARKER_BG);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(4, 4, 4, 4);
        button.setLayoutParams(params);

        return button;
    }

    private void loadRange(Range range) {
        listContainer.removeAllViews();

        long now = System.currentTimeMillis();
        long startTime = getStartTime(range, now);

        ArrayList<GameStatItem> items = getGameStats(startTime, now);

        Collections.sort(items, (a, b) -> Long.compare(b.playtime, a.playtime));

        long totalTime = 0;
        for (GameStatItem item : items) {
            totalTime += item.playtime;
        }

        summaryText.setText(
                context.getString(R.string.allstats_period) + " " + getRangeName(range) + "\n" +
                        context.getString(R.string.allstats_games) + " " + items.size() + "\n" +
                        context.getString(R.string.allstats_total) + " " + formatDuration(totalTime)
        );

        if (items.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(context.getString(R.string.allstats_notfound));
            empty.setTextColor(GOLD_COLOR);
            empty.setTextSize(16f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 40, 20, 40);
            listContainer.addView(empty);
            return;
        }

        int rank = 1;
        for (GameStatItem item : items) {
            listContainer.addView(createGameRow(rank, item, totalTime));
            rank++;
        }
    }

    private ArrayList<GameStatItem> getGameStats(long startTime, long endTime) {

        UsageStatsStore store = new UsageStatsStore(context);

        store.syncFromAndroidUsageStats();

        int days;

        if (startTime >= getStartTime(Range.TODAY, System.currentTimeMillis())) {
            days = 1;
        } else {
            long diff = System.currentTimeMillis() - startTime;

            days = (int) Math.ceil(
                    diff / (24.0 * 60.0 * 60.0 * 1000.0)
            );
        }

        ArrayList<UsageStatsStore.StoredStat> storedStats =
                store.getStats(days);

        ArrayList<GameStatItem> result = new ArrayList<>();

        for (UsageStatsStore.StoredStat stored : storedStats) {

            String pkg = stored.packageName;
            long playtime = stored.playtime;

            if (playtime < 60_000) continue;

            if (shouldIgnorePackage(pkg)) continue;

            try {

                ApplicationInfo appInfo =
                        packageManager.getApplicationInfo(pkg, 0);

                CharSequence originalName =
                        packageManager.getApplicationLabel(appInfo);

                GameStatItem item = new GameStatItem();

                item.packageName = pkg;

                item.appName = SettingsProvider.getAppDisplayName(
                        context,
                        pkg,
                        originalName
                );

                item.appInfo = appInfo;
                item.playtime = playtime;
                item.launches = stored.launches;

                result.add(item);

            } catch (Exception ignored) {}
        }

        return result;
    }

    private View createGameRow(int rank, GameStatItem item, long totalTime) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(14, 12, 14, 12);
        row.setBackgroundColor(DARKER_BG);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 10);
        row.setLayoutParams(rowParams);

        TextView rankText = new TextView(context);
        rankText.setText(String.valueOf(rank));
        rankText.setTextColor(GOLD_COLOR);
        rankText.setTextSize(18f);
        rankText.setTypeface(Typeface.DEFAULT_BOLD);
        rankText.setGravity(Gravity.CENTER);

        row.addView(rankText, new LinearLayout.LayoutParams(50, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(context);

        try {
            AbstractPlatform platform = AbstractPlatform.getPlatform(item.appInfo);
            platform.loadIcon((MainActivity) context, icon, item.appInfo, item.appName);
        } catch (Exception e) {
            icon.setImageDrawable(item.appInfo.loadIcon(packageManager));
        }

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(72, 72);
        iconParams.setMargins(8, 0, 18, 0);
        row.addView(icon, iconParams);

        LinearLayout textBox = new LinearLayout(context);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(context);
        name.setText(item.appName);
        name.setTextColor(GOLD_COLOR);
        name.setTextSize(16f);
        name.setTypeface(Typeface.DEFAULT_BOLD);

        int percent = totalTime > 0 ? (int) ((item.playtime * 100L) / totalTime) : 0;

        TextView details = new TextView(context);
        details.setText(
                formatDuration(item.playtime) +
                        "  •  " + item.launches + " " + context.getString(R.string.allstats_starts) +
                        "  •  " + percent + "%"
        );
        details.setTextColor(GOLD_COLOR);
        details.setTextSize(13f);

        TextView packageText = new TextView(context);
        packageText.setText(item.packageName);
        packageText.setTextColor(Color.parseColor("#bfa45a"));
        packageText.setTextSize(11f);

        textBox.addView(name);
        textBox.addView(details);
        textBox.addView(packageText);

        row.addView(textBox, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        row.setOnClickListener(v -> {
            new PerGameStatsDialog(
                    context,
                    item.packageName,
                    null
            ).show();
        });

        return row;
    }

    private long getStartTime(Range range, long now) {
        Calendar cal = Calendar.getInstance();

        switch (range) {

            case TODAY:
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();

            case WEEK:
                return now - (7L * 24L * 60L * 60L * 1000L);

            case MONTH:
            default:
                return now - (30L * 24L * 60L * 60L * 1000L);
        }
    }

    private String getRangeName(Range range) {
        switch (range) {

            case TODAY:
                return context.getString(R.string.allstats_today);

            case WEEK:
                return context.getString(R.string.allstats_week);

            case MONTH:
            default:
                return context.getString(R.string.allstats_month);
        }
    }

    private boolean shouldIgnorePackage(String packageName) {
        if (packageName.equals(context.getPackageName())) return true;
        // Android Apps
        if (packageName.equals("android")) return true;
        if (packageName.startsWith("com.android.")) return true;
        if (packageName.startsWith("com.google.android.")) return true;
        // Meta Apps
        if (packageName.startsWith("com.oculus.system")) return true;
        if (packageName.startsWith("com.oculus.vrshell")) return true;
        if (packageName.startsWith("com.meta.")) return true;
        // Qualcomm Apps
        if (packageName.startsWith("com.qualcomm.")) return true;
        // Pico Apps
        if (packageName.startsWith("com.pvr.vrshell")) return true;
        if (packageName.startsWith("com.pvr.shortcut")) return true;
        if (packageName.startsWith("com.pvr.settings")) return true;
        if (packageName.startsWith("com.picovr.settings")) return true;
        if (packageName.startsWith("com.pvr.appmanager")) return true;

        return false;
    }

    public static boolean hasUsageStatsPermission(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.getPackageName()
                );
            } else {
                mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.getPackageName()
                );
            }

            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "0 min";
        }

        long minutes = millis / 1000 / 60;
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d h %d min", hours, remainingMinutes);
        } else {
            return String.format(Locale.getDefault(), "%d min", minutes);
        }
    }

    private static class GameStatItem {
        String packageName;
        String appName;
        ApplicationInfo appInfo;
        long playtime;
        int launches;
    }
    private Drawable getLauncherBackground() {
        int opacity = MainActivity.sharedPreferences.getInt(
                SettingsProvider.KEY_CUSTOM_OPACITY,
                7
        );

        int theme = MainActivity.sharedPreferences.getInt(
                SettingsProvider.KEY_CUSTOM_THEME,
                0
        );

        Drawable bg;

        if (theme < MainActivity.THEMES.length) {
            bg = context.getDrawable(MainActivity.THEMES[theme]);
        } else {
            File file = new File(context.getApplicationInfo().dataDir, "theme.png");
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            bg = new BitmapDrawable(context.getResources(), bitmap);
        }

        Drawable copy = bg.getConstantState().newDrawable().mutate();
        copy.setAlpha(255 * opacity / 10);

        return copy;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    private class RoundedDrawable extends Drawable {

        private final Drawable drawable;
        private final float radius;
        private final Path path = new Path();
        private final RectF rect = new RectF();

        RoundedDrawable(Drawable drawable, float radius) {
            this.drawable = drawable;
            this.radius = radius;
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            super.onBoundsChange(bounds);

            drawable.setBounds(bounds);

            rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
            path.reset();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(path);
            drawable.draw(canvas);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            drawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            drawable.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return drawable.getOpacity();
        }
    }
}
