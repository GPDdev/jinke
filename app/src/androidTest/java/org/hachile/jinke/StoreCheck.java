package org.hachile.jinke;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.List;

public final class StoreCheck extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        String database = "check-" + System.nanoTime() + ".db";
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_OK;
        Store store = new Store(context, database);
        try {
            String original = "今天很平静\n\"灵感\" \\ 🌱";
            store.writeDraft(0, "平静", original);
            store.close();
            store = new Store(context, database);
            require(store.draft().text.equals(original), "Draft survives reopen");
            long id = store.save(0, "平静", original);
            require(store.draft().text.isEmpty(), "Save clears draft");
            List<Entry> entries = store.list();
            require(entries.size() == 1 && entries.get(0).text.equals(original), "Saved Unicode and newlines");
            long createdAt = entries.get(0).createdAt;
            store.writeDraft(id, "开心", "修改后的想法");
            store.save(id, "开心", "修改后的想法");
            entries = store.list();
            require(entries.size() == 1 && entries.get(0).createdAt == createdAt, "Edit retains identity and timestamp");
            JSONObject backup = new JSONObject(Store.json(entries));
            require(backup.getJSONArray("entries").getJSONObject(0).getString("text").equals("修改后的想法"), "JSON round trip");
            store.writeDraft(0, "随记", original);
            try { store.save(0, "随记", " \n "); throw new AssertionError("Empty save accepted"); }
            catch (IllegalArgumentException expected) { require(store.draft().text.equals(original), "Empty save keeps draft"); }
            try { store.save(-1, "随记", original); throw new AssertionError("Missing edit accepted"); }
            catch (IllegalStateException expected) { require(store.draft().text.equals(original), "Failed save keeps draft"); }
            store.delete(id);
            require(store.list().isEmpty(), "Delete entry");
            store.save(0, "随记", original);
            backup = new JSONObject(Store.json(store.list()));
            require(backup.getJSONArray("entries").getJSONObject(0).getString("text").equals(original), "JSON escaping");
            result.putString("stream", "JINKE_CHECK_OK: draft recovery, atomic save, edit, delete, JSON, failed-save preservation\n");
        } catch (Throwable failure) {
            result.putString("stream", "JINKE_CHECK_FAILED: " + failure + "\n");
            resultCode = Activity.RESULT_CANCELED;
        } finally {
            store.close();
            context.deleteDatabase(database);
        }
        finish(resultCode, result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
