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

public class TBRazeXR {

    private final Context context;
    private Dialog dialog;

    private static final String RAZE_PACKAGE = "com.drbeef.razexr";
    private static final String RAZE_ACTIVITY = "com.drbeef.razexr.GLES3JNIActivity";

    private static final String RAZE_DIR = "RazeXR/raze";
    private static final String COMMANDLINE_FILE = "commandline.txt";

    private final int[] buttonIds = {
            R.id.button_razexr1,
            R.id.button_razexr2,
            R.id.button_razexr3,
            R.id.button_razexr4,
            R.id.button_razexr5,
            R.id.button_razexr6,
            R.id.button_razexr7
    };

    private enum MainGame {
        DUKE,
        BLOOD,
        EXHUMED,
        NAM,
        REDNECK,
        SHADOW_WARRIOR,
        WW2GI
    }

    private static class GameEntry {

        final String title;
        final String folder;
        final String commandLine;
        final String[] requiredFiles;

        GameEntry(
                String title,
                String folder,
                String commandLine,
                String... requiredFiles
        ) {
            this.title = title;
            this.folder = folder;
            this.commandLine = commandLine;
            this.requiredFiles = requiredFiles;
        }
    }

    public TBRazeXR(Context context) {
        this.context = context;
    }

    public void show() {

        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_razexr);

        File folder = new File(Environment.getExternalStorageDirectory(), RAZE_DIR);
        if (!folder.exists() || (!folder.isDirectory())) {
            startRazeXR();
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

            ApplicationInfo appInfo =
                    pm.getApplicationInfo(RAZE_PACKAGE, 0);

            String name =
                    appInfo.loadLabel(pm).toString();

            ImageView tempImage = new ImageView(context);

            AbstractPlatform platform =
                    AbstractPlatform.getPlatform(appInfo);

            platform.loadIcon(
                    (MainActivity) context,
                    tempImage,
                    appInfo,
                    name
            );

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

            int darkColor =
                    AppsAdapter.darkenColor(mainColor, 0.35f);

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

        setupMainButtons(mainColor);

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
    }

    private void setupMainButtons(int mainColor) {
        View info = dialog.findViewById(R.id.info_razexr);
        View back = dialog.findViewById(R.id.back_button);

        if (info != null) info.setVisibility(View.GONE);
        if (back != null) back.setVisibility(View.GONE);

        setTitle("RazeXR");

        setButtonText(0, "Duke Nukem");
        setButtonText(1, "Blood");
        setButtonText(2, "Exhumed");
        setButtonText(3, "NAM");
        setButtonText(4, "Redneck Rampage");
        setButtonText(5, "Shadow Warrior");
        setButtonText(6, "WW2GI");

        for (int i = 0; i < buttonIds.length; i++) {

            Button button = dialog.findViewById(buttonIds[i]);
            if (button == null) continue;

            button.setVisibility(View.VISIBLE);

            styleButton(button, mainColor);

            setButtonAvailable(button);

            int finalI = i;

            button.setOnClickListener(v -> {
                switch (finalI) {
                    case 0:
                        showGames(MainGame.DUKE, mainColor);
                        break;
                    case 1:
                        showGames(MainGame.BLOOD, mainColor);
                        break;
                    case 2:
                        showGames(MainGame.EXHUMED, mainColor);
                        break;
                    case 3:
                        showGames(MainGame.NAM, mainColor);
                        break;
                    case 4:
                        showGames(MainGame.REDNECK, mainColor);
                        break;
                    case 5:
                        showGames(MainGame.SHADOW_WARRIOR, mainColor);
                        break;
                    case 6:
                        showGames(MainGame.WW2GI, mainColor);
                        break;
                }
            });
        }
    }

    private void showGames(MainGame game, int mainColor) {

        View info = dialog.findViewById(R.id.info_razexr);
        View back = dialog.findViewById(R.id.back_button);

        if (info != null) {
            info.setVisibility(View.VISIBLE);
            info.setOnClickListener(v -> showHelpDialog(getHelpText(game)));
        }

        if (back != null) {

            back.setVisibility(View.VISIBLE);

            back.setOnClickListener(v -> {

                setTitle("RazeXR");

                if (info != null) {
                    info.setVisibility(View.GONE);
                }

                back.setVisibility(View.GONE);

                setupMainButtons(mainColor);
            });
        }

        setTitle(getMainGameTitle(game));

        GameEntry[] games = getGames(game);

        int startIndex = 0;

        if (game == MainGame.WW2GI) {
            startIndex = 1;
        }

        for (int i = 0; i < buttonIds.length; i++) {

            Button button = dialog.findViewById(buttonIds[i]);

            if (button == null) continue;

            int gameIndex = i - startIndex;

            if (gameIndex < 0 || gameIndex >= games.length) {
                button.setVisibility(View.INVISIBLE);
                continue;
            }

            GameEntry entry = games[gameIndex];

            button.setVisibility(View.VISIBLE);
            button.setText(entry.title);

            styleButton(button, mainColor);

            if (isGameAvailable(entry)) {

                setButtonAvailable(button);

                button.setOnClickListener(v -> launchGame(entry));

            } else {

                setButtonUnavailable(button);
            }
        }
    }

