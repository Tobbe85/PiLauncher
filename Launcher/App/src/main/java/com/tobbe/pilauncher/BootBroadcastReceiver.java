package com.tobbe.pilauncher;

import static com.tobbe.pilauncher.MainActivity.sharedPreferences;
import static com.tobbe.pilauncher.SettingsProvider.KEY_AUTORUN;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tobbe.pilauncher.platforms.AbstractPlatform;

public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AbstractPlatform.isOculusHeadset()) {
            return; //unsupported
        }

        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            boolean autorunEnabled = sharedPreferences.getBoolean(KEY_AUTORUN, true);
            if (autorunEnabled) {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }
}