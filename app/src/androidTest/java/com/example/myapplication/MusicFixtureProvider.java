package com.example.myapplication;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Synthetic document-tree queries. Java keeps this separate test-APK process independent
 * of the Kotlin runtime, which Android packages in the target application.
 */
public class MusicFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        });
        switch (DocumentsContract.getDocumentId(uri)) {
            case "root":
                cursor.addRow(new Object[] {"album", "Album", DocumentsContract.Document.MIME_TYPE_DIR});
                cursor.addRow(new Object[] {"song", "B song.wav", "audio/wav"});
                cursor.addRow(new Object[] {"cover", "cover.jpg", "image/jpeg"});
                break;
            case "album":
                cursor.addRow(new Object[] {"nested", "A nested.WAV", "application/octet-stream"});
                cursor.addRow(new Object[] {"root", "Cycle", DocumentsContract.Document.MIME_TYPE_DIR});
                break;
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only fixture");
        File file = new File(getContext().getCacheDir(), "music-fixture.wav");
        if (!file.exists()) {
            int pcmBytes = 16000;
            ByteBuffer wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcmBytes);
            wav.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
            wav.putInt(16).putShort((short) 1).putShort((short) 1);
            wav.putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
            wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcmBytes);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(wav.array()); // One second of silence.
            } catch (IOException error) {
                throw new FileNotFoundException(error.toString());
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
