package com.labo.anapath.appel;

import com.labo.anapath.discussion.Discussion;
import com.labo.anapath.discussion.DiscussionParticipant;
import com.labo.anapath.discussion.DiscussionRepository;
import com.labo.anapath.mobile.MobileDeviceRepository;
import com.labo.anapath.mobile.NotificationsPush;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les règles de l'appel.
 *
 * <p>Ce qui est éprouvé ici tient surtout aux droits : un appel met deux
 * personnes en relation autour d'un dossier médical, et la liaison prouve qui
 * vous êtes sans rien dire de ce à quoi vous avez droit.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceAppelsTest {

    @Mock private DiscussionRepository discussions;
    @Mock private TestOrderRepository demandes;
    @Mock private UserRepository utilisateurs;
    @Mock private JournalAppelRepository journal;
    @Mock private NotificationsPush notifications;
    @Mock private MobileDeviceRepository appareils;

    /** Le vrai registre : c'est lui qui porte l'état, le simuler ne prouverait rien. */
    private RegistreDesAppels registre;
    private ServiceAppels service;

    private final UUID DOSSIER = UUID.randomUUID();
    private final UUID BRANCHE = UUID.randomUUID();
    private final UUID MEDECIN = UUID.randomUUID();
    private final UUID LABO = UUID.randomUUID();
    private final UUID ETRANGER = UUID.randomUUID();

    private Discussion fil;

    @BeforeEach
    void poser() {
        registre = new RegistreDesAppels(new com.fasterxml.jackson.databind.ObjectMapper());
        service = new ServiceAppels(registre, discussions, demandes, utilisateurs,
                journal, notifications, appareils);

        fil = new Discussion(DOSSIER, BRANCHE);
        fil.getParticipants().add(new DiscussionParticipant(fil, MEDECIN, "medecin"));
        fil.getParticipants().add(new DiscussionParticipant(fil, LABO, "technicien"));
        when(discussions.findByTestOrderId(DOSSIER)).thenReturn(Optional.of(fil));

        TestOrder demande = new TestOrder();
        demande.setCode("26-0155");
        when(demandes.findById(DOSSIER)).thenReturn(Optional.of(demande));

        User u = new User();
        u.setLastname("AGBO");
        u.setFirstname("Marc");
        when(utilisateurs.findById(any())).thenReturn(Optional.of(u));
        when(appareils.jetonsDe(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("on ne peut pas appeler sur un fil dont on n'est pas")
    void etrangerNePeutPasAppeler() {
        service.appeler(ETRANGER, BRANCHE, DOSSIER, List.of());

        // Aucun appel ouvert : la liaison prouve qui il est, pas ce à quoi il a
        // droit. Sans ce contrôle, une session valide sonnerait n'importe qui à
        // propos d'un dossier qu'elle n'a pas le droit de voir.
        assertThat(registre.tous()).isEmpty();
    }

    @Test
    @DisplayName("on ne peut pas sonner quelqu'un d'étranger au fil")
    void onNeSonnePasUnEtranger() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of(ETRANGER));

        // La cible est filtrée : il ne reste personne à sonner, donc pas d'appel.
        assertThat(registre.tous()).isEmpty();
    }

    @Test
    @DisplayName("appeler sans nommer personne sonne tout le fil")
    void appelDeGroupe() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());

        assertThat(registre.tous()).hasSize(1);
        Appel appel = registre.tous().iterator().next();
        assertThat(appel.getConviés()).containsExactly(LABO);
        assertThat(appel.getPrésents()).containsKey(MEDECIN);
    }

    @Test
    @DisplayName("un second appel sur le même dossier rejoint le premier")
    void pasDeuxAppelsSurUnDossier() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        UUID premier = registre.tous().iterator().next().getId();

        service.appeler(LABO, BRANCHE, DOSSIER, List.of());

        // Deux appels parallèles couperaient la salle en deux moitiés qui ne
        // s'entendent pas, sans que personne ne comprenne pourquoi.
        assertThat(registre.tous()).hasSize(1);
        assertThat(registre.appel(premier)).isPresent();
        assertThat(registre.appel(premier).get().estPresent(LABO)).isTrue();
    }

    @Test
    @DisplayName("un signal vers quelqu'un hors de l'appel n'est pas relayé")
    void pasDeSignalVersUnTiers() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();

        // LABO n'a pas encore décroché : lui relayer une offre reviendrait à
        // glisser du contenu dans un appel auquel il ne participe pas.
        service.relayer(MEDECIN, appel.getId(), LABO, Map.of("sdp", "offre"));

        assertThat(appel.estPresent(LABO)).isFalse();
    }

    @Test
    @DisplayName("le refus du dernier convié clôt l'appel")
    void refusDuDernier() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();

        service.refuser(LABO, appel.getId());

        // Sans cela, l'appelant reste seul devant une sonnerie sans fin.
        assertThat(registre.tous()).isEmpty();
        ArgumentCaptor<JournalAppel> trace = ArgumentCaptor.forClass(JournalAppel.class);
        verify(journal).save(trace.capture());
        assertThat(trace.getValue().getIssue()).isEqualTo("refusé");
    }

    @Test
    @DisplayName("le dernier qui raccroche clôt l'appel et laisse une trace")
    void leDernierEteint() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();
        service.accepter(LABO, appel.getId());

        service.raccrocher(LABO, appel.getId());

        // Rester seul en ligne n'est pas un appel : c'est un téléphone allumé
        // dans une poche.
        assertThat(registre.tous()).isEmpty();
        verify(journal).save(any());
    }

    @Test
    @DisplayName("la trace dit qui et quand, jamais ce qui s'est dit")
    void laTraceNePorteAucunContenu() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();
        service.accepter(LABO, appel.getId());
        service.raccrocher(LABO, appel.getId());

        ArgumentCaptor<JournalAppel> trace = ArgumentCaptor.forClass(JournalAppel.class);
        verify(journal).save(trace.capture());
        JournalAppel t = trace.getValue();
        assertThat(t.getTestOrderId()).isEqualTo(DOSSIER);
        assertThat(t.getInitiateurId()).isEqualTo(MEDECIN);
        assertThat(t.getDebut()).isNotNull();
        assertThat(t.getSecondes()).isNotNull();
    }

    @Test
    @DisplayName("un convié hors ligne est sonné par notification")
    void leHorsLigneEstSonne() {
        when(notifications.estActif()).thenReturn(true);
        when(appareils.jetonsDe(List.of(LABO))).thenReturn(List.of("jeton"));

        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());

        // Sans cela, l'appel n'atteint que ceux qui regardaient déjà leur écran.
        ArgumentCaptor<Map<String, String>> donnees = ArgumentCaptor.forClass(Map.class);
        verify(notifications).prevenir(eq(List.of("jeton")), any(), any(), donnees.capture());
        assertThat(donnees.getValue()).containsEntry("genre", "appel");
    }

    @Test
    @DisplayName("la maille s'arrête à quatre")
    void limiteDeQuatre() {
        for (int i = 0; i < 6; i++) {
            fil.getParticipants().add(
                    new DiscussionParticipant(fil, UUID.randomUUID(), "technicien"));
        }

        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();

        // Trois conviés plus l'appelant : à six, chaque téléphone tiendrait cinq
        // liaisons montantes et c'est le réseau du plus faible qui déciderait
        // pour tout le monde.
        assertThat(appel.getConviés()).hasSize(Appel.MAXIMUM - 1);
    }
}
