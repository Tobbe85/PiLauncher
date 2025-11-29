package com.tobbe.pilauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class LauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs =
                getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
        boolean near = prefs.getBoolean(SettingsProvider.KEY_NEAR, MainActivity.DEFAULT_NEAR);

        Class<?> target = near ? MainActivityNear.class : MainActivity.class;

        Intent i = new Intent(this, target);
        startActivity(i);
        finish();
    }
}
