/*
 * Copyright (c) 2016 Qiscus.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qiscus.sdk.util;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.qiscus.sdk.Qiscus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import kotlin.jvm.internal.Intrinsics;

public final class QiscusFileUtil {
    public static final String FILES_PATH = Qiscus.getAppsName() + File.separator + "Files";
    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private QiscusFileUtil() {

    }

    public static File from(Uri uri) throws IOException {
        InputStream inputStream = Qiscus.getApps().getContentResolver().openInputStream(uri);
        String fileName = getFileName(uri);
        String[] splitName = splitFileName(fileName);
        File tempFile = File.createTempFile(splitName[0], splitName[1]);
        tempFile = rename(tempFile, fileName);
        tempFile.deleteOnExit();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (inputStream != null) {
            copy(inputStream, out);
            inputStream.close();
        }

        if (out != null) {
            out.close();
        }
        return tempFile;
    }

    public static File from(InputStream inputStream, String fileName) throws IOException {
        File file = new File(generateFilePath(fileName));
        file = rename(file, fileName);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        copy(inputStream, out);

        if (out != null) {
            out.close();
        }
        return file;
    }

    public static String[] splitFileName(String fileName) {
        String name = fileName;
        String extension = "";
        int i = fileName.lastIndexOf('.');
        if (i != -1) {
            name = fileName.substring(0, i);
            extension = fileName.substring(i);
        }

        return new String[]{name, extension};
    }

    public static String getFileName(Uri uri) {
        String result;
        result = getPathFromUri(Qiscus.getApps(),uri);
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf(File.separator);
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * Get Path from URI
     *
     * @param context context
     * @param uri     uri
     * @return String
     */
    @Nullable
    public static String getPathFromUri(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        if (isGoogleDriveUri(uri)){
            return getDriveFilePath(uri, context);
        }
        // DocumentProvider
        else if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    @Nullable
    private static String getDataColumn(Context context, Uri uri, String selection,
                                        String[] selectionArgs) {

        final String column = "_data";
        final String[] projection = {
                column
        };

        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        }
        return null;
    }


    @NonNull
    private static String getDriveFilePath(Uri uri, @NonNull Context context) {
        Cursor returnCursor = context.getContentResolver().query(uri, (String[])null, (String)null, (String[])null, (String)null);
        Integer nameIndex = returnCursor != null ? returnCursor.getColumnIndex("_display_name") : null;
        if (returnCursor != null) {
            returnCursor.moveToFirst();
        }

        String tempName;
        if (returnCursor != null) {
            Intrinsics.checkNotNull(nameIndex);
            tempName = returnCursor.getString(nameIndex);
        } else {
            tempName = null;
        }

        String name = tempName;
        File file = new File(context.getCacheDir(), name);

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(file);
            int maxBufferSize = 1048576;
            int bytesAvailable = inputStream.available();
            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
            byte[] buffers = new byte[bufferSize];

            while(true) {
                int read = inputStream.read(buffers);
                if (read == -1) {
                    inputStream.close();
                    outputStream.close();
                    break;
                }
                outputStream.write(buffers, 0, read);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file.getPath();
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(@NonNull Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(@NonNull Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(@NonNull Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private static boolean isGooglePhotosUri(@NonNull Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    private static boolean isGoogleDriveUri(@NonNull Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) || "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
    }

    public static String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = Qiscus.getApps().getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {
            return contentUri.getPath();
        } else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            String realPath = cursor.getString(index);
            cursor.close();
            return realPath;
        }
    }

    public static File saveFile(File file) {
        String path = generateFilePath(file.getName());
        File newFile = new File(path);
        try {
            FileInputStream in = new FileInputStream(file);
            FileOutputStream out = new FileOutputStream(newFile);
            copy(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return newFile;
    }

    public static String generateFilePath(String fileName) {
        String[] fileNameSplit = splitFileName(fileName);
        return generateFilePath(fileName, fileNameSplit[1]);
    }

    public static String generateFilePath(String fileName, String extension) {
        File file = new File(Environment.getExternalStorageDirectory().getPath(),
                QiscusImageUtil.isImage(fileName) ? QiscusImageUtil.IMAGE_PATH : FILES_PATH);

        if (!file.exists()) {
            file.mkdirs();
        }

        int index = 0;
        String directory = file.getAbsolutePath() + File.separator;
        String[] fileNameSplit = splitFileName(fileName);
        while (true) {
            File newFile;
            if (index == 0) {
                newFile = new File(directory + fileNameSplit[0] + extension);
            } else {
                newFile = new File(directory + fileNameSplit[0] + "-" + index + extension);
            }
            if (!newFile.exists()) {
                return newFile.getAbsolutePath();
            }
            index++;
        }
    }

    public static File rename(File file, String newName) {
        File newFile = new File(file.getParent(), newName);
        if (!newFile.equals(file)) {
            if (newFile.exists()) {
                newFile.delete();
            }
            file.renameTo(newFile);
        }
        return newFile;
    }

    public static boolean isContains(String path) {
        File file = new File(path);
        return file.exists();
    }

    public static String getExtension(File file) {
        return getExtension(file.getPath());
    }

    public static String getExtension(String fileName) {
        int lastDotPosition = fileName.lastIndexOf('.');
        String ext = fileName.substring(lastDotPosition + 1);
        ext = ext.replace("_", "");
        return ext.trim().toLowerCase();
    }

    private static int copy(InputStream input, OutputStream output) throws IOException {
        long count = copyLarge(input, output);
        if (count > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) count;
    }

    private static long copyLarge(InputStream input, OutputStream output) throws IOException {
        return copyLarge(input, output, new byte[DEFAULT_BUFFER_SIZE]);
    }

    private static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {
        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static String createTimestampFileName(String extension) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return timeStamp + "." + extension;
    }

    public static void notifySystem(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        Qiscus.getApps().sendBroadcast(mediaScanIntent);
    }
}
