package com.labo.anapath.report;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.notification.OurVoiceClient;
import com.labo.anapath.common.notification.PhoneNumbers;
import com.labo.anapath.common.notification.SmsSender;
import com.labo.anapath.common.notification.SmsTemplates;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** Début de la plage où un appel vocal automatique est permis (Laravel : 8h). */
    private static final int CALL_WINDOW_START_HOUR = 8;
    /** Fin de la plage où un appel vocal automatique est permis (Laravel : 18h). */
    private static final int CALL_WINDOW_END_HOUR = 18;

    /**
     * Fuseau du laboratoire, celui dans lequel « 8h–18h » a un sens.
     *
     * <p>Explicite, et non l'heure de la machine : le conteneur de production est
     * en UTC, si bien qu'une plage lue sur l'horloge du serveur décalerait tous
     * les appels d'une heure — et en ferait partir un à 19h, heure du patient.</p>
     */
    public static final ZoneId FUSEAU_LABORATOIRE = ZoneId.of("Africa/Porto-Novo");

    private static final String AUDIO_FON ="https://caap.bj/wp-content/uploads/2023/06/RESULTAT-DISPONIBLE-FON-VF.mp3";
    private static final String AUDIO_EN  = "https://caap.bj/wp-content/uploads/2023/06/RESULTAT-DISPONIBLE-ANGLAIS-VF.mp3";
    private static final String AUDIO_FR  = "https://caap.bj/wp-content/uploads/2023/06/RESULTAT-DISPONIBLE-FRANCAIS-VF.mp3";

    private final ReportRepository reportRepository;
    private final AppelByReportRepository appelByReportRepository;
    private final TestOrderRepository testOrderRepository;
    private final LogReportRepository logReportRepository;
    private final SettingAppRepository settingAppRepository;
    private final SmsTemplates smsTemplates;
    private final UserRepository userRepository;
    /** Appels vocaux : FluidPay ne fait que du SMS, la voix reste chez OurVoice. */
    private final OurVoiceClient ourVoiceClient;
    /** SMS : passerelle FluidPay, qui achemine via OurVoice. */
    private final SmsSender smsSender;

    @Override
    @Transactional
    public CallResponseDto callPatient(UUID reportId, UUID userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));

        var patient = report.getTestOrder().getPatient();

        // RÈGLE R7 : mise au format international avant envoi à OurVoice
        String to = PhoneNumbers.toInternational(patient.getTelephone1());

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

        // L'avis est parti : la reprise de 8h n'a plus à s'en occuper.
        report.setPatientNotifiedAt(LocalDateTime.now());
        reportRepository.save(report);

        logAction(reportId, "Appel vocal lancé", userId, report.getBranchId());

        return new CallResponseDto(appelId, reportId, audioUrl);
    }

    @Override
    @Transactional
    public SmsResponseDto sendSms(UUID reportId, UUID userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));

        var patient = report.getTestOrder().getPatient();

        // RÈGLE R7 : mise au format international
        String to = PhoneNumbers.toInternational(patient.getTelephone1());

        // Texte modifiable depuis Paramètres → Communication Mobile ; le message
        // livré prend le relais tant qu'aucun n'y a été saisi.
        //
        // Clé d'idempotence tirée au sort, et non l'identifiant du compte-rendu :
        // ce SMS se renvoie à la demande depuis l'écran de suivi, et une clé stable
        // ferait écarter la relance comme un doublon.
        smsSender.envoyer(to, smsTemplates.smsResultat(report.getBranchId()),
                SmsSender.SOURCE_RESULTAT, UUID.randomUUID());

        // L'avis est parti : la reprise de 8h n'a plus à s'en occuper.
        report.setPatientNotifiedAt(LocalDateTime.now());
        reportRepository.save(report);

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
     *   <li>hors de cette plage, rien n'est envoyé — le compte-rendu garde son
     *       {@link Report#getPatientNotifiedAt()} vide, et
     *       {@link RattrapageAvisPatientJob} reprend l'avis le lendemain à 8h.</li>
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

        LocalTime now = LocalTime.now(FUSEAU_LABORATOIRE);
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
            // L'auteur peut manquer : la notification automatique déclenchée à la
            // validation part parfois sans contexte de sécurité. Journaliser sans
            // auteur vaut mieux que ne pas journaliser — et findById(null) lèverait.
            if (userId != null) {
                userRepository.findById(userId).ifPresent(log::setUser);
            }
            logReportRepository.save(log);
        });
    }
}
