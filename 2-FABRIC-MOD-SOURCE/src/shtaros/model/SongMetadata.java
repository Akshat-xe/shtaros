package shtaros.model;

public record SongMetadata(
        String id,
        String title,
        String author,
        String durationStr,
        int durationSeconds
) {}
