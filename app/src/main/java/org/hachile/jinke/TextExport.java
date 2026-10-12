package org.hachile.jinke;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class TextExport {
    private TextExport() {}

    public static String render(List<Entry> entries, boolean markdown) {
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT);
        StringBuilder output = new StringBuilder(markdown ? "# 今刻 · 心情与想法\n\n" : "今刻 · 心情与想法\n\n");
        for (Entry entry : entries) {
            output.append(markdown ? "## " : "").append(date.format(new Date(entry.createdAt)))
                    .append(" · ").append(entry.mood).append('\n');
            if (entry.updatedAt != entry.createdAt) {
                output.append("修改于：").append(date.format(new Date(entry.updatedAt))).append('\n');
            }
            output.append('\n').append(entry.text).append("\n\n---\n\n");
        }
        return output.toString();
    }
}
