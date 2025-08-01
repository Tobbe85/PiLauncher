package com.tobbe.pilauncher.platforms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.tobbe.pilauncher.SettingsProvider;

import java.util.ArrayList;

public class AndroidPlatform extends AbstractPlatform {
    public ArrayList<ApplicationInfo> getInstalledApps(Context context) {
        ArrayList<ApplicationInfo> installedApps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (!isVirtualRealityApp(app)) {
                if (!SettingsProvider.launchIntents.containsKey(app.packageName)) {
                    SettingsProvider.launchIntents.put(app.packageName, pm.getLaunchIntentForPackage(app.packageName));
                }
                if(SettingsProvider.launchIntents.get(app.packageName) != null) {
                    if(!SettingsProvider.installDates.containsKey(app.packageName)) {
                        long installDate;
                        try {
                            PackageInfo packageInfo = pm.getPackageInfo(app.packageName, 0);
                            installDate = packageInfo.firstInstallTime;
                        } catch (PackageManager.NameNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                        SettingsProvider.installDates.put(app.packageName, installDate);
                    }
                    installedApps.add(app);
                }
            }
        }
        return installedApps;
    }

    @Override
    public boolean runApp(Context context, ApplicationInfo app, boolean multiwindow) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launchIntent != null) {
            if (multiwindow) {
                context.getApplicationContext().startActivity(launchIntent);
            } else {
                context.startActivity(launchIntent);
            }
        }else{
            Log.e("runApp", "Failed to launch");
            return false;
        }
        return true;
    }
}