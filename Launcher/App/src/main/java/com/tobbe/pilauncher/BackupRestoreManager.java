package com.tobbe.pilauncher;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupRestoreManager {

    private static final String META_FILE = "backup.meta";
    private static final String BACKUP_VERSION = "1";
    private static final int BUFFER_SIZE = 8192;

    public static void backupAppData(Context context, Uri uri) throws IOException {

        File dataDir = context.getDataDir();

        try (
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))
        ) {
            writeMetaFile(context, zos);
            zipFolder(dataDir, dataDir, zos);
        }
    }

    private static void writeMetaFile(Context context, ZipOutputStream zos) throws IOException {

        ZipEntry metaEntry = new ZipEntry(META_FILE);
        zos.putNextEntry(metaEntry);

        String content =
                "app=" + context.getPackageName() + "\n" +
                        "version=" + BACKUP_VERSION + "\n";

        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void zipFolder(File rootDir, File currentDir, ZipOutputStream zos) throws IOException {

        File[] files = currentDir.listFiles();

        if (files == null) return;

        byte[] buffer = new byte[BUFFER_SIZE];

        for (File file : files) {

            String relativePath =
                    file.getAbsolutePath()
                            .substring(rootDir.getAbsolutePath().length() + 1);

            if (file.isDirectory()) {

                zipFolder(rootDir, file, zos);

            } else {

                try (FileInputStream fis = new FileInputStream(file)) {

                    ZipEntry entry = new ZipEntry(relativePath);
                    zos.putNextEntry(entry);

                    int len;

                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }

                    zos.closeEntry();
                }
            }
        }
    }

    public static void restoreAppData(Context context, Uri uri) throws IOException {

        validateBackupFile(context, uri);
        restoreBackupFile(context, uri);
    }

    private static void validateBackupFile(Context context, Uri uri) throws IOException {

        boolean validMetaFound = false;

        try (
                InputStream is = context.getContentResolver().openInputStream(uri);
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))
        ) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {

                String entryName = entry.getName();

                validateZipEntryName(context, entryName);

                if (META_FILE.equals(entryName)) {

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();

                    int len;

                    while ((len = zis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }

                    String meta = bos.toString(StandardCharsets.UTF_8.name());

                    boolean correctApp =
                            meta.contains("app=" + context.getPackageName());

                    boolean correctVersion =
                            meta.contains("version=" + BACKUP_VERSION);

                    if (correctApp && correctVersion) {
                        validMetaFound = true;
                    }
                }

                zis.closeEntry();
            }
        }

        if (!validMetaFound) {
            throw new SecurityException(context.getString(R.string.backup_invalid));
        }
    }

    private static void restoreBackupFile(Context context, Uri uri) throws IOException {

        File dataDir = context.getDataDir();

        String canonicalTarget =
                dataDir.getCanonicalPath() + File.separator;

        try (
                InputStream is = context.getContentResolver().openInputStream(uri);
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))
        ) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {

                String entryName = entry.getName();

                validateZipEntryName(context, entryName);

                if (META_FILE.equals(entryName)) {
                    zis.closeEntry();
                    continue;
                }

                File outFile = new File(dataDir, entryName);
                String canonicalOut = outFile.getCanonicalPath();

                if (!canonicalOut.startsWith(canonicalTarget)) {
                    throw new SecurityException(context.getString(R.string.backup_traversal));
                }

                if (entry.isDirectory()) {

                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException(context.getString(R.string.backup_couldnot) + outFile);
                    }

                } else {

                    File parent = outFile.getParentFile();

                    if (parent != null && !parent.exists()) {
                        if (!parent.mkdirs()) {
                            throw new IOException(context.getString(R.string.backup_couldnot) + parent);
                        }
                    }

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {

                        int len;

                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private static void validateZipEntryName(Context context, String entryName) {

        if (entryName == null ||
                entryName.trim().isEmpty() ||
                entryName.startsWith("/") ||
                entryName.startsWith("\\") ||
                entryName.contains("..") ||
                entryName.contains(":")) {

            throw new SecurityException(context.getString(R.string.backup_invalidzip));
        }
    }
}
