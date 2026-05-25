package com.sashkolearn.domain.service;

import com.sashkolearn.domain.model.FullSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteSyncOrchestrator {

    private final NoteSyncService noteSyncService;
    private final AttachmentService attachmentService;
    private final LinkService linkService;

    public FullSyncResult performFullSync(Consumer<String> progressCallback) {
        log.info("Starting full notes synchronization");

        try {

            progressCallback.accept("📁 1/5 сканую нотатки...");
            NoteSyncService.SyncResult syncResult = noteSyncService.syncNotes();
            progressCallback.accept(
                String.format("📁 1/5 проскановано: %d файлів (%d нові, %d апдейтнуті, %d видалені)",
                    syncResult.totalFiles(), syncResult.newNotes(), syncResult.updatedNotes(), syncResult.deletedNotes())
            );
            sendWarnings(syncResult.warnings(), "⚠️ Проблеми при синку нотаток", progressCallback);

            progressCallback.accept("🖼️ 2/5 обробляємо картинки...");
            AttachmentService.AttachmentResult attachmentResult = attachmentService.processAttachmentsForNotes(syncResult.changedNoteIds());
            progressCallback.accept(
                String.format("🖼️ 2/5 опрацьовано %d картинок (%d скіпнуто, %d помилок)",
                    attachmentResult.processed(), attachmentResult.skipped(), attachmentResult.errors())
            );
            sendWarnings(attachmentResult.warnings(), "⚠️ Проблеми з картинками", progressCallback);

            progressCallback.accept("💬 3/5 інжектимо описи картинок у нотатки...");
            int injected = attachmentService.reinjectAllDescriptions();
            progressCallback.accept(
                String.format("💬 3/5 описи вставлені у %d картинок", injected)
            );

            progressCallback.accept("🤖 4/5 генеруємо вектори...");
            NoteSyncService.EmbeddingResult embeddingResult = noteSyncService.generateMissingEmbeddings();
            progressCallback.accept(
                String.format("🤖 4/5 згенеровано %d векторів", embeddingResult.count())
            );
            sendWarnings(embeddingResult.warnings(), "⚠️ Проблеми з ембедингами", progressCallback);

            progressCallback.accept("🔗 5/5 будуємо wikilink граф...");
            LinkService.LinkBuildResult linkResult = linkService.buildLinksForChangedNotes(syncResult.changedNoteIds());
            progressCallback.accept(
                String.format("🔗 5/5 оновлені лінки для %d нотаток (%d лінків, %d поламані)",
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
                embeddingResult.count(),
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

    private void sendWarnings(List<String> warnings, String header, Consumer<String> progressCallback) {
        if (warnings == null || warnings.isEmpty()) return;
        progressCallback.accept(header + ":\n" + String.join("\n", warnings));
    }
}
