package com.sashkolearn.analyzeagent.domain.service;

import com.sashkolearn.analyzeagent.domain.model.FullSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteSyncOrchestrator {

    private final NoteSyncService noteSyncService;
    private final AttachmentService attachmentService;
    private final LinkService linkService;

    /**
     * Performs full synchronization:
     * 1. Syncs note files
     * 2. Processes image attachments
     * 3. Generates embeddings (enriched with attachment descriptions)
     * 4. Builds graph links
     *
     * @param progressCallback callback for sending progress to Telegram
     * @return synchronization result
     */
    public FullSyncResult performFullSync(Consumer<String> progressCallback) {
        log.info("Starting full notes synchronization");

        try {

            progressCallback.accept("📁 1/4 сканую нотатки...");
            NoteSyncService.SyncResult syncResult = noteSyncService.syncNotes();
            progressCallback.accept(
                String.format("📁 1/4 проскановано: %d файлів (%d нові, %d апдейтнуті, %d видалені)",
                    syncResult.totalFiles(), syncResult.newNotes(), syncResult.updatedNotes(), syncResult.deletedNotes())
            );

            progressCallback.accept("🖼️ 2/4 обробляємо картинки...");
            AttachmentService.AttachmentResult attachmentResult = attachmentService.processAttachmentsForNotes(syncResult.changedNoteIds());
            progressCallback.accept(
                String.format("🖼️ 2/4 опрацьовано %d картинок (%d скіпнуто, %d помилок)",
                    attachmentResult.processed(), attachmentResult.skipped(), attachmentResult.errors())
            );

            progressCallback.accept("🤖 3/4 генеруємо вектори...");
            int embeddingsGenerated = noteSyncService.generateMissingEmbeddings();
            progressCallback.accept(
                String.format("🤖 3/4 згенеровано %d векторів", embeddingsGenerated)
            );

            progressCallback.accept("🔗 4/4 будуємо wikilink граф...");
            LinkService.LinkBuildResult linkResult = linkService.buildLinksForChangedNotes(syncResult.changedNoteIds());
            progressCallback.accept(
                String.format("🔗 4/4 оновлені лінки для %d нотаток (%d лінків, %d поламані)",
                    syncResult.changedNoteIds().size(), linkResult.totalLinks(), linkResult.brokenLinks())
            );

            FullSyncResult result = new FullSyncResult(
                new FullSyncResult.SyncStats(
                    syncResult.totalFiles(),
                    syncResult.newNotes(),
                    syncResult.updatedNotes(),
                    syncResult.skippedNotes(),
                    syncResult.deletedNotes()
                ),
                embeddingsGenerated,
                new FullSyncResult.LinkStats(
                    linkResult.totalNotes(),
                    linkResult.totalLinks(),
                    linkResult.brokenLinks()
                )
            );

            log.info("Full sync completed successfully");
            return result;

        } catch (Exception e) {
            log.error("Full sync failed", e);
            progressCallback.accept("❌ Error: " + e.getMessage());
            throw new RuntimeException("Full sync failed: " + e.getMessage(), e);
        }
    }
}
