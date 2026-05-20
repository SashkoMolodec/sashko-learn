package com.sashkolearn.mainagent.domain.model;

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
}
