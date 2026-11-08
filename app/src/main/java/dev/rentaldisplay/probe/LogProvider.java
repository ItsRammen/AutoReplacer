package dev.rentaldisplay.probe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Read-only access to deliberately shared snapshot files; never exposes the live log. */
public final class LogProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File resolve(Uri uri) throws FileNotFoundException {
        List<String> path = uri.getPathSegments();
        if (getContext() == null || !(getContext().getPackageName() + ".logs").equals(uri.getAuthority())
                || path.size() != 2 || !"exports".equals(path.get(0))) throw new FileNotFoundException("Invalid log URI");
        try {
            File file = ExportFiles.resolve(new File(getContext().getCacheDir(),"log-exports"),path.get(1));
            if (!file.isFile()) throw new FileNotFoundException("Log snapshot unavailable");
            return file;
        } catch (java.io.IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Log snapshots are read-only");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i=0;i<columns.length;i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException e) { throw new IllegalArgumentException(e.getMessage()); }
    }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException("Read-only"); }
}
