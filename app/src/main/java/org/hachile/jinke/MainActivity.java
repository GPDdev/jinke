package org.hachile.jinke;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AtomicFile;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 1;
    private static final int EXPORT_REQUEST = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Runnable draftSave = () -> persistDraft();
    private Store store;
    private EditText editor;
    private RadioGroup moods;
    private TextView status;
    private Button saveButton;
    private Button exportButton;
    private boolean loading;
    private boolean storageReady;
    private long editingId;
    private int voiceStart;
    private int voiceEnd;
    private String pendingExportName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        editor = findViewById(R.id.editor);
        moods = findViewById(R.id.moods);
        status = findViewById(R.id.status);
        saveButton = findViewById(R.id.save);
        exportButton = findViewById(R.id.export);
        setupInsets();
        store = new Store(this);
        try {
            displayDraft(store.draft());
            storageReady = true;
        } catch (RuntimeException failure) {
            moods.check(R.id.mood_none);
            status.setText("存储读取失败：暂不能保存，不会覆盖已有记录");
            showError("本机存储暂不可用。请保留当前文字，检查手机可用空间后重试；不要卸载或清除应用数据。");
        }
        if (state != null) {
            voiceStart = state.getInt("voiceStart");
            voiceEnd = state.getInt("voiceEnd");
            pendingExportName = state.getString("pendingExportName");
        }
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { scheduleDraft(); }
            @Override public void afterTextChanged(Editable text) {}
        });
        moods.setOnCheckedChangeListener((group, checkedId) -> scheduleDraft());
        saveButton.setOnClickListener(view -> saveEntry());
        findViewById(R.id.voice).setOnClickListener(view -> voice());
        findViewById(R.id.history).setOnClickListener(view -> withSavedText(this::history));
        exportButton.setOnClickListener(view -> withSavedText(this::exportMenu));
        editor.requestFocus();
        if (state == null) {
            editor.postDelayed(() -> keyboard().showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT), 180);
        }
    }

    private void setupInsets() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            View root = findViewById(R.id.root);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(dp(20) + bars.left, dp(10) + bars.top, dp(20) + bars.right, dp(12) + Math.max(bars.bottom, ime.bottom));
                int visibility = insets.isVisible(WindowInsets.Type.ime()) ? View.GONE : View.VISIBLE;
                findViewById(R.id.date).setVisibility(visibility);
                findViewById(R.id.subtitle).setVisibility(visibility);
                findViewById(R.id.privacy).setVisibility(visibility);
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private InputMethodManager keyboard() {
        return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    }

    private String mood() {
        RadioButton selected = findViewById(moods.getCheckedRadioButtonId());
        return selected == null ? "随记" : selected.getText().toString();
    }

    private void displayDraft(Store.Draft draft) {
        loading = true;
        editingId = draft.editingId;
        editor.setText(draft.text);
        editor.setSelection(editor.length());
        moods.check(R.id.mood_none);
        for (int index = 0; index < moods.getChildCount(); index++) {
            RadioButton chip = (RadioButton) moods.getChildAt(index);
            if (chip.getText().toString().equals(draft.mood)) { moods.check(chip.getId()); }
        }
        saveButton.setText(editingId == 0 ? "保存此刻" : "保存修改");
        status.setText(draft.text.isEmpty() ? "草稿自动保存在本机" : "已恢复本机草稿");
        loading = false;
    }

    private void scheduleDraft() {
        if (loading || !storageReady) { return; }
        status.setText("正在保存草稿……");
        handler.removeCallbacks(draftSave);
        handler.postDelayed(draftSave, 400);
    }

    private boolean persistDraft() {
        handler.removeCallbacks(draftSave);
        if (!storageReady) { return false; }
        try {
            store.writeDraft(editingId, mood(), editor.getText().toString());
            status.setText(editingId == 0 ? "草稿已保存在本机" : "修改草稿已保存 · 点击保存修改完成更新");
            return true;
        } catch (RuntimeException failure) {
            status.setText("草稿保存失败，请勿关闭，检查可用空间后重试");
            return false;
        }
    }

    private boolean saveEntry() {
        if (!storageReady) {
            showError("本机存储暂不可用，当前文字尚未保存。请勿卸载或清除应用数据。");
            return false;
        }
        String text = editor.getText().toString();
        if (text.trim().isEmpty()) {
            toast("先写下一点想法吧");
            editor.requestFocus();
            return false;
        }
        handler.removeCallbacks(draftSave);
        try {
            store.save(editingId, mood(), text);
            displayDraft(new Store.Draft(0, "随记", ""));
            status.setText("已保存 · 可以继续记录下一刻");
            toast("已保存");
            return true;
        } catch (RuntimeException failure) {
            status.setText("保存失败：当前文字仍在输入框");
            showError("保存失败，文字没有清空。请检查手机可用空间后重试。");
            return false;
        }
    }

    private void withSavedText(Runnable action) {
        if (!storageReady) { showError("本机存储暂不可用，请稍后重试。"); return; }
        if (editor.getText().toString().trim().isEmpty()) { action.run(); return; }
        new AlertDialog.Builder(this).setTitle("先保存当前文字？")
                .setMessage("当前内容将保存为正式记录，再继续操作。草稿不会被丢弃。")
                .setNegativeButton("返回", null)
                .setPositiveButton("保存并继续", (dialog, which) -> { if (saveEntry()) { action.run(); } }).show();
    }

    private void voice() {
        if (!persistDraft()) {
            showError("请先解决草稿保存问题，避免切换语音服务时丢失文字。");
            return;
        }
        if (!getPreferences(MODE_PRIVATE).getBoolean("voiceNotice", false)) {
            new AlertDialog.Builder(this).setTitle("使用系统语音输入")
                    .setMessage("语音由手机已安装的识别服务处理，可能需要联网，是否离线取决于该服务。今刻不录音、不保存音频。若没有系统识别服务，可用输入法里的麦克风。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("继续", (dialog, which) -> {
                        getPreferences(MODE_PRIVATE).edit().putBoolean("voiceNotice", true).apply();
                        startVoice();
                    }).show();
        } else { startVoice(); }
    }

    private void startVoice() {
        voiceStart = Math.max(0, editor.getSelectionStart());
        voiceEnd = Math.max(voiceStart, editor.getSelectionEnd());
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "说说此刻的心情和想法");
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            editor.requestFocus();
            keyboard().showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            new AlertDialog.Builder(this).setTitle("请用输入法语音输入")
                    .setMessage("手机没有可调用的系统识别窗口。请点击 vivo/Jovi 输入法或其他输入法键盘上的麦克风，说话后会直接转成文字。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private void history() {
        keyboard().hideSoftInputFromWindow(editor.getWindowToken(), 0);
        try {
            List<Entry> entries = store.list();
            if (entries.isEmpty()) { toast("还没有记录，写下第一条吧"); return; }
            SimpleDateFormat date = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            String[] titles = new String[entries.size()];
            for (int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                String preview = entry.text.replace('\n', ' ').replace('\r', ' ');
                int length = preview.offsetByCodePoints(0, Math.min(40, preview.codePointCount(0, preview.length())));
                titles[index] = date.format(new Date(entry.createdAt)) + " · " + entry.mood + "\n" + preview.substring(0, length);
            }
            new AlertDialog.Builder(this).setTitle("我的记录 · " + entries.size() + " 条")
                    .setItems(titles, (dialog, which) -> detail(entries.get(which)))
                    .setNegativeButton("返回", null).show();
        } catch (RuntimeException failure) { showError("读取记录失败，请勿清除应用数据，稍后重试。"); }
    }

    private void detail(Entry entry) {
        TextView text = new TextView(this);
        text.setText(entry.text);
        text.setTextSize(18);
        text.setTextIsSelectable(true);
        text.setPadding(dp(24), dp(16), dp(24), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(entry.createdAt));
        new AlertDialog.Builder(this).setTitle(date + " · " + entry.mood).setView(scroll)
                .setNegativeButton("关闭", null)
                .setPositiveButton("编辑", (dialog, which) -> {
                    try {
                        store.writeDraft(entry.id, entry.mood, entry.text);
                        displayDraft(new Store.Draft(entry.id, entry.mood, entry.text));
                        editor.requestFocus();
                        keyboard().showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                    } catch (RuntimeException failure) { showError("无法建立编辑草稿，原记录保持不变。"); }
                })
                .setNeutralButton("删除", (dialog, which) -> new AlertDialog.Builder(this)
                        .setTitle("删除这条记录？").setMessage("删除后不能恢复。重要内容请先导出。")
                        .setNegativeButton("取消", null).setPositiveButton("删除", (confirm, button) -> {
                            try { store.delete(entry.id); toast("已删除"); }
                            catch (RuntimeException failure) { showError("删除失败，请稍后重试。"); }
                        }).show()).show();
    }

    private void exportMenu() {
        try {
            List<Entry> entries = store.list();
            if (entries.isEmpty()) { toast("保存第一条记录后就能导出"); return; }
            new AlertDialog.Builder(this).setTitle("导出全部 " + entries.size() + " 条记录")
                    .setMessage("导出文件是明文，请选择安全的保存位置。")
                    .setPositiveButton("TXT", (dialog, which) -> prepareExport(entries, "txt"))
                    .setNeutralButton("Markdown", (dialog, which) -> prepareExport(entries, "md"))
                    .setNegativeButton("JSON", (dialog, which) -> prepareExport(entries, "json")).show();
        } catch (RuntimeException failure) { showError("读取记录失败，未导出任何内容。"); }
    }

    private void prepareExport(List<Entry> entries, String extension) {
        exportButton.setEnabled(false);
        status.setText("正在准备导出文件……");
        pendingExportName = "export-" + java.util.UUID.randomUUID();
        File snapshotFile = new File(getCacheDir(), pendingExportName);
        files.execute(() -> {
            AtomicFile snapshot = new AtomicFile(snapshotFile);
            FileOutputStream output = null;
            try {
                String payload = extension.equals("json") ? Store.json(entries) : TextExport.render(entries, extension.equals("md"));
                output = snapshot.startWrite();
                output.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                snapshot.finishWrite(output);
                handler.post(() -> {
                    if (isDestroyed()) { snapshot.delete(); return; }
                    exportButton.setEnabled(true);
                    String date = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(extension.equals("json") ? "application/json" : "text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, "今刻-" + date + "." + extension);
                    try { startActivityForResult(intent, EXPORT_REQUEST); }
                    catch (ActivityNotFoundException unavailable) {
                        snapshot.delete();
                        pendingExportName = null;
                        showError("找不到系统文件保存窗口，请启用手机的文件管理/文档服务后重试。");
                    }
                });
            } catch (Exception failure) {
                if (output != null) { snapshot.failWrite(output); }
                snapshot.delete();
                handler.post(() -> {
                    if (isDestroyed()) { return; }
                    exportButton.setEnabled(true);
                    pendingExportName = null;
                    status.setText("导出准备失败，记录仍在本机");
                    showError("无法准备导出文件。请检查手机可用空间后重试。");
                });
            }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VOICE_REQUEST && result == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && results.get(0) != null) {
                int start = Math.min(voiceStart, editor.length());
                int end = Math.max(start, Math.min(voiceEnd, editor.length()));
                editor.getText().replace(start, end, results.get(0));
                editor.setSelection(start + results.get(0).length());
                persistDraft();
            } else { toast("没有识别到文字，可以再试一次"); }
        }
        if (request != EXPORT_REQUEST) { return; }
        File snapshotFile = pendingExportName == null ? null : new File(getCacheDir(), pendingExportName);
        pendingExportName = null;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (snapshotFile != null) { new AtomicFile(snapshotFile).delete(); }
            toast("已取消导出，记录仍在本机");
            return;
        }
        if (snapshotFile == null) { showError("导出任务已中断，记录仍在本机，请重新导出。"); return; }
        android.net.Uri destination = data.getData();
        exportButton.setEnabled(false);
        files.execute(() -> {
            String message;
            try (FileInputStream input = new AtomicFile(snapshotFile).openRead();
                 OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) { throw new java.io.IOException("No output stream"); }
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); }
                output.flush();
                message = "导出完成";
            } catch (Exception failure) {
                message = "导出失败，记录仍在本机。请删除可能不完整的导出文件后重试。";
            } finally { new AtomicFile(snapshotFile).delete(); }
            String resultMessage = message;
            handler.post(() -> {
                if (isDestroyed()) { return; }
                exportButton.setEnabled(true);
                status.setText(resultMessage);
                toast(resultMessage);
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        ((TextView) findViewById(R.id.date)).setText(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date()));
    }

    @Override protected void onPause() {
        persistDraft();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("voiceStart", voiceStart);
        state.putInt("voiceEnd", voiceEnd);
        state.putString("pendingExportName", pendingExportName);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(draftSave);
        files.shutdown();
        store.close();
        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("提示").setMessage(message).setPositiveButton("知道了", null).show();
    }
}
