package com.labo.anapath.report;

import java.util.UUID;

public interface NotificationService {

    CallResponseDto callPatient(UUID reportId, UUID userId);

    SmsResponseDto sendSms(UUID reportId, UUID userId);

    /**
     * Notifie le patient en choisissant seul le canal, comme
     * {@code ReportController::callOrSendSms()} en Laravel.
     *
     * @param reportId identifiant du compte rendu
     * @param userId   utilisateur déclencheur (journalisation)
     * @return le canal réellement utilisé et le message à afficher
     */
    NotifyResponseDto notifyPatient(UUID reportId, UUID userId);

    AppelResponseDto getAppelStatus(UUID reportId);
}
