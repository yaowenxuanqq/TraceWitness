package com.tracewitness.platform.media;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaMaintenance {
    private final MediaService media;

    public MediaMaintenance(MediaService media) {
        this.media = media;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        media.recover();
    }

    @Scheduled(fixedDelayString = "${tracewitness.media.sweep-ms:60000}")
    public void recoverOnSchedule() {
        media.recover();
    }
}
