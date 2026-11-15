package com.campusask.campusask.rag;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class RagMaintenance {
    private final RagStore store;
    private final MilvusIndex milvus;
    public RagMaintenance(RagStore store,MilvusIndex milvus) { this.store=store;this.milvus=milvus; }
    @Scheduled(initialDelayString="${rag.retention.initial-delay-ms:60000}",fixedDelayString="${rag.retention.interval-ms:3600000}")
    public void expire() { store.expire(); }
    @Scheduled(initialDelayString="${rag.milvus.initial-delay-ms:10000}",fixedDelayString="${rag.milvus.sync-interval-ms:1000}")
    public void syncVectors() {
        if(!milvus.enabled()) return;
        try { milvus.syncBatch(); }
        catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(RagMaintenance.class)
                .warn("milvus_sync_failed type={}",ex.getClass().getSimpleName()); }
    }
}
