package com.smartrecruit.application.domain;

/** Types d'entrée de l'historique (F-HIST-01) ; les phases 7 et 8 utiliseront les derniers. */
public enum HistoryEventType {
    CREATED, STATUS_CHANGED, NOTE_ADDED, INTERVIEW_SCHEDULED, INTERVIEW_RESCHEDULED, INTERVIEW_CANCELLED,
    INTERVIEW_COMPLETED, AI_ANALYSIS_REQUESTED
}