    private void setTitle(String title) {

        TextView titleView = dialog.findViewById(R.id.razexr_title);

        if (titleView != null) {
            titleView.setText(title);
        }
    }

    private String getMainGameTitle(MainGame game) {

        switch (game) {

            case DUKE:
                return "Duke Nukem";

            case BLOOD:
                return "Blood";

            case EXHUMED:
                return "Exhumed";

            case NAM:
                return "NAM";

            case REDNECK:
                return "Redneck Rampage";

            case SHADOW_WARRIOR:
                return "Shadow Warrior";

            case WW2GI:
                return "WW2GI";
        }

        return "RazeXR";
    }

    private GameEntry[] getGames(MainGame game) {

        switch (game) {

            case DUKE:
                return new GameEntry[]{

                        new GameEntry(
                                "Duke Nukem 3D",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP",
                                "DUKE3D.GRP"
                        ),

                        new GameEntry(
                                "Duke Zone II",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP -file raze/duke/dukezone2.grp",
                                "DUKE3D.GRP",
                                "dukezone2.grp"
                        ),

                        new GameEntry(
                                "Duke it Out in D.C.",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP -file raze/duke/DUKEDC.GRP",
                                "DUKEDC.GRP",
                                "DUKE3D.GRP"
                        ),

                        new GameEntry(
                                "Duke Caribbean: Lifes a Beach",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP -file raze/duke/VACATION.GRP",
                                "VACATION.GRP",
                                "DUKE3D.GRP"
                        ),

                        new GameEntry(
                                "Duke - Nuclear Winter",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP -con NWINTER.CON -file raze/duke/NWINTER.GRP",
                                "DUKE3D.GRP",
                                "NWINTER.GRP"
                        ),

                        new GameEntry(
                                "Duke Nukem Penthouse Paradise",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP -con ppakgame.con -file raze/duke/PENTHOUS.GRP",
                                "DUKE3D.GRP",
                                "PENTHOUS.GRP",
                                "ppakgame.con"
                        ),

                        new GameEntry(
                                "Zombie Crises",
                                "duke",
                                "raze -gamegrp raze/duke/DUKE3D.GRP -file raze/duke/ZombieCrises.GRP",
                                "DUKE3D.GRP",
                                "ZombieCrises.GRP"
                        )
                };

            case BLOOD:
                return new GameEntry[]{

                        new GameEntry(
                                "Blood",
                                "blood",
                                "raze -gamegrp raze/blood/BLOOD.RFF",
                                "BLOOD.INI",
                                "BLOOD.RFF",
                                "GUI.RFF",
                                "SOUNDS.RFF",
                                "SURFACE.DAT",
                                "TILES000.ART",
                                "TILES017.ART",
                                "VOXEL.DAT"
                        ),

                        new GameEntry(
                                "Blood - Cryptic Passage",
                                "blood",
                                "raze -gamegrp raze/blood/BLOOD.RFF -cryptic -file raze/blood/CRYPTIC.ZIP",
                                "BLOOD.RFF",
                                "CRYPTIC.ZIP"
                        ),

                        new GameEntry(
                                "Marrow",
                                "blood",
                                "raze -gamegrp raze/blood/BLOOD.RFF -file raze/blood/marrow.zip",
                                "BLOOD.RFF",
                                "marrow.zip"
                        ),

                        new GameEntry(
                                "Fate of the Damned",
                                "blood",
                                "raze -gamegrp raze/blood/BLOOD.RFF -file raze/blood/FATE.zip -FILE RAZE/BLOOD/FATE.zip",
                                "BLOOD.RFF",
                                "FATE.zip"
                        ),

                        new GameEntry(
                                "Death Wish",
                                "blood",
                                "raze -gamegrp raze/blood/BLOOD.RFF -file raze/blood/deathwish.zip -file raze/blood/dw_music.zip",
                                "BLOOD.RFF",
                                "deathwish.zip",
                                "dw_music.zip"
                        )
                };

            case EXHUMED:
                return new GameEntry[]{

                        new GameEntry(
                                "Exhumed-Powerslave",
                                "exhumed",
                                "raze -gamegrp raze/exhumed/STUFF.DAT",
                                "STUFF.DAT",
                                "BOOK.MOV"
                        )
                };

            case NAM:
                return new GameEntry[]{

                        new GameEntry(
                                "NAM",
                                "nam",
                                "raze -gamegrp raze/nam/NAM.GRP",
                                "NAM.GRP",
                                "GAME.CON"
                        )
                };

            case REDNECK:
                return new GameEntry[]{

                        new GameEntry(
                                "Redneck Rampage",
                                "rampage",
                                "raze -gamegrp raze/rampage/REDNECK.GRP",
                                "REDNECK.GRP"
                        ),

                        new GameEntry(
                                "Suckin Grits on Route 66",
                                "rampage",
                                "raze -gamegrp raze/rampage/REDNECK.GRP -route66 -file raze/rampage/ROUTE66.zip",
                                "REDNECK.GRP",
                                "ROUTE66.zip"
                        ),

                        new GameEntry(
                                "Redneck Rampage Rides Again",
                                "ridesagain",
                                "raze -gamegrp raze/ridesagain/RIDES.GRP",
                                "RIDES.GRP"
                        ),

                        new GameEntry(
                                "Night of the Living Dead",
                                "rampage",
                                "raze -gamegrp raze/rampage/REDNECK.GRP -con DEADGAME.con -file raze/rampage/Dead.grp",
                                "REDNECK.GRP",
                                "Dead.grp",
                                "Deadgame.con"
                        ),

                        new GameEntry(
                                "Redneck Rampage Rides Again : Uranus Attcks",
                                "ridesagain",
                                "raze -gamegrp raze/ridesagain/RIDES.GRP -con URANUS.CON -file raze/ridesagain/URANUS.GRP",
                                "RIDES.GRP",
                                "URANUS.GRP",
                                "URANUS.CON"
                        )
                };

            case SHADOW_WARRIOR:
                return new GameEntry[]{

                        new GameEntry(
                                "Shadow Warrior",
                                "shadowwarrior",
                                "raze -gamegrp raze/shadowwarrior/SW.GRP",
                                "SW.GRP"
                        ),

                        new GameEntry(
                                "Twin Dragon",
                                "shadowwarrior",
                                "raze -gamegrp raze/shadowwarrior/SW.GRP -file raze/shadowwarrior/TD.GRP",
                                "SW.GRP",
                                "TD.GRP"
                        ),

                        new GameEntry(
                                "Wanton Destruction",
                                "shadowwarrior",
                                "raze -gamegrp raze/shadowwarrior/SW.GRP -file raze/shadowwarrior/WT.GRP",
                                "SW.GRP",
                                "WT.GRP"
                        )
                };

            case WW2GI:
                return new GameEntry[]{

                        new GameEntry(
                                "World War II GI",
                                "ww2gi",
                                "raze -gamegrp raze/ww2gi/WW2GI.GRP",
                                "WW2GI.GRP"
                        ),

                        new GameEntry(
                                "Platoon Leader",
                                "ww2gi",
                                "raze -gamegrp raze/ww2gi/WW2GI.GRP -con PLATOONL.DEF -file raze/ww2gi/PLATOONL.DAT",
                                "WW2GI.GRP",
                                "PLATOONL.DAT",
                                "PLATOONL.DEF"
                        )
                };
        }

        return new GameEntry[0];
    }

