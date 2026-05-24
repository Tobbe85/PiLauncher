package com.tobbe.pilauncher.stats;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.usage.UsageEvents;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageStatsStore {

    private static final String PREF = "pilauncher_usage_store";
    private static final String KEY_DAY_PREFIX = "day_";
    private static final String KEY_RAW_PREFIX = "raw_";
    private static final String KEY_LAUNCH_PREFIX = "launch_";
    private static final long MAX_SESSION_DURATION = 2L * 60L * 60L * 1000L;

    private final Context context;
    private final SharedPreferences prefs;

    public UsageStatsStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static class StoredStat {
        public String packageName;
        public long playtime;
        public int launches;
    }

    public void syncFromAndroidUsageStats() {
        if (!AllGameStatsDialog.hasUsageStatsPermission(context)) return;

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        long now = System.currentTimeMillis();

        SharedPreferences.Editor editor = prefs.edit();

        long dayMs = 24L * 60L * 60L * 1000L;

        for (int i = 0; i < 45; i++) {

            long dayStart = getDayStart(now - (i * dayMs));
            long dayEnd = Math.min(dayStart + dayMs, now);

            Map<String, Long> totals = new HashMap<>();
            Map<String, Integer> launches = new HashMap<>();

            Map<String, Long> packageSessionStart = new HashMap<>();
            Map<String, String> instanceToPackage = new HashMap<>();
            Map<String, Integer> packageActiveCount = new HashMap<>();

            Map<String, Long> lastLaunchTime = new HashMap<>();

            UsageEvents events = usm.queryEvents(dayStart, dayEnd);
            UsageEvents.Event ev = new UsageEvents.Event();

            while (events.hasNextEvent()) {
                events.getNextEvent(ev);

                String pkg = ev.getPackageName();
                if (pkg == null) continue;

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

                    int count = packageActiveCount.getOrDefault(pkg, 0);

                    if (count == 0) {

                        packageSessionStart.put(pkg, time);

                        long lastLaunch =
                                lastLaunchTime.getOrDefault(pkg, 0L);

                        if (time - lastLaunch > 30_000L) {
                            launches.merge(pkg, 1, Integer::sum);
                            lastLaunchTime.put(pkg, time);
                        }
                    }

                    instanceToPackage.put(key, pkg);
                    packageActiveCount.put(pkg, count + 1);
                }

                if (isEndEvent) {

                    String ownerPkg = instanceToPackage.remove(key);

                    if (ownerPkg != null) {

                        int count =
                                packageActiveCount.getOrDefault(ownerPkg, 0) - 1;

                        if (count <= 0) {

                            Long start =
                                    packageSessionStart.remove(ownerPkg);

                            if (start != null) {

                                long dur = time - start;

                                if (dur > 0L) {

                                    totals.merge(
                                            ownerPkg,
                                            Math.min(dur, MAX_SESSION_DURATION),
                                            Long::sum
                                    );
                                }
                            }

                            packageActiveCount.remove(ownerPkg);

                        } else {

                            packageActiveCount.put(ownerPkg, count);
                        }
                    }
                }
            }

            for (Map.Entry<String, Long> e : packageSessionStart.entrySet()) {

                long dur = dayEnd - e.getValue();

                if (dur > 0L) {

                    totals.merge(
                            e.getKey(),
                            Math.min(dur, MAX_SESSION_DURATION),
                            Long::sum
                    );
                }
            }

            for (Map.Entry<String, Long> e : totals.entrySet()) {

                String pkg = e.getKey();

                editor.putLong(
                        KEY_DAY_PREFIX + dayStart + "_" + pkg,
                        e.getValue()
                );

                editor.putInt(
                        KEY_LAUNCH_PREFIX + dayStart + "_" + pkg,
                        launches.getOrDefault(pkg, 0)
                );
            }
        }

        editor.apply();

        cleanupOldDays(45);
    }

    public long getPlaytime(String packageName, int days) {
        if (days == 1) {
            return getTodayPlaytimeFromEvents(packageName);
        }
        long total = 0L;
        long now = System.currentTimeMillis();

        for (int i = 0; i < days; i++) {
            long dayStart = getDayStart(now - (i * 24L * 60L * 60L * 1000L));
            total += prefs.getLong(KEY_DAY_PREFIX + dayStart + "_" + packageName, 0L);
        }

        return total;
    }

    public ArrayList<StoredStat> getStats(int days) {
        if (days == 1) {
            return getTodayStatsFromEvents();
        }
        Map<String, Long> totals = new HashMap<>();
        Map<String, Integer> launches = new HashMap<>();

        Map<String, ?> all = prefs.getAll();
        long now = System.currentTimeMillis();

        ArrayList<Long> validDays = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            validDays.add(getDayStart(now - (i * 24L * 60L * 60L * 1000L)));
        }

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();

            for (Long day : validDays) {
                String dayPrefix = KEY_DAY_PREFIX + day + "_";
                String launchPrefix = KEY_LAUNCH_PREFIX + day + "_";

                if (key.startsWith(dayPrefix) && entry.getValue() instanceof Long) {
                    String pkg = key.substring(dayPrefix.length());
                    long value = (Long) entry.getValue();
                    totals.merge(pkg, value, Long::sum);
                    break;
                }

                if (key.startsWith(launchPrefix) && entry.getValue() instanceof Integer) {
                    String pkg = key.substring(launchPrefix.length());
                    int value = (Integer) entry.getValue();
                    launches.merge(pkg, value, Integer::sum);
                    break;
                }
            }
        }

        ArrayList<StoredStat> result = new ArrayList<>();

        for (String pkg : totals.keySet()) {
            StoredStat stat = new StoredStat();
            stat.packageName = pkg;
            stat.playtime = totals.getOrDefault(pkg, 0L);
            stat.launches = launches.getOrDefault(pkg, 0);
            result.add(stat);
        }

        return result;
    }

    private void cleanupOldDays(int keepDays) {
        long now = System.currentTimeMillis();
        long oldestAllowed = getDayStart(now - (keepDays * 24L * 60L * 60L * 1000L));

        SharedPreferences.Editor editor = prefs.edit();

        for (String key : prefs.getAll().keySet()) {
            Long day = extractDayFromKey(key);

            if (day != null && day < oldestAllowed) {
                editor.remove(key);
            }
        }

        editor.apply();
    }

    private Long extractDayFromKey(String key) {
        try {
            if (key.startsWith(KEY_DAY_PREFIX)) {
                String rest = key.substring(KEY_DAY_PREFIX.length());
                return Long.parseLong(rest.substring(0, rest.indexOf("_")));
            }

            if (key.startsWith(KEY_RAW_PREFIX)) {
                String rest = key.substring(KEY_RAW_PREFIX.length());
                return Long.parseLong(rest.substring(0, rest.indexOf("_")));
            }

            if (key.startsWith(KEY_LAUNCH_PREFIX)) {
                String rest = key.substring(KEY_LAUNCH_PREFIX.length());
                return Long.parseLong(rest.substring(0, rest.indexOf("_")));
            }
        } catch (Exception ignored) {}

        return null;
    }

    private long getDayStart(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
    private long getTodayPlaytimeFromEvents(String targetPackage) {
        long now = System.currentTimeMillis();
        long todayStart = getDayStart(now);

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        long total = 0L;

        Map<String, Long> packageSessionStart = new HashMap<>();
        Map<String, String> instanceToPackage = new HashMap<>();
        Map<String, Integer> packageActiveCount = new HashMap<>();

        UsageEvents events = usm.queryEvents(todayStart, now);
        UsageEvents.Event ev = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(ev);

            String pkg = ev.getPackageName();
            if (!targetPackage.equals(pkg)) continue;

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
                int count = packageActiveCount.getOrDefault(pkg, 0);

                if (count == 0) {
                    packageSessionStart.put(pkg, time);
                }

                instanceToPackage.put(key, pkg);
                packageActiveCount.put(pkg, count + 1);
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
                                total += Math.min(dur, MAX_SESSION_DURATION);
                            }
                        }

                        packageActiveCount.remove(ownerPkg);
                    } else {
                        packageActiveCount.put(ownerPkg, count);
                    }
                }
            }
        }

        Long openStart = packageSessionStart.get(targetPackage);
        if (openStart != null) {
            long dur = now - openStart;

            if (dur > 0L) {
                total += Math.min(dur, MAX_SESSION_DURATION);
            }
        }

        return total;
    }

    private ArrayList<StoredStat> getTodayStatsFromEvents() {

        long now = System.currentTimeMillis();
        long todayStart = getDayStart(now);

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        Map<String, Long> totals = new HashMap<>();
        Map<String, Integer> launches = new HashMap<>();

        Map<String, Long> packageSessionStart = new HashMap<>();
        Map<String, String> instanceToPackage = new HashMap<>();
        Map<String, Integer> packageActiveCount = new HashMap<>();

        Map<String, Long> lastLaunchTime = new HashMap<>();

        UsageEvents events = usm.queryEvents(todayStart, now);
        UsageEvents.Event ev = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(ev);

            String pkg = ev.getPackageName();
            if (pkg == null) continue;

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

                int count = packageActiveCount.getOrDefault(pkg, 0);

                if (count == 0) {

                    packageSessionStart.put(pkg, time);

                    long lastLaunch =
                            lastLaunchTime.getOrDefault(pkg, 0L);

                    if (time - lastLaunch > 30_000L) {
                        launches.merge(pkg, 1, Integer::sum);
                        lastLaunchTime.put(pkg, time);
                    }
                }

                instanceToPackage.put(key, pkg);
                packageActiveCount.put(pkg, count + 1);
            }

            if (isEndEvent) {

                String ownerPkg = instanceToPackage.remove(key);

                if (ownerPkg != null) {

                    int count =
                            packageActiveCount.getOrDefault(ownerPkg, 0) - 1;

                    if (count <= 0) {

                        Long start =
                                packageSessionStart.remove(ownerPkg);

                        if (start != null) {

                            long dur = time - start;

                            if (dur > 0L) {

                                totals.merge(
                                        ownerPkg,
                                        Math.min(dur, MAX_SESSION_DURATION),
                                        Long::sum
                                );
                            }
                        }

                        packageActiveCount.remove(ownerPkg);

                    } else {

                        packageActiveCount.put(ownerPkg, count);
                    }
                }
            }
        }

        for (Map.Entry<String, Long> e : packageSessionStart.entrySet()) {

            long dur = now - e.getValue();

            if (dur > 0L) {

                totals.merge(
                        e.getKey(),
                        Math.min(dur, MAX_SESSION_DURATION),
                        Long::sum
                );
            }
        }

        ArrayList<StoredStat> result = new ArrayList<>();

        for (String pkg : totals.keySet()) {

            StoredStat stat = new StoredStat();

            stat.packageName = pkg;
            stat.playtime = totals.getOrDefault(pkg, 0L);
            stat.launches = launches.getOrDefault(pkg, 0);

            result.add(stat);
        }

        return result;
    }
    private String getEventKey(UsageEvents.Event ev) {
        String pkg = ev.getPackageName();
        String cls = ev.getClassName();

        return pkg + "#" + cls;
    }
    public long getStoredPlaytime(String packageName, int days) {
        long total = 0L;
        long now = System.currentTimeMillis();

        for (int i = 0; i < days; i++) {
            long dayStart = getDayStart(now - (i * 24L * 60L * 60L * 1000L));

            total += prefs.getLong(
                    KEY_DAY_PREFIX + dayStart + "_" + packageName,
                    0L
            );
        }

        return total;
    }
    public int getActiveDays(String packageName, int days) {
        int activeDays = 0;
        long now = System.currentTimeMillis();

        for (int i = 1; i < days; i++) {
            long dayStart = getDayStart(now - (i * 24L * 60L * 60L * 1000L));
            long play = prefs.getLong(KEY_DAY_PREFIX + dayStart + "_" + packageName, 0L);

            if (play > 0L) {
                activeDays++;
            }
        }

        if (getTodayPlaytimeFromEvents(packageName) > 0L) {
            activeDays++;
        }

        return activeDays;
    }
}
