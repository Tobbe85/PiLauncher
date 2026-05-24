package com.tobbe.pilauncher.ui;

import static com.tobbe.pilauncher.MainActivity.DEFAULT_SCALE;
import static com.tobbe.pilauncher.MainActivity.DEFAULT_STYLE;
import static com.tobbe.pilauncher.MainActivity.STYLES;
import static com.tobbe.pilauncher.MainActivity.sharedPreferences;

import com.tobbe.pilauncher.stats.PerGameStatsDialog;
import com.tobbe.pilauncher.platforms.AbstractPlatform;

import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.Spannable;
import android.graphics.Color;
import android.provider.Settings;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import java.util.UUID;
import android.app.Dialog;
import android.widget.Button;
import android.webkit.WebView;
import android.net.Uri;
import android.content.pm.ResolveInfo;
import android.view.View;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.util.List;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.content.Intent;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;

import com.tobbe.pilauncher.ImageUtils;
import com.tobbe.pilauncher.MainActivity;
import com.tobbe.pilauncher.R;
import com.tobbe.pilauncher.SettingsProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class AppsAdapter extends BaseAdapter
{
    public static final Set<String> indieGamesSet = new HashSet<>();
    final int style = sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_STYLE, DEFAULT_STYLE);
    private static Drawable iconDrawable;
    private static File iconFile;
    private static String packageName;
    private static long lastClickTime;
    private final MainActivity mainActivityContext;
    private final List<ApplicationInfo> appList;
    private final boolean isEditMode;
    private final boolean showTextLabels;
    private final int itemScale;
    private final SettingsProvider settingsProvider;
    private static boolean needsreload = false;

    public enum SORT_FIELD { APP_NAME, RECENT_DATE, INSTALL_DATE }
    public enum SORT_ORDER { ASCENDING, DESCENDING }

    public static boolean fetch_indie = false;

    public AppsAdapter(MainActivity context, boolean editMode, int scale, boolean names)
    {
        mainActivityContext = context;
        isEditMode = editMode;
        showTextLabels = names;
        itemScale = scale;
        settingsProvider = SettingsProvider.getInstance(mainActivityContext);

        ArrayList<String> sortedGroups = settingsProvider.getAppGroupsSorted(false);
        ArrayList<String> sortedSelectedGroups = settingsProvider.getAppGroupsSorted(true);
        boolean isFirstGroupSelected = !sortedSelectedGroups.isEmpty() && !sortedGroups.isEmpty() && sortedSelectedGroups.get(0).compareTo(sortedGroups.get(0)) == 0;
        appList = settingsProvider.getInstalledApps(context, sortedSelectedGroups, isFirstGroupSelected);
        sharedPreferences = mainActivityContext.getSharedPreferences(mainActivityContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        SORT_FIELD sortField = SORT_FIELD.values()[sharedPreferences.getInt(SettingsProvider.KEY_SORT_FIELD, 0)];
        SORT_ORDER sortOrder = SORT_ORDER.values()[sharedPreferences.getInt(SettingsProvider.KEY_SORT_ORDER, 0)];
        this.sort(sortField, sortOrder);
    }

    private static class ViewHolder {
        LinearLayout layout;
        ImageView imageView;
        TextView textView;
        ImageView progressBar;
    }

    public int getCount()
    {
        return appList.size();
    }

    public Object getItem(int position)
    {
        return appList.get(position);
    }

    public long getItemId(int position)
    {
        return position;
    }

    private final Handler handler = new Handler();
    @SuppressLint("NewApi")
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder holder;

        boolean integrated_Launcher = sharedPreferences.getBoolean(
                SettingsProvider.KEY_INTEGRATED_LAUNCHER,
                MainActivity.DEFAULT_INTEGRATED_LAUNCHER
        );

        final ApplicationInfo currentApp = appList.get(position);
        LayoutInflater inflater = (LayoutInflater) mainActivityContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            // Create a new ViewHolder and inflate the layout
            convertView = inflater.inflate(R.layout.lv_app, parent, false);
            holder = new ViewHolder();
            holder.layout = convertView.findViewById(R.id.layout);
            holder.imageView = convertView.findViewById(R.id.imageLabel);
            holder.textView = convertView.findViewById(R.id.textLabel);
            holder.progressBar = convertView.findViewById(R.id.progress_bar);
            convertView.setTag(holder);

            // Set size of items
            int kScale = sharedPreferences.getInt(SettingsProvider.KEY_CUSTOM_SCALE, DEFAULT_SCALE) + 1;
            float textSize = holder.textView.getTextSize();
            float textSizeScaled = Math.max(10, textSize / 5 * kScale);
            holder.textView.setTextSize(textSizeScaled);

            //Calculate text height
            holder.textView.measure(0, 0);
            int textHeight = (int) holder.textView.getMeasuredHeight();

            ViewGroup.LayoutParams params = holder.layout.getLayoutParams();

            params.width = itemScale;
            if (style == 0) {
                if(showTextLabels) {
                    params.height = (int) ((itemScale) * 0.5625) + textHeight;
                }else{
                    params.height = (int) ((itemScale) * 0.5625);
                }
            } else {
                if(showTextLabels) {
                    params.height = (int) (itemScale + textHeight);
                }else{
                    params.height = (int) itemScale;
                }
            }
            holder.layout.setLayoutParams(params);
        } else {
            // ViewHolder already exists, reuse it
            holder = (ViewHolder) convertView.getTag();
        }

        // set value into textview
        PackageManager pm = mainActivityContext.getPackageManager();
        String name = SettingsProvider.getAppDisplayName(mainActivityContext, currentApp.packageName, currentApp.loadLabel(pm));
        holder.textView.setText(name);
        holder.textView.setVisibility(showTextLabels ? View.VISIBLE : View.GONE);

        if (isEditMode) {
            // short click for app details, long click to activate drag and drop
            holder.layout.setOnTouchListener((view, motionEvent) -> {
                if ((motionEvent.getAction() == MotionEvent.ACTION_DOWN) ||
                        (motionEvent.getAction() == MotionEvent.ACTION_POINTER_DOWN)) {
                    packageName = currentApp.packageName;
                    lastClickTime = System.currentTimeMillis();
                    ClipData dragData = ClipData.newPlainText(name, name);
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        view.startDragAndDrop(dragData, shadowBuilder, view, 0);
                    } else {
                        view.startDrag(dragData, shadowBuilder, view, 0);
                    }
                }
                return false;
            });

            // drag and drop
            holder.layout.setOnDragListener((view, event) -> {
                if (currentApp.packageName.compareTo(packageName) == 0) {
                    if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
                        view.setVisibility(View.INVISIBLE);
                    } else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                        mainActivityContext.reloadUI();
                    } else if (event.getAction() == DragEvent.ACTION_DROP) {
                        if (System.currentTimeMillis() - lastClickTime < 250) {
                            showAppDetails(currentApp);
                        } else {
                            mainActivityContext.reloadUI();
                        }
                    }
                    return event.getAction() != DragEvent.ACTION_DROP;
                }
                return true;
            });
        } else {
            holder.layout.setOnClickListener(view -> {

                if ("com.drbeef.lambda1vr".equals(currentApp.packageName) && integrated_Launcher)  {

                    new com.tobbe.pilauncher.tblauncher.TBLambda1VR(mainActivityContext).show();
                    return;
                }
                if ("com.drbeef.jkxr".equals(currentApp.packageName) && integrated_Launcher) {

                    new com.tobbe.pilauncher.tblauncher.TBJKXR(mainActivityContext).show();
                    return;
                }
                if ("com.drbeef.razexr".equals(currentApp.packageName) && integrated_Launcher) {

                    new com.tobbe.pilauncher.tblauncher.TBRazeXR(mainActivityContext).show();
                    return;
                }
                if ("com.drbeef.questzdoom".equals(currentApp.packageName) && integrated_Launcher) {

                    new com.tobbe.pilauncher.tblauncher.TBQuestZDoom(mainActivityContext).show();
                    return;
                }
                view.post(() -> {

                    holder.layout.animate()
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .setDuration(100)
                            .withEndAction(() -> {

                                holder.layout.animate()
                                        .scaleX(1.4f)
                                        .scaleY(1.4f)
                                        .alpha(0f)
                                        .setDuration(220)
                                        .withEndAction(() -> {

                                            if (!AbstractPlatform.isVirtualRealityApp(currentApp)) {
                                                holder.layout.setScaleX(1f);
                                                holder.layout.setScaleY(1f);
                                                holder.layout.setAlpha(1f);
                                                mainActivityContext.openApp(currentApp);
                                                return;
                                            }

                                            View fade = mainActivityContext.findViewById(R.id.fade_overlay);

                                            fade.animate()
                                                    .alpha(1f)
                                                    .setDuration(180)
                                                    .withEndAction(() -> {

                                                        boolean opened = mainActivityContext.openApp(currentApp);

                                                        if (opened) {
                                                            fade.postDelayed(() -> {
                                                                fade.animate().cancel();
                                                                fade.setAlpha(0f);

                                                                holder.layout.animate().cancel();
                                                                holder.layout.setScaleX(1f);
                                                                holder.layout.setScaleY(1f);
                                                                holder.layout.setAlpha(1f);
                                                            }, 0);
                                                        } else {
                                                            fade.animate()
                                                                    .alpha(0f)
                                                                    .setDuration(200)
                                                                    .start();

                                                            holder.layout.setScaleX(1f);
                                                            holder.layout.setScaleY(1f);
                                                            holder.layout.setAlpha(1f);
                                                        }
                                                    })
                                                    .start();
                                        })
                                        .start();
                            })
                            .start();
                });
            });

            holder.layout.setOnLongClickListener(view -> {
                showAppDetails(currentApp);
                return false;
            });
        }

        // set application icon
        AbstractPlatform platform = AbstractPlatform.getPlatform(currentApp);
        try {
            platform.loadIcon(mainActivityContext, holder.imageView, currentApp, name);
        } catch (Resources.NotFoundException e) {
            Log.e("loadIcon", "Error loading icon for app: " + currentApp.packageName, e);
        }
        return convertView;
    }

    public void onImageSelected(String path, ImageView selectedImageView) {
        AbstractPlatform.clearIconCache();
        if (path != null) {
            Bitmap bitmap = ImageUtils.getResizedBitmap(BitmapFactory.decodeFile(path), 512);
            bitmap = Bitmap.createScaledBitmap(bitmap, 450, 253, true);
            if (style == 1) {
                bitmap = Bitmap.createScaledBitmap(bitmap, 253, 253, true);
                bitmap = AbstractPlatform.applyRoundedCorners(bitmap, 24);
            }
            if (style == 2) {
                bitmap = Bitmap.createScaledBitmap(bitmap, 253, 253, true);
                bitmap = AbstractPlatform.makeRounded(bitmap);
            }
            ImageUtils.saveBitmap(bitmap, iconFile);
            selectedImageView.setImageBitmap(bitmap);
        } else {
            selectedImageView.setImageDrawable(iconDrawable);
            AbstractPlatform.updateIcon(selectedImageView, iconFile, STYLES[style]+"."+ packageName);
        }
        mainActivityContext.reloadUI();
        this.notifyDataSetChanged(); // for real time updates
    }

    private Long getInstallDate(ApplicationInfo applicationInfo) {
        if(SettingsProvider.installDates.containsKey(applicationInfo.packageName)) {
            return SettingsProvider.installDates.get(applicationInfo.packageName);
        }else{
            return 0L;
        }
    }

    public void sort(SORT_FIELD field, SORT_ORDER order) {
        final PackageManager pm = mainActivityContext.getPackageManager();
        final Map<String, Long> recents = settingsProvider.getRecents();

        Collections.sort(appList, (a, b) -> {
            String na;
            String nb;
            long naL;
            long nbL;
            int result;
            switch (field) {
                case RECENT_DATE:
                    if (recents.containsKey(a.packageName)) {
                        naL = recents.get(a.packageName);
                    } else {
                        naL = getInstallDate(a);
                    }
                    if (recents.containsKey(b.packageName)) {
                        nbL = recents.get(b.packageName);
                    } else {
                        nbL = getInstallDate(b);
                    }
                    result = Long.compare(naL, nbL);
                    break;

                case INSTALL_DATE:
                    naL = getInstallDate(a);
                    nbL = getInstallDate(b);
                    result = Long.compare(naL, nbL);
                    break;

                default: //by APP_NAME
                    na = SettingsProvider.getAppDisplayName(mainActivityContext, a.packageName, a.loadLabel(pm)).toUpperCase();
                    nb = SettingsProvider.getAppDisplayName(mainActivityContext, b.packageName, b.loadLabel(pm)).toUpperCase();
                    result = na.compareTo(nb);
                    break;
            }

            return order == SORT_ORDER.ASCENDING ? result : -result;
        });
        this.notifyDataSetChanged();
    }

    private void showAppSize(Context context, String packageName, TextView sizeText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                StorageStatsManager storageStatsManager = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
                StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                PackageManager pm = context.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                UUID storageUuid = storageManager.getUuidForPath(new File(appInfo.dataDir));

                StorageStats stats = storageStatsManager.queryStatsForPackage(storageUuid, packageName, UserHandle.getUserHandleForUid(appInfo.uid));

                long totalBytes = stats.getAppBytes() + stats.getDataBytes() + stats.getCacheBytes();

                String sizeReadable = android.text.format.Formatter.formatFileSize(context, totalBytes);
                sizeText.setText("\uD83D\uDCBE  " + sizeReadable);
            } catch (PackageManager.NameNotFoundException e) {
                sizeText.setText("\uD83D\uDCBE  ?");
                e.printStackTrace();
            } catch (Exception e) {
                String permissionText = context.getString(R.string.request_permission);
                sizeText.setText(
                        new SpannableStringBuilder("\uD83D\uDCBE  ")
                                .append(permissionText, new ForegroundColorSpan(Color.RED), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                );
                e.printStackTrace();
                sizeText.setOnClickListener(v -> {
                    requestUsageStatsPermissionIfNeeded(mainActivityContext);
                });
            }
        } else {
            sizeText.setText("\uD83D\uDCBE  n.A.");
        }
    }

    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = AppOpsManager.MODE_DEFAULT;

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
    }

    // Request USER STATS Permission
    public static void requestUsageStatsPermissionIfNeeded(Context context) {
        if (!hasUsageStatsPermission(context)) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void fetchIndieGameList() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/Tobbe85/PiLauncher/main/indiegames");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                Set<String> result = new HashSet<>();
                while ((line = reader.readLine()) != null) {
                    result.add(line.trim());
                }
                reader.close();
                connection.disconnect();

                fetch_indie = true;

                handler.post(() -> {
                    indieGamesSet.clear();
                    indieGamesSet.addAll(result);
                });
            } catch (Exception e) {
                fetch_indie = false;
            }
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void showAppDetails(ApplicationInfo actApp) {
        // Layout setzen
        needsreload = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(mainActivityContext);
        builder.setView(R.layout.dialog_app_details);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bkg_dialog);
        dialog.show();
        View content = dialog.findViewById(R.id.layout);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int desiredWidth = content.getMeasuredWidth();
        dialog.getWindow().setLayout(
                desiredWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        // Info-Button öffnet App-Details-Einstellungen
        dialog.findViewById(R.id.info).setOnClickListener(view13 ->
                mainActivityContext.openAppDetails(actApp.packageName)
        );

        View statsButton = dialog.findViewById(R.id.stats);

        statsButton.setVisibility(
                PerGameStatsDialog.shouldShowStatsButton(mainActivityContext)
                        ? View.VISIBLE
                        : View.GONE
        );

        statsButton.setOnClickListener(v -> {
            new PerGameStatsDialog(mainActivityContext, actApp.packageName, () -> {
                statsButton.setVisibility(
                        PerGameStatsDialog.shouldShowStatsButton(mainActivityContext)
                                ? View.VISIBLE
                                : View.GONE
                );
            }).show();
        });

        if (AbstractPlatform.isPicoHeadset() && AbstractPlatform.isPicoUltra() && AbstractPlatform.isVirtualRealityApp(actApp)) {
            dialog.findViewById(R.id.perf).setOnClickListener(v -> {
                mainActivityContext.openPerformancetool();
            });
        } else {
            dialog.findViewById(R.id.perf).setVisibility(View.GONE);
        }
        dialog.findViewById(R.id.info_text).setOnClickListener(view13 -> {
            String packageName = actApp.packageName;
            String store = "us";

            if (indieGamesSet.contains(packageName)) {
                if (packageName.equals("com.Baggyg.QuestZDoom_Launcher")) {
                    packageName = "com.drbeef.questzdoom";
                }
                if (packageName.equals("com.BaggyG.JKXR_Companion_App")) {
                    packageName = "com.drbeef.jkxr";
                }
                if (packageName.equals("com.CactusStudios.Lambda1VR_Launcher") || packageName.equals("com.tobbe.lambda1vr_launcher")) {
                    packageName = "com.drbeef.lambda1vr";
                }
                if (packageName.equals("com.BaggyG.RazeXR_Launcher")) {
                    packageName = "com.drbeef.razexr";
                }
                store = "zz";
            }

            if (!store.equals("zz")) {
                String[] requiredLibs = {
                        "libovrplatformloader.so"
                };

                String[] picoLibs = {
                        "libPxrPlatform.so",
                        "libPvr_UnitySDK.so"
                };

                boolean allLibsExist = true;

                for (String libName : requiredLibs) {
                    File libFile = new File(actApp.nativeLibraryDir, libName);
                    if (!libFile.exists()) {
                        allLibsExist = false;
                        break;
                    }
                }

                for (String picoLib : picoLibs) {
                    File picoFile = new File(actApp.nativeLibraryDir, picoLib);
                    if (picoFile.exists()) {
                        allLibsExist = false;
                        break;
                    }
                }

                File ovrPlugin = new File(actApp.nativeLibraryDir, "libOVRPlugin.so");

                boolean hasOculusVrCategory = false;
                try {
                    PackageManager pm = mainActivityContext.getPackageManager();

                    Intent vrIntent = new Intent(Intent.ACTION_MAIN);
                    vrIntent.addCategory("com.oculus.intent.category.VR");
                    vrIntent.setPackage(actApp.packageName);

                    List<ResolveInfo> matches = pm.queryIntentActivities(vrIntent, 0);
                    hasOculusVrCategory = matches != null && !matches.isEmpty();
                } catch (Exception ignored) {
                }

                if (!ovrPlugin.exists() && !hasOculusVrCategory) {
                    allLibsExist = false;
                }

                if (allLibsExist) {
                    store = "ov";
                }
            }

            String url = "https://ppdata.uk/?pkg=" + packageName + "&store=" + store;

            // ----------- NEU: eigenes Fenster (Dialog) statt Browser -----------

            Dialog webDialog = new Dialog(mainActivityContext, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            webDialog.setContentView(R.layout.dialog_webview);

            WebView web = webDialog.findViewById(R.id.web);
            web.getSettings().setJavaScriptEnabled(true);
            web.loadUrl(url);

            webDialog.show();

            Button close = webDialog.findViewById(R.id.close_button);
            close.setOnClickListener(v -> webDialog.dismiss());
        });

        // Delete-Button löscht App
        dialog.findViewById(R.id.delete).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + actApp.packageName));
            mainActivityContext.startActivity(intent);
            dialog.dismiss();
            mainActivityContext.reloadUI();
        });

        // Name setzen
        PackageManager pm = mainActivityContext.getPackageManager();
        String name = SettingsProvider.getAppDisplayName(mainActivityContext, actApp.packageName, actApp.loadLabel(pm));
        final EditText input = dialog.findViewById(R.id.app_name);
        input.setText(name);
        input.clearFocus();
        input.setOnClickListener(v -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) mainActivityContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            needsreload = true;
        });

        dialog.findViewById(R.id.ok).setOnClickListener(view12 -> {
            settingsProvider.setAppDisplayName(actApp, input.getText().toString());
            dialog.dismiss();
            if (needsreload) {
                mainActivityContext.reloadUI();
            }
        });

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        // Icon laden
        ImageView tempImage = dialog.findViewById(R.id.app_icon);
        AbstractPlatform platform = AbstractPlatform.getPlatform(actApp);
        platform.loadIcon(mainActivityContext, tempImage, actApp, name);

        tempImage.post(() -> {
            tempImage.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(tempImage.getDrawingCache());
            tempImage.setDrawingCacheEnabled(false);

            if (bitmap != null) {
                applyGameColorBackground(dialog, bitmap);
            }
        });

        tempImage.setOnClickListener(view1 -> {
            iconDrawable = actApp.loadIcon(pm);
            packageName = actApp.packageName;
            iconFile = AbstractPlatform.pkg2path(mainActivityContext, STYLES[style] + "." + actApp.packageName);
            if (iconFile.exists()) {
                iconFile.delete();
            }
            mainActivityContext.setSelectedImageView(tempImage);
            ImageUtils.showImagePicker(mainActivityContext, MainActivity.PICK_ICON_CODE);
        });

        // Package-Name anzeigen
        TextView packageText = dialog.findViewById(R.id.package_show);
        if (packageText != null) {
            packageText.setText("\uD83D\uDCE6  " + actApp.packageName);
        }

        //App Größe anzeigen
        TextView sizeText = dialog.findViewById(R.id.size_show);
        showAppSize(mainActivityContext, actApp.packageName, sizeText);
        // show app version
        String[] enginelibs = {
                "libUE4.so",
                "libUnreal.so",
                "libil2cpp.so",
                "libunity.so",
                "libxenko.so",
                "libStrideNative.so",
                "libcocos2dcpp.so",
                "libcocos2djs.so",
                "libCryEngine.so",
                "libCrySystem.so",
                "libgodot_android.so",
                "libgodot.so",
                "libgodot_android_template.so"

        };

        TextView versionText = dialog.findViewById(R.id.version_show);
        TextView engineText = dialog.findViewById(R.id.engine_show);
        String enginevar = "Unknown Engine";

        for (String libName : enginelibs) {
            File libFile = new File(actApp.nativeLibraryDir, libName);
            if (libFile.exists()) {
                if (libName.equals("libUE4.so")) {
                    enginevar = "Unreal Engine 4";
                    break;
                } else if (libName.equals("libUnreal.so")) {
                    enginevar = "Unreal Engine 5";
                    break;
                } else if (libName.equals("libil2cpp.so") || libName.equals("libunity.so")) {
                    enginevar = "Unity Engine";
                    break;
                } else if (libName.equals("libxenko.so") || libName.equals("libStrideNative.so")) {
                    enginevar = "Stride Engine";
                    break;
                } else if (libName.equals("libcocos2dcpp.so") || libName.equals("libcocos2djs.so")) {
                    enginevar = "Cocos2d-x Engine";
                    break;
                } else if (libName.equals("libCryEngine.so") || libName.equals("libCrySystem.so")) {
                    enginevar = "Cry Engine";
                    break;
                } else if (libName.equals("libgodot_android.so") || libName.equals("libgodot.so")) {
                    enginevar = "Godot Engine";
                    break;
                } else if (libName.equals("libgodot_android_template.so")) {
                    enginevar = "Godot 4 Engine";
                    break;
                }
            }
        }

        if (versionText != null) {
            try {
                android.content.pm.PackageInfo packageInfo =
                        pm.getPackageInfo(actApp.packageName, 0);

                String versionName = packageInfo.versionName;

                long versionCode;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    versionCode = packageInfo.getLongVersionCode();
                } else {
                    versionCode = packageInfo.versionCode;
                }

                String updateTime = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                        .format(new Date(packageInfo.lastUpdateTime));

                versionText.setText("\uD83C\uDD9A   " + versionName + " (" + versionCode + ")  •  🗓️ " + updateTime);

            } catch (PackageManager.NameNotFoundException e) {
                versionText.setText("\uD83C\uDD9A   ?");
            }
        }
        if (engineText != null) {
            engineText.setText("\uD83D\uDE80  " + enginevar);
        }
    }
    public static int getDominantColor(Bitmap bitmap) {
        Bitmap small = Bitmap.createScaledBitmap(bitmap, 32, 32, true);

        long r = 0, g = 0, b = 0;
        int count = 0;

        for (int x = 0; x < small.getWidth(); x++) {
            for (int y = 0; y < small.getHeight(); y++) {
                int color = small.getPixel(x, y);
                if (Color.alpha(color) < 128) continue;

                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
                count++;
            }
        }

        if (count == 0) return Color.rgb(45, 45, 45);

        return Color.rgb(
                (int) (r / count),
                (int) (g / count),
                (int) (b / count)
        );
    }

    public static int darkenColor(int color, float factor) {
        return Color.rgb(
                Math.max(0, (int) (Color.red(color) * factor)),
                Math.max(0, (int) (Color.green(color) * factor)),
                Math.max(0, (int) (Color.blue(color) * factor))
        );
    }

    private void applyGameColorBackground(AlertDialog dialog, Bitmap bitmap) {
        int mainColor = getDominantColor(bitmap);
        int darkColor = darkenColor(mainColor, 0.35f);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{mainColor, darkColor}
        );

        bg.setCornerRadius(dpToPx(24));

        View layout = dialog.findViewById(R.id.layout);
        if (layout != null) {
            layout.setBackground(bg);
        }

        tintDialogButton(dialog, R.id.info_text, mainColor);
        tintDialogButton(dialog, R.id.ok, mainColor);
    }

    private void tintDialogButton(AlertDialog dialog, int viewId, int baseColor) {
        View button = dialog.findViewById(viewId);
        if (button == null) return;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(darkenColor(baseColor, 0.55f));
        bg.setCornerRadius(dpToPx(45));
        bg.setStroke(dpToPx(1), Color.WHITE);

        button.setBackground(bg);

        if (button instanceof TextView) {
            ((TextView) button).setTextColor(Color.WHITE);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                mainActivityContext.getResources().getDisplayMetrics()
        );
    }

    public String getSelectedPackage() {
        return packageName;
    }
}
