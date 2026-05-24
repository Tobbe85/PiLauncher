package com.tobbe.pilauncher.tblauncher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tobbe.pilauncher.MainActivity;
import com.tobbe.pilauncher.R;
import com.tobbe.pilauncher.platforms.AbstractPlatform;
import com.tobbe.pilauncher.ui.AppsAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TBJKXR {

    private final Context context;
    private Dialog dialog;

    private static final String JKXR_DIR = "JKXR";
    private static final String COMMANDLINE_FILE = "commandline.txt";

    private static final String JKXR_PACKAGE = "com.drbeef.jkxr";
    private static final String JKXR_ACTIVITY = "com.drbeef.jkxr.GLES3JNIActivity";

    private enum GameConfig {
        OUTCAST(R.id.button_jkxr1, "JK2/base/assets0.pk3"),
        ACADEMY(R.id.button_jkxr2, "JK3/base/assets0.pk3");

        final int buttonId;
        final String gameDir;

        GameConfig(int buttonId, String gameDir) {
            this.buttonId = buttonId;
            this.gameDir = gameDir;
        }
    }

    public TBJKXR(Context context) {
        this.context = context;
    }

    public void show() {

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_jkxr);

        File folder = new File(Environment.getExternalStorageDirectory(), JKXR_DIR);
        if (!folder.exists() || (!folder.isDirectory())) {
            startJKXRActivity();
            return;
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );
        }

        int mainColor = Color.rgb(45, 45, 45);

        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(JKXR_PACKAGE, 0);
            String name = appInfo.loadLabel(pm).toString();

            ImageView tempImage = new ImageView(context);
            AbstractPlatform platform = AbstractPlatform.getPlatform(appInfo);
            platform.loadIcon((MainActivity) context, tempImage, appInfo, name);

            tempImage.measure(
                    View.MeasureSpec.makeMeasureSpec(253, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(253, View.MeasureSpec.EXACTLY)
            );
            tempImage.layout(0, 0, 253, 253);

            Bitmap bitmap = Bitmap.createBitmap(
                    tempImage.getWidth(),
                    tempImage.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(bitmap);
            tempImage.draw(canvas);

            mainColor = AppsAdapter.getDominantColor(bitmap);
            int darkColor = AppsAdapter.darkenColor(mainColor, 0.35f);

            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{mainColor, darkColor}
            );
            bg.setCornerRadius(48);

            View layout = dialog.findViewById(R.id.layout);
            if (layout != null) {
                layout.setBackground(bg);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        setupGameButtons(mainColor);

        dialog.show();

        View content = dialog.findViewById(R.id.layout);
        if (content != null && dialog.getWindow() != null) {
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );

            dialog.getWindow().setLayout(
                    content.getMeasuredWidth(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        View info = dialog.findViewById(R.id.info_jkxr);

        if (info != null) {
            info.setOnClickListener(v -> showHelpDialog());
        }
    }

    private void setupGameButtons(int mainColor) {
        for (GameConfig config : GameConfig.values()) {
            Button button = dialog.findViewById(config.buttonId);
            if (button == null) continue;

            styleButton(button, mainColor);

            if (isGameFolderAvailable(config.gameDir)) {
                setButtonAvailable(button);
                button.setOnClickListener(v -> launchGame(config));
            } else {
                setButtonUnavailable(button);
            }
        }
    }

    private void styleButton(Button button, int mainColor) {
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setCornerRadius(32);
        buttonBg.setColor(Color.argb(
                140,
                Color.red(mainColor),
                Color.green(mainColor),
                Color.blue(mainColor)
        ));
        buttonBg.setStroke(2, Color.argb(180, 255, 255, 255));
        button.setBackground(buttonBg);
    }

    private boolean isGameFolderAvailable(String gameDir) {
        File sdRoot = Environment.getExternalStorageDirectory();
        File gameDirFile = new File(new File(sdRoot, JKXR_DIR), gameDir);
        return gameDirFile.exists() && gameDirFile.isFile();
    }

    private void setButtonAvailable(Button button) {
        button.setEnabled(true);
        button.setClickable(true);
        button.setAlpha(1.0f);

        if (button.getBackground() != null) {
            button.getBackground().mutate().clearColorFilter();
        }

        button.setTextColor(Color.WHITE);
    }

    private void setButtonUnavailable(Button button) {
        button.setEnabled(false);
        button.setClickable(false);
        button.setAlpha(0.45f);

        if (button.getBackground() != null) {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);
            button.getBackground().mutate().setColorFilter(
                    new ColorMatrixColorFilter(matrix)
            );
        }

        button.setTextColor(Color.LTGRAY);
    }

    private void launchGame(GameConfig config) {

        String commandLine = buildCommandLine(config.buttonId);

        if (!writeCommandLineFile(commandLine)) {
            return;
        }

        if (dialog != null) {
            dialog.dismiss();
        }

        startJKXRActivity();
    }

    private String buildCommandLine(int buttonId) {

        if (buttonId == R.id.button_jkxr1) {
            return "jo";
        } else {
            return "ja";
        }
    }

    private boolean writeCommandLineFile(String commandLine) {
        FileOutputStream fos = null;

        try {
            File sdRoot = Environment.getExternalStorageDirectory();
            File jkxrDir = new File(sdRoot, JKXR_DIR);

            if (!jkxrDir.exists() || !jkxrDir.isDirectory()) {
                Toast.makeText(context, "/sdcard/JKXR not found.", Toast.LENGTH_LONG).show();
                return false;
            }

            File cmdFile = new File(jkxrDir, COMMANDLINE_FILE);
            fos = new FileOutputStream(cmdFile, false);
            fos.write(commandLine.getBytes(StandardCharsets.UTF_8));
            fos.flush();

            return true;

        } catch (IOException e) {
            Toast.makeText(context, "Error while writing: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;

        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void startJKXRActivity() {
        try {
            Intent intent = new Intent();
            intent.setClassName(JKXR_PACKAGE, JKXR_ACTIVITY);
            context.startActivity(intent);

        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "JKXR Activity not found.", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(context, "Start error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void showHelpDialog() {

        String helpText =
                        "/sdcard/JKXR/...\n\n" +
                        "Jedi Knight II  - Jedi Outcast  = JK2\n" +
                        "Jedi Knight III - Jedi Academy  = JK3\n";

        TextView textView = new TextView(context);

        textView.setText(helpText);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(16);
        textView.setPadding(40, 40, 40, 40);

        textView.setTypeface(Typeface.MONOSPACE);

        textView.setBackgroundColor(
                Color.parseColor("#2d2d2d")
        );

        ScrollView scrollView = new ScrollView(context);

        scrollView.setBackgroundColor(
                Color.parseColor("#2d2d2d")
        );

        scrollView.addView(textView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(scrollView)
                .setPositiveButton("OK", null)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(
                            Color.parseColor("#2d2d2d")
                    )
            );
        }
    }
}
