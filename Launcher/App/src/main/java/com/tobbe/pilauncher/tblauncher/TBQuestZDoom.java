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
import android.os.AsyncTask;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tobbe.pilauncher.MainActivity;
import com.tobbe.pilauncher.R;
import com.tobbe.pilauncher.platforms.AbstractPlatform;
import com.tobbe.pilauncher.ui.AppsAdapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TBQuestZDoom {

    private final Context context;
    private Dialog dialog;

    private static final String QZD_PACKAGE      = "com.drbeef.questzdoom";
    private static final String QZD_ACTIVITY     = "com.drbeef.questzdoom.GLES3JNIActivity";
    private static final String QZD_DIR          = "QuestZDoom";
    private static final String WADS_DIR         = "QuestZDoom/wads";
    private static final String MODS_DIR         = "QuestZDoom/mods";
    private static final String COMMANDLINE_FILE  = "commandline.txt";
    private static final String[] HIDDEN_UNKNOWN_MODS = {
            "laser-sight-0.5.5-vr.pk3",
            "Ultimate-Cheat-Menu.zip"
    };

    // ─── Button ID ranges ────────────────────────────────────────────────────────

    // 1-99    Main games
    // 100-199 Gameplay Mods
    // 200-299 Map Packs
    // 300-399 VR Weapons
    // 400-499 Models
    // 500+    Others

    private enum MainGame {
        DOOM1, DOOM2, FREEDOOM1, FREEDOOM2,
        HERETIC, HEXEN, CHEX3, STRIFE, HACX,
        SQUARE, ROTT, OSIRIS
    }

    private enum ModCategory {
        GAMEPLAY,   // 100–199, single-select
        MAPPACK,    // 200–299, single-select
        VRWEAPONS,  // 300–399, single-select
        MODELS,     // 400–499, multi-select
        OTHERS      // 500+,    multi-select
    }

    private static class MainGameEntry {
        final String title;
        final MainGame game;
        final String iwad;
        final String zipFile;
        final String[] requiredFiles;
        final String downloadUrl;

        MainGameEntry(String title, MainGame game, String iwad,
                      String zipFile, String[] requiredFiles, String downloadUrl) {
            this.title = title;
            this.game = game;
            this.iwad = iwad;
            this.zipFile = zipFile;
            this.requiredFiles = requiredFiles;
            this.downloadUrl = downloadUrl;
        }

        boolean hasDownload() {
            return downloadUrl != null && !downloadUrl.trim().isEmpty();
        }
    }

    private static class ModEntry {
        final int        intId;           // mirrors "Baggy's QZD-Launcher's" coregames.json intID
        final String     title;
        final ModCategory category;
        final String     zipFile;
        final String[]   extractedFiles;
        final String     downloadUrl;
        final MainGame[] compatibleWith;  // null = all games
        final int[]      incompatibleIds; // IDs from lstIncompatible in coregames.json
        final int[][]    requiredIds;     // IDs from lstrequired in coregames.json

        ModEntry(int intId, String title, ModCategory category,
                 String zipFile, String[] extractedFiles,
                 String downloadUrl, int[] incompatibleIds, int[][] requiredIds,
                 MainGame... compatibleWith) {
            this.intId          = intId;
            this.title          = title;
            this.category       = category;
            this.zipFile        = zipFile;
            this.extractedFiles = extractedFiles;
            this.downloadUrl    = downloadUrl;
            this.incompatibleIds = incompatibleIds != null ? incompatibleIds : new int[0];
            this.requiredIds     = requiredIds;
            this.compatibleWith  = (compatibleWith.length == 0) ? null : compatibleWith;
        }
    }

    private static final MainGameEntry[] MAIN_GAMES = {
            new MainGameEntry("Doom 1", MainGame.DOOM1, "doom.wad",
                    "DOOM.WAD", new String[]{"DOOM.WAD"}, ""),

            new MainGameEntry("Doom 2", MainGame.DOOM2, "DOOM2.WAD",
                    "DOOM2.WAD", new String[]{"DOOM2.WAD"}, ""),

            new MainGameEntry("FreeDoom (Phase 1)", MainGame.FREEDOOM1, "freedoom1.wad",
                    "freedoom-0.12.1.zip", new String[]{"freedoom1.wad"},
                    "https://github.com/freedoom/freedoom/releases/download/v0.12.1/freedoom-0.12.1.zip"),

            new MainGameEntry("FreeDoom (Phase 2)", MainGame.FREEDOOM2, "freedoom2.wad",
                    "freedoom-0.12.1.zip", new String[]{"freedoom2.wad"},
                    "https://github.com/freedoom/freedoom/releases/download/v0.12.1/freedoom-0.12.1.zip"),

            new MainGameEntry("Heretic", MainGame.HERETIC, "heretic.wad",
                    "HERETIC.WAD", new String[]{"HERETIC.WAD"}, ""),

            new MainGameEntry("Hexen", MainGame.HEXEN, "hexen.wad",
                    "HEXEN.WAD", new String[]{"HEXEN.WAD"}, ""),

            new MainGameEntry("Chex Quest 3", MainGame.CHEX3, "chex3.wad",
                    "ChexQuest3.zip", new String[]{"chex3.wad"},
                    "https://www.moddb.com/downloads/start/15691"),

            new MainGameEntry("Strife", MainGame.STRIFE, "strife1.wad",
                    "STRIFE1.WAD", new String[]{"STRIFE1.WAD"}, ""),

            new MainGameEntry("HacX", MainGame.HACX, "hacx.wad",
                    "hacx12.zip", new String[]{"HACX.WAD"},
                    "https://www.moddb.com/downloads/start/83028"),

            new MainGameEntry("Adventures of Square", MainGame.SQUARE, "square1.pk3",
                    "square-ep2-pk3-2.1.zip", new String[]{"square1.pk3"},
                    "http://adventuresofsquare.com/downloads/square-ep2-pk3-2.1.zip"),

            new MainGameEntry("Return of the Triad", MainGame.ROTT, "rott_tc_full.pk3",
                    "rott_tc_16.zip", new String[]{"rott_tc_full.pk3"},
                    "https://www.moddb.com/downloads/start/36543"),

            new MainGameEntry("Project Osiris", MainGame.OSIRIS, "osiris.ipk3",
                    "osiris-1.0.3.zip", new String[]{"osiris.ipk3"},
                    "https://www.moddb.com/downloads/start/254801")
    };

    private static final ModEntry[] ALL_MODS = {

            // ── GAMEPLAY MODS ──

            new ModEntry(100, "Brutal Doom", ModCategory.GAMEPLAY,
                    "bd21.zip",
                    new String[]{"brutalv21.pk3"},
                    "https://www.moddb.com/downloads/start/194554",
                    new int[]{1, 7, 11, 12, 13, 14, 16, 17},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2, MainGame.CHEX3),

            new ModEntry(101, "Brutal Doom Bolognese Gore Mod", ModCategory.GAMEPLAY,
                    "sm4BBgorev3.zip",
                    new String[]{"sm4BBgorev3.pk3"},
                    "https://www.moddb.com/downloads/start/171168",
                    new int[]{1, 500},
                    null),

            new ModEntry(103, "Brutal Doom MeatGrinder", ModCategory.GAMEPLAY,
                    "meatgrinderv2fix.zip",
                    new String[]{"meatgrinderV2C.pk3"},
                    "https://www.moddb.com/downloads/start/186619",
                    new int[]{1, 5, 7, 8, 11, 12, 13, 14, 16, 17, 405, 406, 407, 409, 500},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(102, "Brutal Doom Monsters Only", ModCategory.GAMEPLAY,
                    "bd21monstersonlyfix.zip",
                    new String[]{"bd21monstersonlyfix.pk3"},
                    "https://www.moddb.com/downloads/start/179866",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 16, 17, 405, 406, 407, 409, 500},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),


            // ── MAP PACKS ──

            new ModEntry(213, "Alien Eradication", ModCategory.MAPPACK,
                    "AlienEradication.zip",
                    new String[]{"ALIENSERADICATIONRC1RELEASE.wad", "KKALIENTRILOGLYPAYLDEDRC1RELEASE.pk3"},
                    "https://www.moddb.com/downloads/start/193618",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 101, 102, 103, 300, 303, 304, 305, 306, 400, 401, 403, 404, 406, 407, 408, 409, 410, 500},
                    new int[][]{{405}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(205, "Alien Vendetta", ModCategory.MAPPACK,
                    "Alien_Vendetta.zip",
                    new String[]{"AV.WAD"},
                    "https://www.moddb.com/downloads/start/64169",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 404, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(201, "Back to Saturn X Ep. 1", ModCategory.MAPPACK,
                    "btsx_e1.zip",
                    new String[]{"btsx_e1a.wad", "btsx_e1b.wad"},
                    "https://www.moddb.com/downloads/start/87899",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 400, 403, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(219, "Back to Saturn X Ep. 2", ModCategory.MAPPACK,
                    "btsx_e2.zip",
                    new String[]{"btsx_e2a.wad", "btsx_e2b.wad"},
                    "https://www.moddb.com/downloads/start/87901",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 400, 403, 500},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(233, "Brutal Doom 64 Main", ModCategory.MAPPACK,
                    "BrutalDoom64.zip",
                    new String[]{"bd64gamev2.pk3", "bd64mapsV2.pk3", "ZD64MUSIC.PK3"},
                    "https://www.moddb.com/downloads/start/113435",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(211, "Brutal Wolfenstein 3D", ModCategory.MAPPACK,
                    "wolf.zip",
                    new String[]{"ZMC-BWFinal.pk3"},
                    "https://www.moddb.com/downloads/start/171037",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 101, 102, 103, 300, 301, 302, 303, 304, 305, 306, 400, 401, 402, 403, 404, 405, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(235, "Castlevania Simons Destiny", ModCategory.MAPPACK,
                    "castlevania.zip",
                    new String[]{"Castlevania.ipk3"},
                    "https://www.moddb.com/addons/start/195416",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(218, "Community Chest", ModCategory.MAPPACK,
                    "cchest.zip",
                    new String[]{"Cchest.wad"},
                    "https://www.moddb.com/downloads/start/56925",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 303, 305, 306, 404, 410, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(218, "Community Chest 2", ModCategory.MAPPACK,
                    "cchest2.zip",
                    new String[]{"Cchest2.wad"},
                    "https://www.moddb.com/downloads/start/56926",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 303, 305, 306, 404, 410, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(208, "Doom 2 the Way id Did", ModCategory.MAPPACK,
                    "d2twid.zip",
                    new String[]{"D2TWID.wad"},
                    "https://www.moddb.com/downloads/start/62142",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 404, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(204, "Doom 64 Retribution", ModCategory.MAPPACK,
                    "D64RTRv1.5.zip",
                    new String[]{"D64RTR[v1.5].WAD"},
                    "https://www.moddb.com/downloads/start/170502",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 400, 401, 402, 403, 404, 406, 408, 500},
                    new int[][]{{410}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(207, "Doom the Way id Did", ModCategory.MAPPACK,
                    "dtwid.zip",
                    new String[]{"DTWID.wad"},
                    "https://www.moddb.com/downloads/start/62141",
                    new int[]{1, 3, 5, 7, 8, 11, 12, 13, 14, 404, 500},
                    null,
                    MainGame.DOOM1, MainGame.FREEDOOM1),

            new ModEntry(241, "Ghosted 2", ModCategory.MAPPACK,
                    "ghosted2-vr-v2.2.pk3",
                    new String[]{"ghosted2-vr-v2.2.pk3"},
                    "https://github.com/iAmErmac/Ghosted2-VR/releases/download/v2.2/ghosted2-vr-v2.2.pk3",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(230, "Going Down", ModCategory.MAPPACK,
                    "goingdownE3-sept27.zip",
                    new String[]{"goingdownE3.wad"},
                    "https://www.moddb.com/downloads/start/75793",
                    new int[]{1, 2, 4, 5, 7, 8, 11, 12, 13, 14},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(243, "GZ P.T. VR Enhanced Edition", ModCategory.MAPPACK,
                    "GZPT-vr-v1.0.pk3",
                    new String[]{"GZPT-vr-v1.0.pk3"},
                    "https://github.com/iAmErmac/GZPT-VR/releases/download/v1.0/GZPT-vr-v1.0.pk3",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(203, "Hell on Earth Starter Pack", ModCategory.MAPPACK,
                    "Brutal_Doom_20b_Hell_on_Earth_Starter_Pack.zip",
                    new String[]{"hellonearthstarterpack.wad"},
                    "https://www.moddb.com/downloads/start/95669",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(214, "Hocus Pocus Doom", ModCategory.MAPPACK,
                    "hocus.zip",
                    new String[]{"HOCUS.pk3"},
                    "https://www.moddb.com/downloads/start/189681",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 400, 401, 402, 403, 404, 405, 406, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(237, "Legend of Doom", ModCategory.MAPPACK,
                    "legend-of-doom.pk3",
                    new String[]{"legend-of-doom.pk3"},
                    "https://github.com/baggyg/legend-of-doom/releases/download/v1.1.0vr/legend-of-doom.pk3",
                    null,
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(209, "Maps of Chaos The Full Package", ModCategory.MAPPACK,
                    "mapsofchaos.zip",
                    new String[]{"mapsofchaos.wad"},
                    "https://www.moddb.com/addons/start/63294",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 305, 306, 404, 406, 408, 500},
                    new int[][]{{100,101,102,103}},
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(222, "Pirate Doom", ModCategory.MAPPACK,
                    "pirates.zip",
                    new String[]{"Pirates!.wad"},
                    "https://www.moddb.com/downloads/start/120561",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(200, "Scythe 2", ModCategory.MAPPACK,
                    "scythe2.zip",
                    new String[]{"scythe2.wad"},
                    "https://www.moddb.com/downloads/start/66226",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 400, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(206, "SIGIL", ModCategory.MAPPACK,
                    "SIGIL_v1_21.zip",
                    new String[]{"SIGIL_v1_21.wad"},
                    "https://romero.com/s/SIGIL_v1_21.zip",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 404, 500},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(244, "SIGIL II", ModCategory.MAPPACK,
                    "SIGIL_II_V1_0.zip",
                    new String[]{"SIGIL_II_V1_0.WAD"},
                    "https://romero.com/s/SIGIL_II_V1_0.zip",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 404, 500},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(240, "Splatterhouse VR", ModCategory.MAPPACK,
                    "splatterhouse-vr-v1.0.pk3",
                    new String[]{"splatterhouse-vr-v1.0.pk3"},
                    "https://github.com/iAmErmac/Splatterhouse-VR/releases/download/v1.0/splatterhouse-vr-v1.0.pk3",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(217, "Suspended in Dusk", ModCategory.MAPPACK,
                    "sid.zip",
                    new String[]{"sid.wad"},
                    "https://www.moddb.com/downloads/start/116588",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 303, 305, 306, 404, 410, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(239, "The City of the Damned - Apocalypse", ModCategory.MAPPACK,
                    "tcotda-vr-v1.0.pk3",
                    new String[]{"tcotda-vr-v1.0.pk3"},
                    "https://github.com/iAmErmac/TCOTDA-VR/releases/download/v1.0/tcotda-vr-v1.0.pk3",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(210, "UAC Ultra", ModCategory.MAPPACK,
                    "uac_ultra.zip",
                    new String[]{"uacultra.wad"},
                    "https://www.moddb.com/addons/start/68434",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 404, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(216, "Unloved", ModCategory.MAPPACK,
                    "unloved.zip",
                    new String[]{"unloved.pk3"},
                    "https://www.moddb.com/downloads/start/72527",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 303, 305, 306, 404, 410, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(234, "Wolfenstein 3D", ModCategory.MAPPACK,
                    "wolf3d.zip",
                    new String[]{"Wolf3D.pk7", "Wolf3D_Common.pk7", "Wolf3D_HighRes.pk7", "Wolf3D_Resources.pk3"},
                    "https://www.afadoomer.com/wolf3d/files/Wolf3D_TC_2.0.2.zip",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 101, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 308, 400, 401, 402, 403, 404, 405, 500, 504},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(242, "ZBlood - Bloody Hell", ModCategory.MAPPACK,
                    "ZBloody_Hell_v1915.zip",
                    new String[]{"ZBloody_Hell_v1915.pk3"},
                    "https://github.com/iAmErmac/ZBlood-VR/releases/download/v1.0/ZBloody_Hell_v1915.zip",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 307, 400, 401, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),


            // ── VR WEAPONS ──

            new ModEntry(300, "3D VR Weapons for Brutal Doom", ModCategory.VRWEAPONS,
                    "BR_VR_Quest_RC2.zip",
                    new String[]{"BR_VR_Quest_RC2.zip"},
                    "https://github.com/ajantaju/br_vr/releases/download/RC2/BR_VR_Quest_RC2.zip",
                    null,
                    new int[][]{{100}},
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(308, "3D VR Weapons for Brutal Doom 64", ModCategory.VRWEAPONS,
                    "BR_VR_BD64_Quest.zip",
                    new String[]{"BR_VR_BD64_Quest.zip"},
                    "https://github.com/ajantaju/br_vr/releases/download/B35/BR_VR_BD64_Quest.zip",
                    null,
                    new int[][]{{233}, {503}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(309, "3D VR Weapons for Brutal Wolfenstein", ModCategory.VRWEAPONS,
                    "VR_ZMC_BrutalWolf.zip",
                    new String[]{"VR_ZMC_BrutalWolf.zip"},
                    "https://github.com/ajantaju/br_vr/raw/master/VR_ZMC_BrutalWolf.zip",
                    null,
                    new int[][]{{211}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(311, "3D VR Weapons for Chex", ModCategory.VRWEAPONS,
                    "Chex_Weapons_Quest_RC1.pk3",
                    new String[]{"Chex_Weapons_Quest_RC1.pk3"},
                    "https://github.com/iAmErmac/Chex-Quest-VR-Weapons/releases/download/rc1/Chex_Weapons_Quest_RC1.pk3",
                    null,
                    null,
                    MainGame.CHEX3),

            new ModEntry(306, "3D VR Weapons for Doom 64 Retribution", ModCategory.VRWEAPONS,
                    "D64Retribution.zip",
                    new String[]{"D64Retribution.zip"},
                    "https://github.com/ajantaju/br_vr/raw/master/D64Retribution.zip",
                    null,
                    new int[][]{{204}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(305, "3D VR Weapons for HacX", ModCategory.VRWEAPONS,
                    "HacX.zip",
                    new String[]{"HacX.zip"},
                    "https://github.com/ajantaju/br_vr/raw/master/HacX.zip",
                    new int[]{1, 8, 11, 12, 14, 15, 100, 102, 103},
                    null,
                    MainGame.HACX),

            new ModEntry(313, "3D VR Weapons for Hocus Pocus", ModCategory.VRWEAPONS,
                    "Hocus-Doom-VR_v1.0.pk3",
                    new String[]{"Hocus-Doom-VR_v1.0.pk3"},
                    "https://github.com/iAmErmac/Hocus-Doom-VR/releases/download/v1.0/Hocus-Doom-VR_v1.0.pk3",
                    null,
                    new int[][]{{214}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(304, "3D VR Weapons for Meatgrinder", ModCategory.VRWEAPONS,
                    "BR_VR_MeatGrinder.zip",
                    new String[]{"BR_VR_MeatGrinder.zip"},
                    "https://github.com/ajantaju/br_vr/releases/download/RC1/BR_VR_MeatGrinder.zip",
                    null,
                    new int[][]{{103}},
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(301, "3D Universal VR Weapons", ModCategory.VRWEAPONS,
                    "vrweapons_vanilla.pk3",
                    new String[]{"vrweapons_vanilla.pk3"},
                    "https://github.com/iAmErmac/Universal_Doom_3DWeapons_VR/releases/download/v1.0.0/HDVRweapons_Quest_Plus_v1.0.pk3",
                    new int[]{1, 100, 214, 215},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2, MainGame.SQUARE, MainGame.STRIFE, MainGame.CHEX3, MainGame.HERETIC, MainGame.HEXEN, MainGame.HACX, MainGame.OSIRIS, MainGame.ROTT),

            new ModEntry(303, "Voxel VR Heretic Weapons", ModCategory.VRWEAPONS,
                    "HereticVRWeapons.pk3.zip",
                    new String[]{"HereticVRWeapons.pk3"},
                    "https://www.moddb.com/downloads/start/194141",
                    null,
                    null,
                    MainGame.HERETIC),

            new ModEntry(312, "Voxel VR Hexen Weapons", ModCategory.VRWEAPONS,
                    "hexen_VR_weapons_Ermac_WIP.zip",
                    new String[]{"hexen_VR_weapons_Ermac_WIP.pk3"},
                    "https://www.moddb.com/downloads/start/272500",
                    null,
                    null,
                    MainGame.HEXEN),

            new ModEntry(302, "Voxel VR Weapons for Vanilla", ModCategory.VRWEAPONS,
                    "voxelweapons.zip",
                    new String[]{"WeaponsForVR.pk3"},
                    "https://www.moddb.com/downloads/start/194140",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 100, 103, 213, 214, 215},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(307, "VR Weapons for Classic Doom (Alternative)", ModCategory.VRWEAPONS,
                    "classicweapons.zip",
                    new String[]{"Classic_Weapons.pk3"},
                    "https://www.moddb.com/downloads/start/194134",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 100, 103, 213, 214, 215},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(310, "ZBlood 3D VR Weapons", ModCategory.VRWEAPONS,
                    "zblood-vr-weapons-v1.0.pk3",
                    new String[]{"zblood-vr-weapons-v1.0.pk3"},
                    "https://github.com/iAmErmac/ZBlood-VR/releases/download/v1.1/zblood-vr-weapons-v1.1.pk3",
                    null,
                    new int[][]{{242}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),


            // ── TEXTURES / SOUNDS ──

            new ModEntry(412, "3D Items for Classic Doom", ModCategory.MODELS,
                    "3ditems.zip",
                    new String[]{"Classic_3DModels.pk3"},
                    "https://www.moddb.com/downloads/start/194138",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 100, 102, 103, 204, 211, 213, 214, 215, 405, 406, 407, 409},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(411, "3D Monsters for Classic Doom", ModCategory.MODELS,
                    "3dmonsters.zip",
                    new String[]{"Classic_3DMonsters.pk3"},
                    "https://www.moddb.com/downloads/start/194137",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 100, 102, 103, 204, 211, 213, 214, 215, 405, 406, 407, 409},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(403, "AI Upscaled Textures", ModCategory.MODELS,
                    "NeuralUpscale2x_v1.0.zip",
                    new String[]{"NeuralUpscale2x_v1.0.pk3"},
                    "https://www.moddb.com/downloads/start/194344",
                    new int[]{1, 7, 8, 11, 12, 13, 14, 404, 406, 407, 500},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),

            new ModEntry(406, "Brutal Wolfenstein HD Textures", ModCategory.MODELS,
                    "BW_HighRes.zip",
                    new String[]{"wolfTex.pk3"},
                    "https://www.moddb.com/downloads/start/194408",
                    null,
                    new int[][]{{211}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(413, "Doom 2 Music FULL Remake", ModCategory.MODELS,
                    "wiebe.zip",
                    new String[]{"Doom2MusicBrandonWiebe.wad"},
                    "https://www.moddb.com/addons/start/182501",
                    null,
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(410, "Doom 64 Retribution - OGG Music Pack", ModCategory.MODELS,
                    "doom64ogg.zip",
                    new String[]{"D64MUS.PK3"},
                    "https://www.moddb.com/addons/start/123039",
                    null,
                    new int[][]{{204}},
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(409, "Duke Nukem 3D", ModCategory.MODELS,
                    "duke.zip",
                    new String[]{"DukeNukem3D_TC.pk3"},
                    "https://www.moddb.com/downloads/start/194209",
                    new int[]{1, 2, 4, 7, 8, 11, 12, 13, 14, 100, 102, 103, 300, 301, 302, 303, 304, 305, 306, 400, 401, 402, 403, 404, 405, 406, 407, 408, 500},
                    null,
                    MainGame.DOOM2, MainGame.FREEDOOM2),

            new ModEntry(404, "Hacx 2.0 AI Upscaled", ModCategory.MODELS,
                    "HacX_GFX_Overhaul.pk3.zip",
                    new String[]{"HacX_GFX_Overhaul.pk3"},
                    "https://www.moddb.com/downloads/start/193838",
                    null,
                    null,
                    MainGame.HACX),

            new ModEntry(415, "Heretic Neural Texture Pack", ModCategory.MODELS,
                    "neuralheretic.zip",
                    new String[]{"heretic_gz.pk3"},
                    "https://www.moddb.com/addons/start/189684",
                    null,
                    null,
                    MainGame.HERETIC),

            new ModEntry(414, "Hexen Neural Texture Pack", ModCategory.MODELS,
                    "hexen_ntp_v4.zip",
                    new String[]{"hexen_gz_v4.pk3"},
                    "https://www.moddb.com/downloads/start/225048",
                    null,
                    null,
                    MainGame.HEXEN),

            new ModEntry(401, "IDKFA Remastered Soundtrack", ModCategory.MODELS,
                    "idkfa.zip",
                    new String[]{"IDKFAv2.wad"},
                    "https://www.moddb.com/downloads/start/194632",
                    null,
                    null,
                    MainGame.DOOM1, MainGame.FREEDOOM1),

            new ModEntry(421, "VoxelDoom", ModCategory.MODELS,
                    "cheello_voxels.zip",
                    new String[]{"cheello_voxels_v2_1_lzd.pk3"},
                    "https://www.moddb.com/addons/start/254796",
                    new int[]{1, 7, 8, 9, 10, 11, 12, 13, 14, 100, 102, 103},
                    null,
                    MainGame.DOOM1, MainGame.DOOM2, MainGame.FREEDOOM1, MainGame.FREEDOOM2),


            // ── OTHERS ──

            new ModEntry(500, "Chex Quest Patch", ModCategory.OTHERS,
                    "Brutal-Doom-v21-Chex-Quest-Patch.8.zip",
                    new String[]{"cheques-07082020.pk3"},
                    "https://www.moddb.com/downloads/start/139591",
                    null,
                    new int[][]{{100}},
                    MainGame.CHEX3),

            new ModEntry(502, "Isabelle Companion", ModCategory.OTHERS,
                    "isabelle.zip",
                    new String[]{"ISABELLE.pk3"},
                    "https://www.moddb.com/downloads/start/195853",
                    null,
                    null),

            new ModEntry(501, "SlomoBulletTime Ultimate", ModCategory.OTHERS,
                    "bullettime.zip",
                    new String[]{"SlomoBulletTime_Ultimate_R3.1c.pk3"},
                    "https://www.moddb.com/addons/start/125210",
                    null,
                    null),

            new ModEntry(504, "Tacticool Holosight", ModCategory.OTHERS,
                    "BR_TactiCool_Replacer.zip",
                    new String[]{"BR_TactiCool_Replacer.zip"},
                    "https://github.com/ajantaju/br_vr/raw/master/BR_TactiCool_Replacer.zip",
                    null,
                    new int[][]{{100, 300}}),
    };

    public TBQuestZDoom(Context context) {
        this.context = context;
    }

    public void show() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_qzd);

        File folder = new File(Environment.getExternalStorageDirectory(), QZD_DIR);
        if (!folder.exists() || (!folder.isDirectory())) {
            startQuestZDoom();
            return;
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int mainColor = Color.rgb(45, 45, 45);

        try {
            PackageManager pm    = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(QZD_PACKAGE, 0);
            String name          = info.loadLabel(pm).toString();

            ImageView tempImage = new ImageView(context);
            AbstractPlatform platform = AbstractPlatform.getPlatform(info);
            platform.loadIcon((MainActivity) context, tempImage, info, name);

            tempImage.measure(
                    View.MeasureSpec.makeMeasureSpec(253, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(253, View.MeasureSpec.EXACTLY));
            tempImage.layout(0, 0, 253, 253);

            Bitmap bitmap = Bitmap.createBitmap(253, 253, Bitmap.Config.ARGB_8888);
            new Canvas(bitmap);
            tempImage.draw(new Canvas(bitmap));

            mainColor = AppsAdapter.getDominantColor(bitmap);

            int darkColor = AppsAdapter.darkenColor(mainColor, 0.35f);
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{mainColor, darkColor});
            bg.setCornerRadius(48);

            View layout = dialog.findViewById(R.id.layout);
            if (layout != null) layout.setBackground(bg);

        } catch (Exception e) {
            e.printStackTrace();
        }

        setupMainButtons(mainColor);
        dialog.show();

        View content = dialog.findViewById(R.id.layout);
        if (content != null && dialog.getWindow() != null) {
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            dialog.getWindow().setLayout(
                    content.getMeasuredWidth(),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setupMainButtons(int mainColor) {
        setTitle("QuestZDoom");

        View info = dialog.findViewById(R.id.info_qzd);
        if (info != null) {
            info.setVisibility(View.VISIBLE);
            info.setOnClickListener(v -> showHelpDialog(
                    "QuestZDoom Launcher\n\n" +
                            "sdcard/QuestZDoom/wads...\n\n" +
                            "Doom 1               = DOOM.WAD\n" +
                            "Doom 2               = DOOM2.WAD\n" +
                            "Free Doom (Phase 1)  = freedoom1.wad\n" +
                            "Free Doom (Phase 2)  = freedoom2.wad\n" +
                            "Heretic              = HERETIC.WAD\n" +
                            "Chex Quest 3         = chex3.wad\n" +
                            "Strife               = STRIFE1.WAD\n" +
                            "Hexen                = HEXEN.WAD\n" +
                            "HacX                 = HACX.WAD\n" +
                            "Adventures of Square = square1.pk3\n" +
                            "Return of the Triad  = rott_tc_full.pk3\n" +
                            "Project Osiris       = osiris.ipk3\n\n" +
                            "sdcard/QuestZDoom/mods...\n\n" +
                            "Any mods goes here"
            ));
        }

        View back = dialog.findViewById(R.id.back_button);
        if (back != null) back.setVisibility(View.GONE);

        int[] mainButtonResIds = {
                R.id.button_qzd1,  R.id.button_qzd2,  R.id.button_qzd3,
                R.id.button_qzd4,  R.id.button_qzd5,  R.id.button_qzd6,
                R.id.button_qzd7,  R.id.button_qzd8,  R.id.button_qzd9,
                R.id.button_qzd10, R.id.button_qzd11, R.id.button_qzd12
        };

        for (int i = 0; i < MAIN_GAMES.length && i < mainButtonResIds.length; i++) {
            Button btn = dialog.findViewById(mainButtonResIds[i]);
            if (btn == null) continue;

            MainGameEntry entry = MAIN_GAMES[i];
            btn.setText(entry.title);
            btn.setAllCaps(true);
            btn.setVisibility(View.VISIBLE);
            resetButtonLayout(btn);
            styleButton(btn, mainColor);

            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) btn.getLayoutParams();

            lp.leftMargin = 8;
            lp.rightMargin = 8;

            btn.setLayoutParams(lp);

            if (isMainGameAvailable(entry)) {
                setButtonAvailable(btn);
                btn.setOnClickListener(v -> showModSelection(entry, mainColor));

            } else if (entry.hasDownload()) {
                setButtonAvailable(btn);

                btn.setText(entry.title + "  ⬇");
                btn.setTextColor(Color.parseColor("#FFD700"));

                btn.setOnClickListener(v -> downloadAndInstallMainGame(entry, mainColor, () -> {
                    setupMainButtons(mainColor);
                    showModSelection(entry, mainColor);
                }));

            } else {
                setButtonUnavailable(btn);
            }
        }

        for (int i = MAIN_GAMES.length; i < mainButtonResIds.length; i++) {
            Button btn = dialog.findViewById(mainButtonResIds[i]);
            if (btn != null) {
                resetButtonLayout(btn);
                btn.setVisibility(View.GONE);
            }
        }
    }

    private void showModSelection(MainGameEntry gameEntry, int mainColor) {
        setTitle(gameEntry.title);

        View info = dialog.findViewById(R.id.info_qzd);
        if (info != null) info.setVisibility(View.GONE);

        View back = dialog.findViewById(R.id.back_button);
        if (back != null) {
            back.setVisibility(View.VISIBLE);
            back.setOnClickListener(v -> setupMainButtons(mainColor));
        }

        final ModEntry[] selectedGameplay  = {null};
        final ModEntry[] selectedMapPack   = {null};
        final ModEntry[] selectedVRWeapons = {null};
        final List<ModEntry> selectedModels = new ArrayList<>();
        final List<ModEntry> selectedOthers = new ArrayList<>();

        preselectModsFromCommandLine(gameEntry, selectedGameplay, selectedMapPack,
                selectedVRWeapons, selectedModels, selectedOthers);

        int[] btnIds = {
                R.id.button_qzd1, R.id.button_qzd2, R.id.button_qzd3,
                R.id.button_qzd4, R.id.button_qzd5, R.id.button_qzd6,
                R.id.button_qzd7, R.id.button_qzd8, R.id.button_qzd9,
                R.id.button_qzd10, R.id.button_qzd11, R.id.button_qzd12
        };

        String[] names = {
                context.getString(R.string.qzd_gameplaymods),
                context.getString(R.string.qzd_mappacks),
                context.getString(R.string.qzd_vrweapons),
                context.getString(R.string.qzd_models),
                context.getString(R.string.qzd_others)
        };

        for (int i = 0; i < btnIds.length; i++) {
            Button btn = dialog.findViewById(btnIds[i]);
            if (btn == null) continue;

            if (i < 5) {
                btn.setVisibility(View.VISIBLE);
                btn.setText(names[i]);
                btn.setAllCaps(true);
                btn.setEnabled(true);
                btn.setAlpha(1f);
                btn.setTextColor(Color.WHITE);
                styleButton(btn, mainColor);
            } else {
                btn.setVisibility(View.GONE);
            }
        }

        LinearLayout layout = dialog.findViewById(R.id.layout);
        if (layout == null) return;

        View oldDynamicStart = layout.findViewWithTag("qzd_dynamic_start");
        if (oldDynamicStart != null) layout.removeView(oldDynamicStart);

        View oldSelectedText = layout.findViewWithTag("qzd_selected_mods_text");
        if (oldSelectedText != null) layout.removeView(oldSelectedText);

        TextView selectedModsText = new TextView(context);
        selectedModsText.setTag("qzd_selected_mods_text");
        selectedModsText.setTextColor(Color.WHITE);
        selectedModsText.setTextSize(14f);
        selectedModsText.setPadding(12, 4, 12, 0);
        selectedModsText.setTypeface(Typeface.MONOSPACE);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textLp.topMargin = 4;
        textLp.bottomMargin = -12;
        selectedModsText.setLayoutParams(textLp);

        Runnable refreshSelectedText = () -> updateSelectedModsText(
                selectedModsText,
                selectedGameplay[0],
                selectedMapPack[0],
                selectedVRWeapons[0],
                selectedModels,
                selectedOthers
        );

        refreshSelectedText.run();
        layout.addView(selectedModsText);

        Button gameplayBtn = dialog.findViewById(R.id.button_qzd1);
        Button mapBtn      = dialog.findViewById(R.id.button_qzd2);
        Button vrBtn       = dialog.findViewById(R.id.button_qzd3);
        Button modelsBtn   = dialog.findViewById(R.id.button_qzd4);
        Button othersBtn   = dialog.findViewById(R.id.button_qzd5);

        if (gameplayBtn != null) gameplayBtn.setOnClickListener(v ->
                showCategoryChooserDialog(gameEntry, ModCategory.GAMEPLAY, mainColor,
                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                        selectedModels, selectedOthers, refreshSelectedText));

        if (mapBtn != null) mapBtn.setOnClickListener(v ->
                showCategoryChooserDialog(gameEntry, ModCategory.MAPPACK, mainColor,
                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                        selectedModels, selectedOthers, refreshSelectedText));

        if (vrBtn != null) vrBtn.setOnClickListener(v ->
                showCategoryChooserDialog(gameEntry, ModCategory.VRWEAPONS, mainColor,
                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                        selectedModels, selectedOthers, refreshSelectedText));

        if (modelsBtn != null) modelsBtn.setOnClickListener(v ->
                showCategoryChooserDialog(gameEntry, ModCategory.MODELS, mainColor,
                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                        selectedModels, selectedOthers, refreshSelectedText));

        if (othersBtn != null) othersBtn.setOnClickListener(v ->
                showCategoryChooserDialog(gameEntry, ModCategory.OTHERS, mainColor,
                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                        selectedModels, selectedOthers, refreshSelectedText));

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setTag("qzd_dynamic_start");
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionRowLp.topMargin = 42;

        Button clearBtn = new Button(context);
        clearBtn.setText(context.getString(R.string.qzd_clear));
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setTextSize(13f);
        clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearBtn.setAllCaps(false);
        clearBtn.setSingleLine(true);
        clearBtn.setMinWidth(0);
        clearBtn.setMinimumWidth(0);
        clearBtn.setPadding(10, 14, 10, 14);
        clearBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        styleButton(clearBtn, mainColor);

        Button startBtn = new Button(context);
        startBtn.setText("▶  " + context.getString(R.string.qzd_start));
        startBtn.setTextColor(Color.WHITE);
        startBtn.setTextSize(17f);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        startBtn.setAllCaps(false);
        startBtn.setSingleLine(false);
        startBtn.setMaxLines(2);
        startBtn.setMinWidth(0);
        startBtn.setMinimumWidth(0);
        startBtn.setPadding(14, 16, 14, 16);
        startBtn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        styleStartButton(startBtn);

        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0.7f
        );
        clearLp.rightMargin = 12;

        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.3f
        );
        startLp.leftMargin = 12;

        actionRow.addView(clearBtn, clearLp);
        actionRow.addView(startBtn, startLp);

        layout.addView(actionRow, actionRowLp);

        clearBtn.setOnClickListener(v -> {
            selectedGameplay[0] = null;
            selectedMapPack[0] = null;
            selectedVRWeapons[0] = null;
            selectedModels.clear();
            selectedOthers.clear();

            refreshSelectedText.run();

        });

        startBtn.setOnClickListener(v -> {
            String commandLine = buildCommandLine(
                    gameEntry,
                    selectedGameplay[0],
                    selectedMapPack[0],
                    selectedVRWeapons[0],
                    selectedModels,
                    selectedOthers
            );

            if (dialog != null) dialog.dismiss();

            if (writeCommandLineFile(commandLine)) {
                startQuestZDoom();
            }
        });
    }

    private void showCategoryChooserDialog(
            MainGameEntry gameEntry,
            ModCategory category,
            int mainColor,
            ModEntry[] selectedGameplay,
            ModEntry[] selectedMapPack,
            ModEntry[] selectedVRWeapons,
            List<ModEntry> selectedModels,
            List<ModEntry> selectedOthers,
            Runnable onSelectionChanged) {

        Dialog catDialog = new Dialog(context);
        catDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{mainColor, AppsAdapter.darkenColor(mainColor, 0.35f)});
        bg.setCornerRadius(48);
        root.setBackground(bg);

        TextView title = new TextView(context);
        title.setText(categoryLabel(category).replace("─", "").trim());
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 18);
        root.addView(title);

        Button backBtn = new Button(context);
        backBtn.setText("← Back");
        backBtn.setTextColor(Color.WHITE);
        styleButton(backBtn, mainColor);
        root.addView(backBtn);

        ScrollView scroll = new ScrollView(context);
        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(0, 18, 0, 0);
        scroll.addView(inner);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        catDialog.setContentView(root);

        if (catDialog.getWindow() != null) {
            catDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Runnable rebuild = new Runnable() {
            @Override
            public void run() {
                inner.removeAllViews();

                List<ModEntry> mods = getCompatibleMods(
                        gameEntry.game,
                        category,
                        selectedGameplay[0],
                        selectedMapPack[0],
                        selectedVRWeapons[0],
                        selectedModels,
                        selectedOthers
                );

                for (ModEntry mod : mods) {

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, 4, 0, 4);

                    CheckBox cb = new CheckBox(context);
                    cb.setText(mod.title);
                    cb.setTextColor(Color.WHITE);
                    cb.setTextSize(16f);
                    cb.setPadding(8, 10, 8, 10);

                    LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );
                    row.addView(cb, cbLp);

                    Button deleteBtn = new Button(context);
                    deleteBtn.setText("🗑");
                    deleteBtn.setTextColor(Color.WHITE);
                    deleteBtn.setTextSize(14f);
                    deleteBtn.setTypeface(Typeface.DEFAULT_BOLD);
                    deleteBtn.setPadding(8, 4, 8, 4);

                    GradientDrawable deleteBg = new GradientDrawable();
                    deleteBg.setCornerRadius(24);
                    deleteBg.setColor(Color.argb(150, 160, 40, 40));
                    deleteBg.setStroke(2, Color.argb(180, 255, 180, 180));
                    deleteBtn.setBackground(deleteBg);

                    LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    deleteBtn.setMinWidth(0);
                    deleteBtn.setMinHeight(0);
                    deleteBtn.setMinimumWidth(0);
                    deleteBtn.setMinimumHeight(0);

                    deleteBtn.setPadding(12, 4, 12, 4);
                    deleteBtn.setTextSize(12f);
                    deleteLp.gravity = android.view.Gravity.CENTER_VERTICAL;
                    deleteLp.leftMargin = 8;

                    boolean installed = isModInstalled(mod);

                    if (installed) {
                        deleteBtn.setVisibility(View.VISIBLE);
                    } else {
                        deleteBtn.setVisibility(View.GONE);
                    }

                    row.addView(deleteBtn, deleteLp);

                    boolean checked = false;
                    switch (category) {
                        case GAMEPLAY:  checked = mod == selectedGameplay[0]; break;
                        case MAPPACK:   checked = mod == selectedMapPack[0]; break;
                        case VRWEAPONS: checked = mod == selectedVRWeapons[0]; break;
                        case MODELS:    checked = selectedModels.contains(mod); break;
                        case OTHERS:    checked = containsModFile(selectedOthers, mod); break;
                    }

                    cb.setChecked(checked);

                    if (!installed) {
                        cb.append("  ⬇");
                        cb.setTextColor(Color.parseColor("#FFD700"));
                    }

                    deleteBtn.setOnClickListener(v -> {

                        AlertDialog confirmDialog;

                        LinearLayout layout = new LinearLayout(context);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(40, 36, 40, 36);

                        GradientDrawable bg = new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                new int[]{mainColor, AppsAdapter.darkenColor(mainColor, 0.35f)}
                        );
                        bg.setCornerRadius(48);
                        layout.setBackground(bg);

                        TextView title = new TextView(context);
                        title.setText(context.getString(R.string.qzd_deletemod));
                        title.setTextColor(Color.WHITE);
                        title.setTextSize(22f);
                        title.setTypeface(Typeface.DEFAULT_BOLD);
                        title.setPadding(0, 0, 0, 20);

                        TextView msg = new TextView(context);
                        msg.setText(mod.title + "\n\n" + context.getString(R.string.qzd_reallydelete));
                        msg.setTextColor(Color.WHITE);
                        msg.setTextSize(16f);
                        msg.setPadding(0, 0, 0, 30);

                        LinearLayout buttons = new LinearLayout(context);
                        buttons.setOrientation(LinearLayout.HORIZONTAL);
                        buttons.setGravity(android.view.Gravity.END);

                        Button cancelBtn = new Button(context);
                        cancelBtn.setText(context.getString(R.string.qzd_cancel));
                        cancelBtn.setTextColor(Color.WHITE);
                        styleButton(cancelBtn, mainColor);

                        Button deleteConfirmBtn = new Button(context);
                        deleteConfirmBtn.setText(context.getString(R.string.qzd_delete));
                        deleteConfirmBtn.setTextColor(Color.WHITE);
                        styleButton(deleteConfirmBtn, mainColor);

                        LinearLayout.LayoutParams btnLp =
                                new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT);

                        btnLp.leftMargin = 18;

                        buttons.addView(cancelBtn);
                        buttons.addView(deleteConfirmBtn, btnLp);

                        layout.addView(title);
                        layout.addView(msg);
                        layout.addView(buttons);

                        confirmDialog = new AlertDialog.Builder(context)
                                .setView(layout)
                                .create();

                        if (confirmDialog.getWindow() != null) {
                            confirmDialog.getWindow().setBackgroundDrawable(
                                    new ColorDrawable(Color.TRANSPARENT));
                        }

                        cancelBtn.setOnClickListener(v2 -> confirmDialog.dismiss());

                        deleteConfirmBtn.setOnClickListener(v2 -> {

                            applyCategorySelection(category, mod,
                                    selectedGameplay, selectedMapPack, selectedVRWeapons,
                                    selectedModels, selectedOthers, false);
                            if (onSelectionChanged != null) onSelectionChanged.run();

                            if (deleteInstalledMod(mod)) {

                                applyCategorySelection(category, mod,
                                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                                        selectedModels, selectedOthers, false);

                                removeModsWithMissingRequirements(
                                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                                        selectedModels, selectedOthers);

                                if (onSelectionChanged != null) onSelectionChanged.run();

                                writeCommandLineFile(buildCommandLine(
                                        gameEntry,
                                        selectedGameplay[0],
                                        selectedMapPack[0],
                                        selectedVRWeapons[0],
                                        selectedModels,
                                        selectedOthers
                                ));
                                Toast.makeText(context,
                                        mod.title + " " + context.getString(R.string.qzd_deleted),
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context,
                                        context.getString(R.string.qzd_couldnotdelete) + ": " + mod.title,
                                        Toast.LENGTH_LONG).show();
                            }

                            confirmDialog.dismiss();

                            this.run();
                        });

                        confirmDialog.show();
                    });

                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked && !isModInstalled(mod)) {
                            buttonView.setOnCheckedChangeListener(null);
                            ((CheckBox) buttonView).setChecked(false);

                            downloadAndInstallMod(mod, mainColor, () -> {
                                applyCategorySelection(category, mod,
                                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                                        selectedModels, selectedOthers, true);

                                autoSelectRequired(mod, mainColor,
                                        selectedGameplay, selectedMapPack, selectedVRWeapons,
                                        selectedModels, selectedOthers, this);

                                if (onSelectionChanged != null) onSelectionChanged.run();

                                this.run();
                            });
                            return;
                        }

                        applyCategorySelection(category, mod,
                                selectedGameplay, selectedMapPack, selectedVRWeapons,
                                selectedModels, selectedOthers, isChecked);

                        if (isChecked) {
                            autoSelectRequired(mod, mainColor,
                                    selectedGameplay, selectedMapPack, selectedVRWeapons,
                                    selectedModels, selectedOthers, this);
                        } else {
                            removeModsWithMissingRequirements(
                                    selectedGameplay, selectedMapPack, selectedVRWeapons,
                                    selectedModels, selectedOthers);
                        }

                        if (onSelectionChanged != null) onSelectionChanged.run();

                        this.run();
                    });

                    inner.addView(row);
                }
            }
        };

        backBtn.setOnClickListener(v -> {
            if (onSelectionChanged != null) onSelectionChanged.run();
            catDialog.dismiss();
        });

        rebuild.run();
        catDialog.show();

        if (catDialog.getWindow() != null) {
            catDialog.getWindow().setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92),
                    (int) (context.getResources().getDisplayMetrics().heightPixels * 0.80));
        }
    }

    private List<ModEntry> getCompatibleMods(
            MainGame game, ModCategory cat,
            ModEntry selGameplay, ModEntry selMapPack,
            ModEntry selVRWeapons, List<ModEntry> selModels,
            List<ModEntry> selOthers) {

        List<ModEntry> currentlySelected = new ArrayList<>();
        if (selGameplay  != null) currentlySelected.add(selGameplay);
        if (selMapPack   != null) currentlySelected.add(selMapPack);
        if (selVRWeapons != null) currentlySelected.add(selVRWeapons);
        currentlySelected.addAll(selModels);
        currentlySelected.addAll(selOthers);

        List<ModEntry> result = new ArrayList<>();

        for (ModEntry mod : ALL_MODS) {
            if (mod.category != cat) continue;
            if (!isCompatibleWithGame(mod, game)) continue;

            boolean blocked = false;
            for (ModEntry sel : currentlySelected) {
                if (mod != sel && areIncompatible(mod, sel)) {
                    blocked = true;
                    break;
                }
            }

            if (!blocked) {
                if (cat == ModCategory.VRWEAPONS && mod.requiredIds != null) {
                    if (areRequiredMet(mod, selGameplay, selMapPack, selVRWeapons, selModels, selOthers)) {
                        result.add(mod);
                    }
                } else {
                    result.add(mod);
                }
            }
        }

        if (cat == ModCategory.OTHERS) {
            for (ModEntry unknown : getUnknownInstalledMods()) {
                result.add(unknown);
            }
        }

        return result;
    }

    private boolean areIncompatible(ModEntry a, ModEntry b) {
        for (int id : a.incompatibleIds) {
            if (id == b.intId) return true;
        }
        for (int id : b.incompatibleIds) {
            if (id == a.intId) return true;
        }
        return false;
    }

    private boolean isCompatibleWithGame(ModEntry mod, MainGame game) {
        if (mod.compatibleWith == null) return true;
        for (MainGame g : mod.compatibleWith) {
            if (g == game) return true;
        }
        return false;
    }

    private void applyCategorySelection(
            ModCategory cat, ModEntry mod,
            ModEntry[] selGameplay, ModEntry[] selMapPack,
            ModEntry[] selVRWeapons, List<ModEntry> selModels,
            List<ModEntry> selOthers, boolean checked) {

        switch (cat) {
            case GAMEPLAY:
                selGameplay[0] = checked ? mod : null;
                break;

            case MAPPACK:
                selMapPack[0] = checked ? mod : null;
                break;

            case VRWEAPONS:
                selVRWeapons[0] = checked ? mod : null;
                break;

            case MODELS:
                if (checked) {
                    if (!selModels.contains(mod)) selModels.add(mod);
                } else {
                    selModels.remove(mod);
                }
                break;

            case OTHERS:
                if (checked) {
                    if (!containsModFile(selOthers, mod)) selOthers.add(mod);
                } else {
                    removeModFile(selOthers, mod);
                }
                break;
        }
    }

    private String buildCommandLine(
            MainGameEntry game,
            ModEntry gameplay,
            ModEntry mapPack,
            ModEntry vrWeapons,
            List<ModEntry> models,
            List<ModEntry> others) {

        StringBuilder sb = new StringBuilder();
        sb.append("qzdoom --supersampling 1.0 --msaa 1");
        sb.append(" -iwad wads/").append(game.iwad);

        appendMod(sb, gameplay);
        appendMod(sb, mapPack);
        appendMod(sb, vrWeapons);

        for (ModEntry m : models) appendMod(sb, m);
        for (ModEntry o : others) appendMod(sb, o);

        sb.append(" -file mods/laser-sight-0.5.5-vr.pk3 -file mods/Ultimate-Cheat-Menu.zip +logfile questzdoom.log");

        return sb.toString();
    }

    private void appendMod(StringBuilder sb, ModEntry mod) {
        if (mod != null) {
            for (String file : mod.extractedFiles) {
                sb.append(" -file mods/").append(file.trim());
            }
        }
    }

    private boolean isMainGameAvailable(MainGameEntry entry) {
        File dir = new File(Environment.getExternalStorageDirectory(), WADS_DIR);
        if (!dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (String req : entry.requiredFiles) {
            boolean found = false;
            for (File f : files) {
                if (f.getName().equalsIgnoreCase(req)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean areRequiredMet(ModEntry mod,
                                   ModEntry selGameplay, ModEntry selMapPack,
                                   ModEntry selVRWeapons,
                                   List<ModEntry> selModels,
                                   List<ModEntry> selOthers) {
        if (mod.requiredIds == null) return true;

        java.util.Set<Integer> selectedIds = new java.util.HashSet<>();
        if (selGameplay  != null) selectedIds.add(selGameplay.intId);
        if (selMapPack   != null) selectedIds.add(selMapPack.intId);
        if (selVRWeapons != null) selectedIds.add(selVRWeapons.intId);
        for (ModEntry m : selModels) selectedIds.add(m.intId);
        for (ModEntry o : selOthers) selectedIds.add(o.intId);

        for (int[] group : mod.requiredIds) {
            boolean groupMet = false;
            for (int id : group) {
                if (selectedIds.contains(id)) {
                    groupMet = true;
                    break;
                }
            }
            if (!groupMet) return false;
        }
        return true;
    }

    private void autoSelectRequired(ModEntry forMod, int mainColor,
                                    ModEntry[] selGameplay, ModEntry[] selMapPack,
                                    ModEntry[] selVRWeapons, List<ModEntry> selModels,
                                    List<ModEntry> selOthers, Runnable rebuildUI) {

        if (forMod.requiredIds == null) return;

        java.util.Set<Integer> selectedIds = new java.util.HashSet<>();
        if (selGameplay[0]  != null) selectedIds.add(selGameplay[0].intId);
        if (selMapPack[0]   != null) selectedIds.add(selMapPack[0].intId);
        if (selVRWeapons[0] != null) selectedIds.add(selVRWeapons[0].intId);
        for (ModEntry m : selModels) selectedIds.add(m.intId);
        for (ModEntry o : selOthers) selectedIds.add(o.intId);

        for (int[] group : forMod.requiredIds) {
            boolean alreadyMet = false;
            for (int id : group) {
                if (selectedIds.contains(id)) { alreadyMet = true; break; }
            }
            if (alreadyMet) continue;

            for (int id : group) {
                ModEntry req = findModById(id);
                if (req == null) continue;

                if (!isModInstalled(req)) {
                    downloadAndInstallMod(req, mainColor, () -> {
                        applyCategorySelection(req.category, req,
                                selGameplay, selMapPack, selVRWeapons, selModels, selOthers, true);
                        rebuildUI.run();
                    });
                } else {
                    applyCategorySelection(req.category, req,
                            selGameplay, selMapPack, selVRWeapons, selModels, selOthers, true);
                }
                break;
            }
        }
    }

    private ModEntry findModById(int id) {
        for (ModEntry m : ALL_MODS) {
            if (m.intId == id) return m;
        }
        return null;
    }

    private boolean isModInstalled(ModEntry mod) {
        File dir = new File(Environment.getExternalStorageDirectory(), MODS_DIR);
        if (!dir.exists()) return false;
        for (String file : mod.extractedFiles) {
            if (!new File(dir, file.trim()).exists()) return false;
        }
        return true;
    }

    private boolean writeCommandLineFile(String commandLine) {
        FileOutputStream fos = null;
        try {
            File folder = new File(Environment.getExternalStorageDirectory(), QZD_DIR);
            if (!folder.exists() || !folder.isDirectory()) {
                Toast.makeText(context, "/sdcard/QuestZDoom not found.", Toast.LENGTH_LONG).show();
                return false;
            }
            File noLauncherFile = new File(folder, "no_launcher");

            if (!noLauncherFile.exists()) {
                try {
                    noLauncherFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            File cmdFile = new File(folder, COMMANDLINE_FILE);
            fos = new FileOutputStream(cmdFile, false);
            fos.write(commandLine.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fos != null) { try { fos.close(); } catch (IOException ignored) {} }
        }
    }


    private void downloadAndInstallMod(ModEntry mod, int mainColor, Runnable onComplete) {
        if (mod.downloadUrl == null || mod.downloadUrl.isEmpty()) {
            return;
        }

        ProgressBar progressBar = new ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        TextView progressText = new TextView(context);
        progressText.setText(context.getString(R.string.qzd_connecting));
        progressText.setTextColor(Color.WHITE);
        progressText.setPadding(16, 8, 16, 8);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 36, 40, 36);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{mainColor, AppsAdapter.darkenColor(mainColor, 0.35f)}
        );
        bg.setCornerRadius(48);
        layout.setBackground(bg);

        TextView title = new TextView(context);
        title.setText(mod.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 24);

        layout.addView(title);
        layout.addView(progressBar);
        layout.addView(progressText);

        AlertDialog progress = new AlertDialog.Builder(context)
                .setView(layout)
                .setCancelable(false)
                .create();

        progress.show();

        if (progress.getWindow() != null) {
            progress.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        new AsyncTask<Void, Object, Boolean>() {

            String errorMsg = "";

            @Override
            protected Boolean doInBackground(Void... voids) {
                final int MAX_RETRIES = 1;
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        File modsDir = new File(
                                Environment.getExternalStorageDirectory(), MODS_DIR);
                        modsDir.mkdirs();

                        File target  = new File(modsDir, mod.zipFile);
                        File tmpFile = new File(modsDir, mod.zipFile + ".part");
                        long resumeFrom = tmpFile.exists() ? tmpFile.length() : 0;

                        String finalUrl = resolveModDbUrl(mod.downloadUrl);

                        publishProgress(finalUrl);
                        HttpURLConnection conn = (HttpURLConnection)
                                new URL(finalUrl).openConnection();
                        conn.setConnectTimeout(20_000);
                        conn.setReadTimeout(0);
                        conn.setRequestProperty("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 Chrome/124.0 Safari/537.36");
                        if (resumeFrom > 0)
                            conn.setRequestProperty("Range", "bytes=" + resumeFrom + "-");

                        conn.connect();
                        int status = conn.getResponseCode();

                        if (status != HttpURLConnection.HTTP_OK && status != 206) {
                            errorMsg = "HTTP " + status;
                            conn.disconnect();
                            resumeFrom = 0;
                            tmpFile.delete();
                            continue;
                        }
                        if (status == HttpURLConnection.HTTP_OK && resumeFrom > 0) {
                            resumeFrom = 0;
                            tmpFile.delete();
                        }

                        long contentLength = conn.getContentLengthLong();
                        long totalBytes    = contentLength > 0 ? contentLength + resumeFrom : -1;

                        InputStream      is  = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(tmpFile, resumeFrom > 0);
                        byte[] buf       = new byte[32_768];
                        long   downloaded = resumeFrom;
                        int    len;
                        long   lastUpdate = System.currentTimeMillis();

                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                            downloaded += len;
                            long now = System.currentTimeMillis();
                            if (now - lastUpdate > 300) {
                                lastUpdate = now;
                                if (totalBytes > 0) {
                                    int pct = (int) (downloaded * 100L / totalBytes);
                                    publishProgress(pct,
                                            formatSize(downloaded) + " / " + formatSize(totalBytes));
                                } else {
                                    publishProgress(-1, formatSize(downloaded));
                                }
                            }
                        }
                        fos.flush(); fos.close(); conn.disconnect();

                        if (tmpFile.length() < 10_000) {
                            errorMsg = "File too small (" + tmpFile.length() + " B)";
                            tmpFile.delete();
                            continue;
                        }

                        if (mod.zipFile.toLowerCase().endsWith(".zip")) {
                            boolean keepAsZip = false;
                            for (String extracted : mod.extractedFiles) {
                                if (extracted.toLowerCase().endsWith(".zip")) {
                                    keepAsZip = true; break;
                                }
                            }
                            if (keepAsZip) {
                                tmpFile.renameTo(target);
                            } else {
                                boolean ok = unzipSelectedFiles(tmpFile, modsDir, mod.extractedFiles);
                                tmpFile.delete();
                                if (!ok) { errorMsg = context.getString(R.string.qzd_extractionfailed); continue; }
                            }
                        } else {
                            tmpFile.renameTo(target);
                        }
                        return true;

                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMsg = e.getMessage() != null ? e.getMessage() : context.getString(R.string.qzd_unknownerror);
                    }
                }
                return false;
            }

            @Override
            protected void onProgressUpdate(Object... values) {
                if (values[0] instanceof Integer) {
                    int pct = (Integer) values[0];
                    if (pct >= 0) { progressBar.setIndeterminate(false); progressBar.setProgress(pct); }
                    else            progressBar.setIndeterminate(true);
                }
                if (values.length > 1 && values[1] instanceof String)
                    progressText.setText((String) values[1]);
                else if (values[0] instanceof String) {
                    progressBar.setIndeterminate(true);
                    progressText.setText((String) values[0]);
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progress.dismiss();
                if (success) {
                    Toast.makeText(context, mod.title + " " + context.getString(R.string.qzd_installed), Toast.LENGTH_SHORT).show();
                    if (onComplete != null) onComplete.run();
                } else {
                    Toast.makeText(context,
                            context.getString(R.string.qzd_downloadfailed) + " " + mod.title +
                                    (errorMsg.isEmpty() ? "" : "\n" + errorMsg),
                            Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void startQuestZDoom() {
        try {
            Intent intent = new Intent();
            intent.setClassName(QZD_PACKAGE, QZD_ACTIVITY);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "QuestZDoom not installed.", Toast.LENGTH_LONG).show();
        }
    }

    private void setTitle(String title) {
        TextView tv = dialog.findViewById(R.id.qzd_title);
        if (tv != null) tv.setText(title);
    }

    private void styleButton(Button button, int mainColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(32);
        bg.setColor(Color.argb(140,
                Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor)));
        bg.setStroke(2, Color.argb(180, 255, 255, 255));
        button.setBackground(bg);
    }

    private void setButtonAvailable(Button button) {
        button.setEnabled(true);
        button.setClickable(true);
        button.setAlpha(1.0f);
        if (button.getBackground() != null) button.getBackground().mutate().clearColorFilter();
        button.setTextColor(Color.WHITE);
    }

    private void setButtonUnavailable(Button button) {
        button.setEnabled(false);
        button.setClickable(false);
        button.setAlpha(0.45f);
        if (button.getBackground() != null) {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);
            button.getBackground().mutate().setColorFilter(new ColorMatrixColorFilter(matrix));
        }
        button.setTextColor(Color.LTGRAY);
    }

    private String categoryLabel(ModCategory cat) {
        switch (cat) {
            case GAMEPLAY:  return "── Gameplay Mods ───────────────────";
            case MAPPACK:   return "── Map Packs ───────────────────────";
            case VRWEAPONS: return "── VR Weapons ──────────────────────";
            case MODELS:    return "── Models ──────────────────────────";
            case OTHERS:    return "── Others ──────────────────────────";
        }
        return "";
    }

    private void showHelpDialog(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setPadding(40, 40, 40, 40);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setBackgroundColor(Color.parseColor("#2d2d2d"));

        ScrollView sv = new ScrollView(context);
        sv.setBackgroundColor(Color.parseColor("#2d2d2d"));
        sv.addView(tv);

        AlertDialog d = new AlertDialog.Builder(context)
                .setView(sv)
                .setPositiveButton("OK", null)
                .create();
        d.show();
        if (d.getWindow() != null)
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#2d2d2d")));
    }

    private boolean unzipSelectedFiles(File zipFile, File targetDir, String[] wantedFiles) {
        try (java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {

            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[32_768];

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { zis.closeEntry(); continue; }

                String entryFileName = new File(entry.getName()).getName();

                for (String wanted : wantedFiles) {
                    if (entryFileName.equalsIgnoreCase(wanted.trim())) {
                        File outFile = new File(targetDir, wanted.trim());
                        outFile.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                        }
                        break;
                    }
                }
                zis.closeEntry();
            }

            for (String wanted : wantedFiles) {
                if (!new File(targetDir, wanted.trim()).exists()) {
                    return false;
                }
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String resolveModDbUrl(String startUrl) {
        if (startUrl == null || !startUrl.contains("moddb.com")) return startUrl;
        String cookies = "";
        try {
            HttpURLConnection conn = openConn(startUrl, "", startUrl);
            int status = conn.getResponseCode();
            cookies = collectCookies(conn, "");

            String mirrorUrl = startUrl.replace("/downloads/start/", "/downloads/mirror/");
            if (status == HttpURLConnection.HTTP_OK) {
                String html = readString(conn);
                Pattern p = Pattern.compile("href=\"(/downloads/mirror/\\d+[^\"]*)\"");
                Matcher m = p.matcher(html);
                if (m.find()) mirrorUrl = "https://www.moddb.com" + m.group(1);
            }
            conn.disconnect();

            Thread.sleep(4_000);

            String current = mirrorUrl;
            for (int i = 0; i < 10; i++) {
                conn = openConn(current, cookies, startUrl);
                status = conn.getResponseCode();
                cookies = collectCookies(conn, cookies);

                if (status >= 300 && status < 400) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (loc == null || loc.isEmpty()) return current;
                    if (loc.startsWith("//"))  loc = "https:" + loc;
                    else if (loc.startsWith("/")) loc = "https://www.moddb.com" + loc;
                    current = loc;
                } else if (status == HttpURLConnection.HTTP_OK) {
                    String ct = conn.getContentType();
                    if (ct != null && ct.startsWith("text/html")) {
                        String html = readString(conn);
                        conn.disconnect();
                        Pattern p = Pattern.compile(
                                "href=\"(https?://[^\"]+\\.(?:zip|pk3|pk7|wad|pk4))\"",
                                Pattern.CASE_INSENSITIVE);
                        Matcher m = p.matcher(html);
                        if (m.find()) { current = m.group(1); continue; }
                        return current;
                    }
                    conn.disconnect();
                    return current;
                } else {
                    conn.disconnect();
                    return current;
                }
            }
            return current;
        } catch (Exception e) {
            e.printStackTrace();
            return startUrl;
        }
    }

    private HttpURLConnection openConn(String url, String cookies, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        c.setRequestProperty("Referer", referer);
        if (cookies != null && !cookies.isEmpty()) c.setRequestProperty("Cookie", cookies);
        c.connect();
        return c;
    }

    private String collectCookies(HttpURLConnection conn, String existing) {
        StringBuilder sb = new StringBuilder(existing);
        List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
        if (setCookies == null) return existing;
        for (String raw : setCookies) {
            String part = raw.split(";")[0].trim();
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(part);
        }
        return sb.toString();
    }

    private String readString(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
        return baos.toString("UTF-8");
    }
    private void resetButtonLayout(Button btn) {
        ViewGroup.LayoutParams params = btn.getLayoutParams();

        if (params instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
            lp.topMargin = 10;
            lp.bottomMargin = 0;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.gravity = android.view.Gravity.NO_GRAVITY;
            btn.setLayoutParams(lp);
        }
    }
    private void styleStartButton(Button button) {

        GradientDrawable bg = new GradientDrawable();

        bg.setCornerRadius(40);

        bg.setColor(Color.parseColor("#C46A1A"));

        bg.setStroke(3, Color.parseColor("#FFD27A"));

        button.setBackground(bg);

        button.setTextColor(Color.WHITE);

        button.setTextSize(17f);

        button.setTypeface(Typeface.DEFAULT_BOLD);

        button.setElevation(10f);

        button.setPadding(20, 18, 20, 18);
    }
    private boolean deleteInstalledMod(ModEntry mod) {
        File modsDir = new File(Environment.getExternalStorageDirectory(), MODS_DIR);
        boolean deletedAny = false;

        for (String file : mod.extractedFiles) {
            File target = new File(modsDir, file.trim());
            if (target.exists()) {
                if (target.delete()) {
                    deletedAny = true;
                }
            }
        }

        File zip = new File(modsDir, mod.zipFile);
        if (zip.exists()) {
            if (zip.delete()) {
                deletedAny = true;
            }
        }

        File part = new File(modsDir, mod.zipFile + ".part");
        if (part.exists()) {
            part.delete();
        }

        return deletedAny;
    }
    private String readCommandLineFile() {
        File file = new File(
                Environment.getExternalStorageDirectory(),
                QZD_DIR + "/" + COMMANDLINE_FILE
        );

        if (!file.exists()) return "";

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = new java.io.FileInputStream(file);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();

            return baos.toString("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void preselectModsFromCommandLine(
            MainGameEntry gameEntry,
            ModEntry[] selectedGameplay,
            ModEntry[] selectedMapPack,
            ModEntry[] selectedVRWeapons,
            List<ModEntry> selectedModels,
            List<ModEntry> selectedOthers) {

        String cmd = readCommandLineFile().toLowerCase();

        if (cmd.isEmpty()) return;

        if (!cmd.contains(gameEntry.iwad.toLowerCase())) {
            return;
        }

        for (ModEntry mod : ALL_MODS) {
            if (!isCompatibleWithGame(mod, gameEntry.game)) continue;

            boolean found = false;

            for (String file : mod.extractedFiles) {
                if (cmd.contains(("mods/" + file.trim()).toLowerCase())) {
                    found = true;
                    break;
                }
            }

            if (!found) continue;

            switch (mod.category) {
                case GAMEPLAY:
                    selectedGameplay[0] = mod;
                    break;

                case MAPPACK:
                    selectedMapPack[0] = mod;
                    break;

                case VRWEAPONS:
                    selectedVRWeapons[0] = mod;
                    break;

                case MODELS:
                    if (!selectedModels.contains(mod)) selectedModels.add(mod);
                    break;

                case OTHERS:
                    if (!selectedOthers.contains(mod)) selectedOthers.add(mod);
                    break;
            }
        }
        for (ModEntry unknown : getUnknownInstalledMods()) {
            for (String file : unknown.extractedFiles) {
                if (cmd.contains(("mods/" + file.trim()).toLowerCase())) {
                    if (!containsModFile(selectedOthers, unknown)) {
                        selectedOthers.add(unknown);
                    }
                    break;
                }
            }
        }
    }

    private void updateSelectedModsText(
            TextView textView,
            ModEntry selectedGameplay,
            ModEntry selectedMapPack,
            ModEntry selectedVRWeapons,
            List<ModEntry> selectedModels,
            List<ModEntry> selectedOthers) {

        StringBuilder sb = new StringBuilder();

        if (selectedGameplay != null) sb.append(selectedGameplay.title).append(", ");
        if (selectedMapPack != null) sb.append(selectedMapPack.title).append(", ");
        if (selectedVRWeapons != null) sb.append(selectedVRWeapons.title).append(", ");

        for (ModEntry m : selectedModels) {
            sb.append(m.title).append(", ");
        }

        for (ModEntry o : selectedOthers) {
            sb.append(o.title).append(", ");
        }

        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
            textView.setText(sb.toString());
        } else {
            textView.setText("");
        }
    }
    private void removeModsWithMissingRequirements(
            ModEntry[] selGameplay,
            ModEntry[] selMapPack,
            ModEntry[] selVRWeapons,
            List<ModEntry> selModels,
            List<ModEntry> selOthers) {

        boolean changed;

        do {
            changed = false;

            if (selGameplay[0] != null && !areRequiredMet(selGameplay[0], selGameplay[0], selMapPack[0], selVRWeapons[0], selModels, selOthers)) {
                selGameplay[0] = null;
                changed = true;
            }

            if (selMapPack[0] != null && !areRequiredMet(selMapPack[0], selGameplay[0], selMapPack[0], selVRWeapons[0], selModels, selOthers)) {
                selMapPack[0] = null;
                changed = true;
            }

            if (selVRWeapons[0] != null && !areRequiredMet(selVRWeapons[0], selGameplay[0], selMapPack[0], selVRWeapons[0], selModels, selOthers)) {
                selVRWeapons[0] = null;
                changed = true;
            }

            for (int i = selModels.size() - 1; i >= 0; i--) {
                ModEntry m = selModels.get(i);
                if (!areRequiredMet(m, selGameplay[0], selMapPack[0], selVRWeapons[0], selModels, selOthers)) {
                    selModels.remove(i);
                    changed = true;
                }
            }

            for (int i = selOthers.size() - 1; i >= 0; i--) {
                ModEntry o = selOthers.get(i);
                if (!areRequiredMet(o, selGameplay[0], selMapPack[0], selVRWeapons[0], selModels, selOthers)) {
                    selOthers.remove(i);
                    changed = true;
                }
            }

        } while (changed);
    }
    private List<ModEntry> getUnknownInstalledMods() {
        List<ModEntry> result = new ArrayList<>();

        File dir = new File(Environment.getExternalStorageDirectory(), MODS_DIR);
        File[] files = dir.listFiles();
        if (files == null) return result;

        int id = 9000;

        for (File f : files) {
            if (!f.isFile()) continue;

            String name = f.getName();
            String lower = name.toLowerCase();
            if (isHiddenUnknownMod(name)) {
                continue;
            }

            if (!(lower.endsWith(".pk3") || lower.endsWith(".pk7") ||
                    lower.endsWith(".wad") || lower.endsWith(".zip"))) {
                continue;
            }

            if (isKnownModFile(name)) continue;

            result.add(new ModEntry(
                    id++,
                    name,
                    ModCategory.OTHERS,
                    name,
                    new String[]{name},
                    null,
                    null,
                    null
            ));
        }

        return result;
    }

    private boolean isKnownModFile(String fileName) {
        for (ModEntry mod : ALL_MODS) {
            if (mod.zipFile.equalsIgnoreCase(fileName)) return true;

            for (String extracted : mod.extractedFiles) {
                if (extracted.trim().equalsIgnoreCase(fileName)) return true;
            }
        }
        return false;
    }

    private boolean containsModFile(List<ModEntry> list, ModEntry mod) {
        for (ModEntry m : list) {
            for (String a : m.extractedFiles) {
                for (String b : mod.extractedFiles) {
                    if (a.trim().equalsIgnoreCase(b.trim())) return true;
                }
            }
        }
        return false;
    }

    private void removeModFile(List<ModEntry> list, ModEntry mod) {
        for (int i = list.size() - 1; i >= 0; i--) {
            ModEntry m = list.get(i);

            for (String a : m.extractedFiles) {
                for (String b : mod.extractedFiles) {
                    if (a.trim().equalsIgnoreCase(b.trim())) {
                        list.remove(i);
                        return;
                    }
                }
            }
        }
    }
    private boolean isHiddenUnknownMod(String fileName) {
        for (String hidden : HIDDEN_UNKNOWN_MODS) {
            if (hidden.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }
    private void downloadAndInstallMainGame(MainGameEntry game, int mainColor, Runnable onComplete) {
        if (!game.hasDownload()) return;

        ProgressBar progressBar = new ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);

        TextView progressText = new TextView(context);
        progressText.setText(context.getString(R.string.qzd_connecting));
        progressText.setTextColor(Color.WHITE);
        progressText.setPadding(16, 8, 16, 8);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 36, 40, 36);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{mainColor, AppsAdapter.darkenColor(mainColor, 0.35f)}
        );
        bg.setCornerRadius(48);
        layout.setBackground(bg);

        TextView title = new TextView(context);
        title.setText(game.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 24);

        layout.addView(title);
        layout.addView(progressBar);
        layout.addView(progressText);

        AlertDialog progress = new AlertDialog.Builder(context)
                .setView(layout)
                .setCancelable(false)
                .create();

        progress.show();

        if (progress.getWindow() != null) {
            progress.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        new AsyncTask<Void, Object, Boolean>() {
            String errorMsg = "";

            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    File wadsDir = new File(Environment.getExternalStorageDirectory(), WADS_DIR);
                    wadsDir.mkdirs();

                    File target = new File(wadsDir, game.zipFile);
                    File tmpFile = new File(wadsDir, game.zipFile + ".part");

                    String finalUrl = resolveModDbUrl(game.downloadUrl);

                    publishProgress(finalUrl);

                    HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                    conn.setConnectTimeout(20_000);
                    conn.setReadTimeout(0);
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36");

                    conn.connect();

                    int status = conn.getResponseCode();
                    if (status != HttpURLConnection.HTTP_OK) {
                        errorMsg = "HTTP " + status;
                        conn.disconnect();
                        return false;
                    }

                    long totalBytes = conn.getContentLengthLong();

                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(tmpFile, false);

                    byte[] buf = new byte[32_768];
                    long downloaded = 0;
                    int len;
                    long lastUpdate = System.currentTimeMillis();

                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        downloaded += len;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 300) {
                            lastUpdate = now;
                            if (totalBytes > 0) {
                                int pct = (int) (downloaded * 100L / totalBytes);
                                publishProgress(pct,
                                        formatSize(downloaded) + " / " + formatSize(totalBytes));
                            } else {
                                publishProgress(-1, formatSize(downloaded));
                            }
                        }
                    }

                    fos.flush();
                    fos.close();
                    is.close();
                    conn.disconnect();

                    if (game.zipFile.toLowerCase().endsWith(".zip")) {
                        boolean ok = unzipSelectedFiles(tmpFile, wadsDir, game.requiredFiles);
                        tmpFile.delete();

                        if (!ok) {
                            errorMsg = context.getString(R.string.qzd_extractionfailed);
                            return false;
                        }
                    } else {
                        if (target.exists()) target.delete();
                        if (!tmpFile.renameTo(target)) {
                            errorMsg = context.getString(R.string.qzd_extractionfailed);
                            return false;
                        }
                    }

                    return isMainGameAvailable(game);

                } catch (Exception e) {
                    e.printStackTrace();
                    errorMsg = e.getMessage() != null ? e.getMessage() : context.getString(R.string.qzd_unknownerror);
                    return false;
                }
            }

            @Override
            protected void onProgressUpdate(Object... values) {
                if (values[0] instanceof Integer) {
                    int pct = (Integer) values[0];
                    if (pct >= 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(pct);
                    } else {
                        progressBar.setIndeterminate(true);
                    }
                }

                if (values.length > 1 && values[1] instanceof String) {
                    progressText.setText((String) values[1]);
                } else if (values[0] instanceof String) {
                    progressBar.setIndeterminate(true);
                    progressText.setText((String) values[0]);
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progress.dismiss();

                if (success) {
                    Toast.makeText(context,
                            game.title + " " + context.getString(R.string.qzd_installed),
                            Toast.LENGTH_SHORT).show();

                    if (onComplete != null) onComplete.run();

                } else {
                    Toast.makeText(context,
                            context.getString(R.string.qzd_downloadfailed) + " " + game.title +
                                    (errorMsg.isEmpty() ? "" : "\n" + errorMsg),
                            Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }
}