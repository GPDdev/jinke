package org.hachile.jinke;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class Store extends SQLiteOpenHelper {
    static final class Draft {
        final long editingId;
        final String mood;
        final String text;

        Draft(long editingId, String mood, String text) {
            this.editingId = editingId;
            this.mood = mood;
            this.text = text;
        }
    }

    Store(Context context) {
        this(context, "jinke.db");
    }

    Store(Context context, String name) {
        super(context, name, null, 1);
    }

    @Override public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, mood TEXT NOT NULL, body TEXT NOT NULL)");
        database.execSQL("CREATE TABLE draft (id INTEGER PRIMARY KEY CHECK(id=1), editing_id INTEGER NOT NULL, mood TEXT NOT NULL, body TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database upgrade");
    }

    Draft draft() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT editing_id,mood,body FROM draft WHERE id=1", null)) {
            return cursor.moveToFirst() ? new Draft(cursor.getLong(0), cursor.getString(1), cursor.getString(2)) : new Draft(0, "随记", "");
        }
    }

    void writeDraft(long editingId, String mood, String text) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("editing_id", editingId);
        values.put("mood", mood);
        values.put("body", text);
        if (getWritableDatabase().insertWithOnConflict("draft", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1) {
            throw new IllegalStateException("Draft write failed");
        }
    }

    long save(long editingId, String mood, String text) {
        if (text.trim().isEmpty()) { throw new IllegalArgumentException("Empty entry"); }
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("updated_at", now);
            values.put("mood", mood);
            values.put("body", text);
            long savedId = editingId;
            if (editingId == 0) {
                values.put("created_at", now);
                savedId = database.insertOrThrow("entries", null, values);
            } else if (database.update("entries", values, "id=?", new String[]{Long.toString(editingId)}) != 1) {
                throw new IllegalStateException("Entry no longer exists");
            }
            database.delete("draft", "id=1", null);
            database.setTransactionSuccessful();
            return savedId;
        } finally {
            database.endTransaction();
        }
    }

    List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,created_at,updated_at,mood,body FROM entries ORDER BY created_at DESC,id DESC", null)) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3), cursor.getString(4)));
            }
        }
        return entries;
    }

    void delete(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{Long.toString(id)});
    }

    static String json(List<Entry> entries) throws JSONException {
        JSONArray items = new JSONArray();
        for (Entry entry : entries) {
            JSONObject item = new JSONObject();
            item.put("id", entry.id);
            item.put("createdAt", entry.createdAt);
            item.put("updatedAt", entry.updatedAt);
            item.put("mood", entry.mood);
            item.put("text", entry.text);
            items.put(item);
        }
        JSONObject backup = new JSONObject();
        backup.put("app", "今刻");
        backup.put("schemaVersion", 1);
        backup.put("timestampUnit", "unix_milliseconds");
        backup.put("exportedAt", System.currentTimeMillis());
        backup.put("entries", items);
        return backup.toString(2);
    }
}
