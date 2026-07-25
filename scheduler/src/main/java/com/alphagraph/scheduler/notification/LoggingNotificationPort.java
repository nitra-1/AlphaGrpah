package com.alphagraph.scheduler.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingNotificationPort implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationPort.class);

    @Override
    public void notify(String subject, String message) {
        log.info("[NOTIFY] {}: {}", subject, message);
    }
}
