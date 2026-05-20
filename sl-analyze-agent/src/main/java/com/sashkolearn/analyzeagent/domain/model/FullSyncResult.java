package com.sashkolearn.analyzeagent.domain.model;

public record FullSyncResult(
    SyncStats syncStats,
    int embeddingsGenerated,
    LinkStats linkStats
) {
    public record SyncStats(
        int totalFiles,
        int newNotes,
        int updatedNotes,
        int skippedNotes,
        int deletedNotes
    ) {
    }

    public record LinkStats(
        int totalNotes,
        int totalLinks,
        int brokenLinks
    ) {
    }

    public String toMessage() {
        return String.format(
            "=== Full Sync Completed ===\n\n" +
            "Files: %d total (%d new, %d updated, %d deleted)\n" +
            "Embeddings generated: %d\n" +
            "Links: %d created (%d broken wikilinks)",
            syncStats.totalFiles(),
            syncStats.newNotes(),
            syncStats.updatedNotes(),
            syncStats.deletedNotes(),
            embeddingsGenerated,
            linkStats.totalLinks(),
            linkStats.brokenLinks()
        );
    }
}
