package com.campusask.campusask.rag;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class RagMaintenance {
    private final RagStore store;
    public RagMaintenance(RagStore store) { this.store=store; }
    @Scheduled(initialDelayString="${rag.retention.initial-delay-ms:60000}",fixedDelayString="${rag.retention.interval-ms:3600000}")
    public void expire() { store.expire(); }
}
