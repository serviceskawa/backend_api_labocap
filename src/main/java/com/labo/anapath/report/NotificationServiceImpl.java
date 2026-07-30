package com.labo.anapath.report;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** Début de la plage où un appel vocal automatique est permis (Laravel : 8h). */
    private static final int CALL_WINDOW_START_HOUR = 8;
    /** Fin de la plage où un appel vocal automatique est permis (Laravel : 18h). */
    private static final int CALL_WINDOW_END_HOUR = 18;

    private static final String AUDIO_FON ="https://caap.bj/wp-content/uploads/2023/06/RESULTAT-DISPONIBLE-FON-VF.mp3";
    private static final String AUDIO_EN  = "https://caap.bj/wp-content/uploads/2023/06/RESULTAT-DISPONIBLE-ANGLAIS-VF.mp3";
    private static final String AUDIO_FR  = "https://caap.bj/wp-content/uploads/2023/06/RESULTAT-DISPONIBLE-FRANCAIS-VF.mp3";

    private static final String SMS_BODY =
            "Bonjour c'est le cabinet medical Anathomie pathologique adechinan situé à fifadji " +
            "vos résultats d'analyse sont maintenant disponible vous pouvez venir les recupérer " +
            "à tout moment pendant nos heures d'ouvertures. " +
            "Nous sommes ouvert du Lundi au vendredi de 08h à 17h Merci de votre confiance";

    private final ReportRepository reportRepository;
    private final AppelByReportRepository appelByReportRepository;
    private final TestOrderRepository testOrderRepository;
    private final LogReportRepository logReportRepository;
    private final SettingAppRepository settingAppRepository;
    private final UserRepository userRepository;
    private final OurVoiceClient ourVoiceClient;

    @Override
    @Transactional
    public CallResponseDto callPatient(UUID reportId, UUID userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));

        var patient = report.getTestOrder().getPatient();

        // RÈGLE R7 : préfixer avec "229" avant envoi à OurVoice
        String to = "229" + patient.getTelephone1();

        // RÈGLE R6 : sélection URL audio selon langue du patient
        String audioUrl = switch (patient.getLangue() == null ? "" : patient.getLangue().toLowerCase()) {
            case "fon" -> AUDIO_FON;
            case "anglais" -> AUDIO_EN;
            default -> AUDIO_FR;
        };

        String accessToken = settingAppRepository.findByKey("key_ourvoice")
                .map(SettingApp::getValue)
                .orElseThrow(() -> new InvalidOperationException("Clé API OurVoice non configurée"));
        String endpoint = settingAppRepository.findByKey("link_ourvoice_call")
                .map(SettingApp::getValue)
                .orElseThrow(() -> new InvalidOperationException("Endpoint OurVoice non configuré"));

        String appelId = ourVoiceClient.call(endpoint, accessToken, to, audioUrl);

        // Mettre à jour testOrder.statusAppel
        report.getTestOrder().setStatusAppel(appelId);
        testOrderRepository.save(report.getTestOrder());

        // Créer ou mettre à jour AppelByReport
        AppelByReport appel = appelByReportRepository.findByReportId(reportId)
                .orElse(new AppelByReport());
        appel.setBranchId(report.getBranchId());
        appel.setReport(report);
        appel.setAppelId(appelId);
        appelByReportRepository.save(appel);

        logAction(reportId, "Appel vocal lancé", userId, report.getBranchId());

        return new CallResponseDto(appelId, reportId, audioUrl);
    }

    @Override
    @Transactional
    public SmsResponseDto sendSms(UUID reportId, UUID userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));

        var patient = report.getTestOrder().getPatient();

        // RÈGLE R7 : préfixer avec "229"
        String to = "229" + patient.getTelephone1();

        String accessToken = settingAppRepository.findByKey("key_ourvoice")
                .map(SettingApp::getValue)
                .orElseThrow(() -> new InvalidOperationException("Clé API OurVoice non configurée"));
        String endpoint = settingAppRepository.findByKey("link_ourvoice_sms")
                .map(SettingApp::getValue)
                .orElseThrow(() -> new InvalidOperationException("Endpoint OurVoice SMS non configuré"));

        ourVoiceClient.sms(endpoint, accessToken, to, SMS_BODY);

        logAction(reportId, "SMS envoyé", userId, report.getBranchId());

        return new SmsResponseDto("sent");
    }

    /**
     * Notifie le patient en arbitrant seul entre SMS et appel vocal.
     *
     * <p>Règle reprise de {@code ReportController::callOrSendSms()} :
     * <ol>
     *   <li>si le bon d'examen porte {@code option}, on envoie un SMS ;</li>
     *   <li>sinon on lance un appel vocal, mais seulement entre
     *       {@value #CALL_WINDOW_START_HOUR}h et {@value #CALL_WINDOW_END_HOUR}h ;</li>
     *   <li>hors de cette plage, rien n'est envoyé.</li>
     * </ol>
     * Sans cet arbitrage, un appel automatique pouvait partir en pleine nuit.
     */
    @Override
    @Transactional
    public NotifyResponseDto notifyPatient(UUID reportId, UUID userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));

        boolean smsOption = report.getTestOrder() != null
                && Boolean.TRUE.equals(report.getTestOrder().getOption());

        if (smsOption) {
            sendSms(reportId, userId);
            return new NotifyResponseDto("SMS", reportId, null, "SMS envoyé au patient");
        }

        LocalTime now = LocalTime.now();
        boolean withinCallWindow = !now.isBefore(LocalTime.of(CALL_WINDOW_START_HOUR, 0))
                && !now.isAfter(LocalTime.of(CALL_WINDOW_END_HOUR, 0));
        if (!withinCallWindow) {
            return new NotifyResponseDto("NONE", reportId, null,
                    "Aucun appel : hors de la plage autorisée ("
                            + CALL_WINDOW_START_HOUR + "h–" + CALL_WINDOW_END_HOUR + "h)");
        }

        CallResponseDto call = callPatient(reportId, userId);
        return new NotifyResponseDto("CALL", reportId, call.appelId(), "Appel vocal lancé");
    }

    @Override
    @Transactional(readOnly = true)
    public AppelResponseDto getAppelStatus(UUID reportId) {
        AppelByReport appel = appelByReportRepository.findByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Appel", reportId));
        return new AppelResponseDto(appel.getId(), reportId, appel.getAppelId(), appel.getCreatedAt());
    }

    private void logAction(UUID reportId, String action, UUID userId, UUID branchId) {
        reportRepository.findById(reportId).ifPresent(report -> {
            LogReport log = new LogReport();
            log.setBranchId(branchId);
            log.setReport(report);
            log.setAction(action);
            log.setDescription("Action: " + action + " on report: " + reportId);
            userRepository.findById(userId).ifPresent(log::setUser);
            logReportRepository.save(log);
        });
    }
}
