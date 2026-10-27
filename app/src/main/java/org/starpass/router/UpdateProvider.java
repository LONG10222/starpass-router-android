package org.starpass.router;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants the system installer read-only access to exactly one private APK. */
public final class UpdateProvider extends ContentProvider {
    static File apk(android.content.Context context){return new File(context.getFilesDir(),"updates/update.apk");}
    static Uri uri(android.content.Context context){return Uri.parse("content://"+context.getPackageName()+".updates/update.apk");}
    private void validate(Uri uri){if(!uri.equals(uri(getContext())))throw new IllegalArgumentException("Unknown update URI");}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){validate(uri);return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        validate(uri);if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(apk(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        validate(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns,1);Object[] values=new Object[columns.length];
        for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]="StarPass-Router-update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=apk(getContext()).length();}
        cursor.addRow(values);return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
