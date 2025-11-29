package com.tobbe.pilauncher.platforms;

import static com.tobbe.pilauncher.MainActivity.DEFAULT_STYLE;
import static com.tobbe.pilauncher.MainActivity.STYLES;
import static com.tobbe.pilauncher.MainActivity.sharedPreferences;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Color;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import com.tobbe.pilauncher.MainActivity;
import com.tobbe.pilauncher.R;
import com.tobbe.pilauncher.SettingsProvider;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public abstract class AbstractPlatform {
    final int style = sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_STYLE, DEFAULT_STYLE);
    private final String ICONS1_URL = "https://raw.githubusercontent.com/Tobbe85/PiLauncher/main/"+STYLES[style]+"/";
    private final String SIDEQUEST_URL = "https://raw.githubusercontent.com/Tobbe85/PiLauncher/main/sidequest/";
    private static final String ICONS_FALLBACK_URL = "https://pilauncher.lwiczka.pl/get_icon.php?id=";
    protected static final HashMap<String, Drawable> iconCache = new HashMap<>();
    protected static final HashSet<String> ignoredIcons = new HashSet<>();

    public static Bitmap applyRoundedCorners(Bitmap bitmap, float dpRadius) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        paint.setAntiAlias(true);

        float radius = dpRadius * Resources.getSystem().getDisplayMetrics().density;
        final RectF rect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rect, radius, radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, null, rect, paint);

        return output;
    }
    public static Bitmap drawableToBitmap(Drawable drawable) {
        // Zielgröße festlegen: 253x253
        final int targetSize = 253;

        // Bitmap in Zielgröße erstellen
        Bitmap scaledBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas scaledCanvas = new Canvas(scaledBitmap);

        // Drawable auf die Zielgröße setzen und zeichnen
        drawable.setBounds(0, 0, targetSize, targetSize);
        drawable.draw(scaledCanvas);

        // Abgerundete Ecken auf dem skalierten Bitmap anwenden
        Bitmap roundedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(roundedBitmap);

        final Paint paint = new Paint();
        paint.setAntiAlias(true);

        float cornerRadius = 24f * Resources.getSystem().getDisplayMetrics().density;

        final RectF rect = new RectF(0, 0, targetSize, targetSize);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaledBitmap, 0, 0, paint);

        return roundedBitmap;
    }

    public static Bitmap makeRounded(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final RectF rect = new RectF(0, 0, width, height);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawOval(rect, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, null, rect, paint);

        return output;
    }
    private Bitmap downloadAndResizeSidequest(String urlStr, int style) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoInput(true);
            connection.connect();

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);

            if (bitmap == null) return null;

            int width = 253;
            int height = 253;

            if (style == 0) {
                width = 450;
                height = 253;
            }

            Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);

            if (style == 2) {
                // Rundes Icon
                Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(output);
                final Paint paint = new Paint();
                final RectF rect = new RectF(0, 0, width, height);

                paint.setAntiAlias(true);
                canvas.drawARGB(0, 0, 0, 0);
                canvas.drawOval(rect, paint);

                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(resized, null, rect, paint);
                return output;
            }

            return resized;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private void downloadIcon(final Activity activity, String pkg, @SuppressWarnings("unused") String name, final Runnable callback) {
        final File file = pkg2path(activity, STYLES[style] + "." + pkg);
        new Thread(() -> {
            try {
                synchronized (pkg) {
                    if (downloadIconFromUrl(ICONS1_URL + pkg + ".png", file)) {
                        if (style == 1) {
                            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                            if (bmp != null) {
                                bmp = applyRoundedCorners(Bitmap.createScaledBitmap(bmp, 253, 253, true), 24);
                                FileOutputStream out = new FileOutputStream(file);
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                                out.close();
                            }
                        }
                        activity.runOnUiThread(callback);
                    } else if (downloadIconFromUrl(ICONS_FALLBACK_URL + pkg + "&set=" + STYLES[style], file)) {
                        if (style == 1) {
                            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                            if (bmp != null) {
                                bmp = applyRoundedCorners(Bitmap.createScaledBitmap(bmp, 253, 253, true), 24);
                                FileOutputStream out = new FileOutputStream(file);
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                                out.close();
                            }
                        }
                        activity.runOnUiThread(callback);
                    } else {
                        // NEU: JSON von SideQuest holen
                        String jsonUrl = SIDEQUEST_URL + pkg + ".json";
                        try {
                            URL url = new URL(jsonUrl);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(5000);
                            conn.setReadTimeout(5000);
                            conn.setRequestMethod("GET");

                            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                reader.close();

                                JSONObject json = new JSONObject(sb.toString());
                                String imgUrl = json.optString("image_url", null);

                                if (imgUrl != null && !imgUrl.isEmpty()) {
                                    Bitmap img = downloadAndResizeSidequest(imgUrl, style);

                                    if (img != null) {
                                        if (style == 1) {
                                            img = applyRoundedCorners(img, 24);
                                        }
                                        FileOutputStream out = new FileOutputStream(file);
                                        img.compress(Bitmap.CompressFormat.PNG, 100, out);
                                        out.close();
                                        activity.runOnUiThread(callback);
                                        return;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        Log.d("Missing icon", file.getName());
                        ignoredIcons.add(STYLES[style] + "." + file.getName());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void loadIcon(Activity activity, ImageView icon, ApplicationInfo app, String name) {
        String pkg = app.packageName;

        if (iconCache.containsKey(STYLES[style]+"."+pkg)) {
            icon.setImageDrawable(iconCache.get(STYLES[style]+"."+pkg));
            return;
        }else{
            PackageManager pm = activity.getPackageManager();
            Resources resources;
            try {
                resources = pm.getResourcesForApplication(app.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return;
            }
            int iconId = app.icon;
            if (iconId == 0) {
                iconId = android.R.drawable.sym_def_app_icon;
            }
            Drawable appIcon = ResourcesCompat.getDrawableForDensity(resources, iconId, DisplayMetrics.DENSITY_XXXHIGH, null);

            Bitmap raw = drawableToBitmap(appIcon);

            Bitmap styledIcon = null;
            switch (style) {
                case 0: // Rechteck
                    styledIcon = Bitmap.createScaledBitmap(raw, 450, 253, true);
                    break;
                case 1: // Quadrat
                    styledIcon = Bitmap.createScaledBitmap(raw, 253, 253, true);
                    break;
                case 2: // Rund
                    styledIcon = Bitmap.createScaledBitmap(raw, 253, 253, true);
                    styledIcon = makeRounded(styledIcon);
                    break;
            }

            if (styledIcon != null) {
                icon.setImageBitmap(styledIcon);
            }
        }

        final File file = pkg2path(activity, STYLES[style]+"."+pkg);
        if (file.exists()) {
            if (updateIcon(icon, file, STYLES[style]+"."+pkg)) {
                return;
            }
        }
        downloadIcon(activity, pkg, name, () -> updateIcon(icon, file, STYLES[style]+"."+pkg));
    }

    public abstract boolean runApp(Context context, ApplicationInfo app, boolean multiwindow);
    public static boolean isImageFileComplete(File imageFile) {
        boolean success = false;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            success = (bitmap != null);
        } catch (Exception e) {
            // do nothing
        }
        if (!success) {
            Log.e("imgComplete", "Failed to read image file: " + imageFile);
        }
        return success;
    }

    public static void clearIconCache() {
        ignoredIcons.clear();
        iconCache.clear();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void clearAllIcons(MainActivity activity) {
        for (String pkg : iconCache.keySet()) {
            final File file = pkg2path(activity, pkg);
            Log.i("Cache file", file.getAbsolutePath() + " | Exists: " + file.exists());
            if (file.exists()) {
                file.delete();
            }
        }
        clearIconCache();
    }

    public static AbstractPlatform getPlatform(ApplicationInfo app) {
        if (app.packageName.startsWith(PSPPlatform.PACKAGE_PREFIX)) {
            return new PSPPlatform();
        } else if (isVirtualRealityApp(app)) {
            return new VRPlatform();
        } else {
            return new AndroidPlatform();
        }
    }

    public static File pkg2path(Context context, String pkg) {
        File iconDir = new File(context.getFilesDir(), "icons");
        if (!iconDir.exists()) iconDir.mkdirs();

        File newFile = new File(iconDir, pkg + ".webp");

        File oldFile = new File(context.getCacheDir(), pkg + ".webp");
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.renameTo(newFile);
        }

        return newFile;
    }

    public static boolean updateIcon(ImageView icon, File file, String pkg) {
        try {
            Drawable drawable = Drawable.createFromPath(file.getAbsolutePath());
            if (drawable != null) {
                icon.setImageDrawable(drawable);
                iconCache.put(pkg, drawable);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    protected static boolean downloadIconFromUrl(String url, File outputFile) {
        try {
            return saveStream(new URL(url).openStream(), outputFile);
        } catch (Exception e) {
            return false;
        }
    }

    protected static boolean saveStream(InputStream is, File outputFile) {
        try {
            DataInputStream dis = new DataInputStream(is);

            int length;
            byte[] buffer = new byte[65536];
            FileOutputStream fos = new FileOutputStream(outputFile);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.flush();
            fos.close();

            if (!isImageFileComplete(outputFile)) {
                return false;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(outputFile.getAbsolutePath());
            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float aspectRatio = (float) width / height;
                if (width > 512) {
                    width = 512;
                    height = Math.round(width / aspectRatio);
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                }
                try {
                    fos = new FileOutputStream(outputFile);
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 75, fos);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMagicLeapHeadset() {
        String vendor = Build.MANUFACTURER.toUpperCase();
        return vendor.startsWith("MAGIC LEAP");
    }

    public static boolean isOculusHeadset() {
        String vendor = Build.MANUFACTURER.toUpperCase();
        return vendor.startsWith("META") || vendor.startsWith("OCULUS");
    }

    public static boolean isPicoHeadset() {
        String vendor = Build.MANUFACTURER.toUpperCase();
        return vendor.startsWith("PICO") || vendor.startsWith("PİCO");
    }

    public static boolean isPico3Pro() {
        if(isPicoHeadset()) {
            String osVersion = Build.DISPLAY;
            return osVersion.startsWith("4.");
        }
        return false;
    }

    public static boolean isVirtualRealityApp(ApplicationInfo app) {
        String[] nonVrApps = {      //move to tools category
                "com.pico4.settings",   //app that shows android settings
                "com.pico.browser",     //in-build pico web browser
                "com.ss.android.ttvr",  //pico video
                "com.pvr.mrc"           //pico's mixed reality capture
        };
        for (String nonVrApp : nonVrApps) {
            if (app.packageName.startsWith(nonVrApp)) {
                return false;
            }
        }
        if (app.metaData != null) {
            for (String key : app.metaData.keySet()) {
                if (key.startsWith("com.oculus")) {
                    return true;
                }
                if (key.startsWith("pvr.app.type")) {
                    return true;
                }
                if (key.startsWith("com.picovr.type")) {
                    return true;
                }
                if (key.contains("vr.application.mode")) {
                    return true;
                }
            }
        }
        return false;
    }
}
