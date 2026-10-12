package org.hachile.jinke;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

public final class TextExportCheck {
    public static void main(String[] arguments) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        Entry entry = new Entry(1, 0, 1000, "平静", "第一行\n\"第二行\" 🌱\n# 原文");
        String plain = TextExport.render(Arrays.asList(entry), false);
        String markdown = TextExport.render(Arrays.asList(entry), true);
        if (!plain.contains("1970-01-01 08:00:00 +08:00 · 平静") || !plain.contains(entry.text)
                || !plain.contains("修改于：1970-01-01 08:00:01 +08:00")
                || !markdown.startsWith("# 今刻") || !markdown.contains("## 1970-01-01")
                || !TextExport.render(Collections.emptyList(), false).equals("今刻 · 心情与想法\n\n")) {
            throw new AssertionError("Text export check failed");
        }
        System.out.println("TEXT_EXPORT_CHECK_OK: Unicode, multiline, timestamps, Markdown, empty export");
    }
}