    private boolean isGameAvailable(GameEntry entry) {

        File folder = new File(
                Environment.getExternalStorageDirectory(),
                RAZE_DIR + "/" + entry.folder
        );

        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        File[] files = folder.listFiles();

        if (files == null) {
            return false;
        }

        for (String requiredFile : entry.requiredFiles) {

            boolean found = false;

            for (File file : files) {

                if (file.getName().equalsIgnoreCase(requiredFile)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    private void launchGame(GameEntry entry) {

        if (!writeCommandLineFile(entry.commandLine)) {
            return;
        }

        if (dialog != null) {
            dialog.dismiss();
        }

        startRazeXR();
    }

    private boolean writeCommandLineFile(String commandLine) {

        FileOutputStream fos = null;

        try {

            File folder = new File(
                    Environment.getExternalStorageDirectory(),
                    "RazeXR"
            );

            if (!folder.exists() || !folder.isDirectory()) {
                Toast.makeText(
                        context,
                        "/sdcard/RazeXR not found.",
                        Toast.LENGTH_LONG
                ).show();
                return false;
            }

            File cmdFile = new File(folder, COMMANDLINE_FILE);

            fos = new FileOutputStream(cmdFile, false);

            fos.write(
                    commandLine.getBytes(StandardCharsets.UTF_8)
            );

            fos.flush();

            return true;

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    context,
                    "Error while writing commandline.txt",
                    Toast.LENGTH_LONG
            ).show();

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

    private void startRazeXR() {

        try {

            Intent intent = new Intent();

            intent.setClassName(
                    RAZE_PACKAGE,
                    RAZE_ACTIVITY
            );

            context.startActivity(intent);

        } catch (ActivityNotFoundException e) {

            Toast.makeText(
                    context,
                    "RazeXR not installed.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void styleButton(Button button, int mainColor) {

        GradientDrawable buttonBg = new GradientDrawable();

        buttonBg.setCornerRadius(32);

        buttonBg.setColor(
                Color.argb(
                        140,
                        Color.red(mainColor),
                        Color.green(mainColor),
                        Color.blue(mainColor)
                )
        );

        buttonBg.setStroke(
                2,
                Color.argb(180, 255, 255, 255)
        );

        button.setBackground(buttonBg);
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

    private void setButtonText(int index, String text) {

        Button button = dialog.findViewById(buttonIds[index]);

        if (button != null) {
            button.setText(text);
            button.setVisibility(View.VISIBLE);
        }
    }

    private String getHelpText(MainGame game) {

        switch (game) {

            case DUKE:
                return "/sdcard/RazeXR/raze/duke/...\n\n" +
                        "Duke Nukem 3D        = DUKE3D.GRP\n" +
                        "Duke Zone II         = DUKE3D.GRP, dukezone2.grp\n" +
                        "Duke it Out in D.C.  = DUKE3D.GRP, DUKEDC.GRP\n" +
                        "Caribbean            = DUKE3D.GRP, VACATION.GRP\n" +
                        "Nuclear Winter       = DUKE3D.GRP, NWINTER.GRP\n" +
                        "Penthouse Paradise   = DUKE3D.GRP, PENTHOUS.GRP,\n" +
                        "                       ppakgame.con\n" +
                        "Zombie Crises        = DUKE3D.GRP, ZombieCrises.GRP\n";

            case BLOOD:
                return "/sdcard/RazeXR/raze/blood/...\n\n" +
                        "Blood                = BLOOD.INI, BLOOD.RFF, GUI.RFF,\n" +
                        "                       SOUNDS.RFF, SURFACE.DAT,\n" +
                        "                       TILES000.ART, TILES017.ART,\n" +
                        "                       VOXEL.DAT\n" +
                        "Cryptic Passage      = BLOOD.RFF, CRYPTIC.ZIP\n" +
                        "Marrow               = BLOOD.RFF, marrow.zip\n" +
                        "Fate of the Damned   = BLOOD.RFF, FATE.zip\n" +
                        "Death Wish           = BLOOD.RFF, deathwish.zip,\n" +
                        "                       dw_music.zip\n";

            case EXHUMED:
                return "/sdcard/RazeXR/raze/exhumed/...\n\n" +
                        "Exhumed-Powerslave   = STUFF.DAT, BOOK.MOV\n";

            case NAM:
                return "/sdcard/RazeXR/raze/nam/...\n\n" +
                        "NAM   = NAM.GRP, GAME.CON\n";

            case REDNECK:
                return "/sdcard/RazeXR/raze/rampage/...\n\n" +
                        "Redneck Rampage            = REDNECK.GRP\n" +
                        "Route 66                   = REDNECK.GRP, ROUTE66.zip\n" +
                        "Night of the Living Dead   = REDNECK.GRP, Dead.grp,\n" +
                        "                             Deadgame.con\n\n" +
                        "/sdcard/RazeXR/raze/ridesagain/...\n\n" +
                        "Rides Again                = RIDES.GRP\n" +
                        "Uranus Attacks             = RIDES.GRP, URANUS.GRP,\n" +
                        "                             URANUS.CON\n";

            case SHADOW_WARRIOR:
                return "/sdcard/RazeXR/raze/shadowwarrior/...\n\n" +
                        "Shadow Warrior       = SW.GRP\n" +
                        "Twin Dragon          = SW.GRP, TD.GRP\n" +
                        "Wanton Destruction   = SW.GRP, WT.GRP\n";

            case WW2GI:
                return "/sdcard/RazeXR/raze/ww2gi...\n\n" +
                        "World War II GI   = WW2GI.GRP\n" +
                        "Platoon Leader    = WW2GI.GRP, PLATOONL.DAT,\n" +
                        "                    PLATOONL.DEF\n";
        }

        return "";
    }

    private void showHelpDialog(String helpText) {

        TextView textView = new TextView(context);

        textView.setText(helpText);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(16);
        textView.setPadding(40, 40, 40, 40);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setBackgroundColor(Color.parseColor("#2d2d2d"));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.parseColor("#2d2d2d"));
        scrollView.addView(textView);

        AlertDialog helpDialog = new AlertDialog.Builder(context)
                .setView(scrollView)
                .setPositiveButton("OK", null)
                .create();

        helpDialog.show();

        if (helpDialog.getWindow() != null) {
            helpDialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.parseColor("#2d2d2d"))
            );
        }
    }
}