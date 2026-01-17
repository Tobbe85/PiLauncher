package com.tobbe.pilauncher.platforms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import com.tobbe.pilauncher.MainActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class WinlatorPlatform extends AbstractPlatform {

    private static final String TAG = "WinlatorPlatform";
    public static final String PACKAGE_PREFIX = "winlator/";

    private static final File WINLATOR_ROOT =
            new File("/data/com.winlator.cmod/Winlator/containers");

    /* ------------------------------------------------------------ */
    /*      KEIN @Override – Methode existiert NICHT im Parent       */
    /* ------------------------------------------------------------ */

    public boolean isSupported(Context context) {
        return WINLATOR_ROOT.exists();
    }

    /* ------------------------------------------------------------ */
    /*      KEIN @Override – wie bei PSPPlatform                     */
    /* ------------------------------------------------------------ */

    public ArrayList<ApplicationInfo> getInstalledApps(Context context) {
        ArrayList<ApplicationInfo> output = new ArrayList<>();

        if (!isSupported(context)) return output;

        for (File container : Objects.requireNonNull(WINLATOR_ROOT.listFiles())) {
            File desktopDir = new File(container, "desktop");
            if (!desktopDir.isDirectory()) continue;

            for (File desktop : Objects.requireNonNull(desktopDir.listFiles())) {
                if (!desktop.getName().endsWith(".desktop")) continue;

                ApplicationInfo app = new ApplicationInfo();
                app.name = desktop.getName().replace(".desktop", "");
                app.packageName = PACKAGE_PREFIX + desktop.getAbsolutePath();
                output.add(app);
            }
        }
        return output;
    }

    /* ------------------------------------------------------------ */
    /*  loadIcon existiert im Parent → @Override OK, optional        */
    /* ------------------------------------------------------------ */

    @Override
    public void loadIcon(Activity activity,
                         ImageView icon,
                         ApplicationInfo app,
                         String name) {

        try {
            File desktop =
                    new File(app.packageName.substring(PACKAGE_PREFIX.length()));

            File icons64 =
                    new File(desktop.getParentFile().getParentFile(), "icons/64");

            File iconFile =
                    new File(icons64, name + ".png");

            if (iconFile.isFile()) {
                Bitmap bmp = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                activity.runOnUiThread(() -> icon.setImageBitmap(bmp));
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Icon load failed", e);
        }

        // Fallback: Standard-Icon-Handling
        super.loadIcon(activity, icon, app, name);
    }

    /* ------------------------------------------------------------ */
    /*  ABSTRACT → @Override MUSS vorhanden sein                    */
    /* ------------------------------------------------------------ */

    @Override
    public boolean runApp(Context context,
                          ApplicationInfo app,
                          boolean multiwindow) {

        if (context == null || app == null || app.packageName == null) {
            return false;
        }

        try {
            String desktopPath =
                    app.packageName.substring(PACKAGE_PREFIX.length());

            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("winlator_shortcut", desktopPath);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Winlator shortcut", e);
            return false;
        }
    }
}