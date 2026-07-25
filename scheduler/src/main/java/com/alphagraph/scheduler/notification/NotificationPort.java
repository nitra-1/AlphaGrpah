package com.alphagraph.scheduler.notification;

/**
 * Structural hook for the Notify stage (docs/002_Engine_Architecture.md §6). Phase 0 ships only
 * a logging implementation; email/webhook implementations arrive when there's something real to
 * notify about.
 */
public interface NotificationPort {

    void notify(String subject, String message);
}
