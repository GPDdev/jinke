package org.hachile.jinke;

public final class Entry {
    public final long id;
    public final long createdAt;
    public final long updatedAt;
    public final String mood;
    public final String text;

    public Entry(long id, long createdAt, long updatedAt, String mood, String text) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.mood = mood;
        this.text = text;
    }
}
