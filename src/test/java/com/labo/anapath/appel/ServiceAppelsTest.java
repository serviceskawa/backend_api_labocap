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
    @Mock private com.labo.anapath.discussion.DiscussionService discussionService;

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
                journal, notifications, appareils, discussionService);
        // Appeler ne suppose plus d'être affecté au dossier, mais d'exercer au
        // soin. L'étranger de ces cas n'est donc plus quelqu'un d'absent du
        // fil — il n'y a plus d'absent — mais quelqu'un du comptoir.
        org.mockito.Mockito.lenient()
                .when(discussionService.exerceUnMetierDuSoin(any())).thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(discussionService.exerceUnMetierDuSoin(MEDECIN)).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(discussionService.exerceUnMetierDuSoin(LABO)).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(utilisateurs.findMetiersDuSoin())
                .thenReturn(List.of(quelquun(MEDECIN), quelquun(LABO)));

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

    /** Une personne du soin, réduite à ce que l'appel lui demande : son identité. */
    private User quelquun(UUID id) {
        User u = new User();
        u.setId(id);
        u.setLastname("AGBO");
        u.setFirstname("Marc");
        return u;
    }

    @Test
    @DisplayName("qui n'exerce pas au soin ne peut pas appeler")
    void etrangerNePeutPasAppeler() {
        service.appeler(ETRANGER, BRANCHE, DOSSIER, List.of());

        // Aucun appel ouvert : la liaison prouve qui il est, pas ce à quoi il a
        // droit. Sans ce contrôle, une session valide sonnerait n'importe qui à
        // propos d'un dossier qu'elle n'a pas le droit de voir.
        assertThat(registre.tous()).isEmpty();
    }

    @Test
    @DisplayName("on ne sonne pas quelqu'un qui n'exerce pas au soin")
    void onNeSonnePasUnEtranger() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of(ETRANGER));

        // La cible est filtrée : il ne reste personne à sonner, donc pas d'appel.
        assertThat(registre.tous()).isEmpty();
    }

    @Test
    @DisplayName("appeler sans nommer personne sonne tout le soin")
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

        // Un réveil, et non une notification : c'est l'application qui doit
        // dessiner l'écran plein et faire sonner. Une notification ordinaire
        // est dessinée par Android, et le code n'est appelé qu'au moment où
        // l'on touche — trop tard pour sonner.
        ArgumentCaptor<Map<String, String>> donnees = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Integer> vie = ArgumentCaptor.forClass(Integer.class);
        verify(notifications).reveiller(eq(List.of("jeton")), donnees.capture(), vie.capture());
        verify(notifications, never()).prevenir(any(), any(), any(), any());

        assertThat(donnees.getValue())
                .containsEntry("genre", "appel")
                .containsKey("appel")
                .containsKey("nomAppelant");

        // Une durée de vie courte : un téléphone rallumé une heure plus tard ne
        // doit pas sonner pour un appel abandonné depuis longtemps.
        assertThat(vie.getValue()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("la maille s'arrête à quatre")
    void limiteDeQuatre() {
        // Sept personnes au soin : c'est l'ordre de grandeur du laboratoire
        // depuis que le fil les montre toutes, et non plus les deux du dossier.
        java.util.List<User> soin = new java.util.ArrayList<>(
                List.of(quelquun(MEDECIN), quelquun(LABO)));
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            org.mockito.Mockito.lenient()
                    .when(discussionService.exerceUnMetierDuSoin(id)).thenReturn(true);
            soin.add(quelquun(id));
        }
        when(utilisateurs.findMetiersDuSoin()).thenReturn(soin);

        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();

        // Trois conviés plus l'appelant : à six, chaque téléphone tiendrait cinq
        // liaisons montantes et c'est le réseau du plus faible qui déciderait
        // pour tout le monde.
        assertThat(appel.getConviés()).hasSize(Appel.MAXIMUM - 1);
    }

    @Test
    @DisplayName("un médecin non affecté peut appeler sur le dossier")
    void leNonAffectePeutAppeler() {
        // Il n'est pas participant enregistré du fil — ni médecin affecté, ni
        // composeur du lot — et c'est précisément le cas qu'on ouvre : le fil
        // le montre, il doit donc pouvoir appeler ce qu'il voit.
        UUID confrere = UUID.randomUUID();
        org.mockito.Mockito.lenient()
                .when(discussionService.exerceUnMetierDuSoin(confrere)).thenReturn(true);
        when(utilisateurs.findMetiersDuSoin())
                .thenReturn(List.of(quelquun(confrere), quelquun(MEDECIN)));

        service.appeler(confrere, BRANCHE, DOSSIER, List.of());

        assertThat(registre.tous()).hasSize(1);
        assertThat(registre.tous().iterator().next().getConviés()).containsExactly(MEDECIN);
    }

    @Test
    @DisplayName("une sélection explicite est plafonnée elle aussi")
    void selectionExpliciteAussiPlafonnee() {
        // Le groupe par défaut était borné, pas la sélection nommée : on
        // pouvait donc monter à sept en les choisissant un à un, et c'est le
        // réseau du plus faible qui aurait décidé pour tout le monde.
        java.util.List<UUID> cibles = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            org.mockito.Mockito.lenient()
                    .when(discussionService.exerceUnMetierDuSoin(id)).thenReturn(true);
            cibles.add(id);
        }

        service.appeler(MEDECIN, BRANCHE, DOSSIER, cibles);

        assertThat(registre.tous().iterator().next().getConviés())
                .hasSize(Appel.MAXIMUM - 1);
    }

    @Test
    @DisplayName("celui qui se connecte réentend la sonnerie qui l'attendait")
    void laSonnerieEstRedite() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        UUID appel = registre.tous().iterator().next().getId();

        // Le cas réel : le téléphone était fermé, la notification l'a réveillé,
        // et sa liaison s'ouvre après que la sonnerie est partie. Sans ce
        // rappel, il touche « Untel vous appelle » et tombe sur son accueil.
        service.rappelerLesSonneries(LABO);

        // Rien à vérifier côté envoi — le registre n'a pas de liaison ouverte
        // en test —, mais l'appel doit être encore là et LABO encore convié.
        assertThat(registre.appel(appel)).isPresent();
        assertThat(registre.appel(appel).get().getConviés()).contains(LABO);
    }

    @Test
    @DisplayName("on ne redit rien à qui a déjà décroché")
    void pasDeRappelAQuiEstDejaLa() {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        UUID appel = registre.tous().iterator().next().getId();
        service.accepter(LABO, appel);

        service.rappelerLesSonneries(LABO);

        // Il est présent : lui refaire sonner l'appel qu'il tient en main
        // rouvrirait l'écran d'appel entrant par-dessus la conversation.
        assertThat(registre.appel(appel).get().estPresent(LABO)).isTrue();
    }

    @Test
    @DisplayName("un appel que personne ne décroche s'éteint tout seul")
    void lAppelSansReponseSEteint() throws Exception {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();
        vieillir(appel, 60);

        service.eteindreLesAppelsSansReponse();

        // Sans cela il resterait ouvert : il ferait sonner le destinataire à
        // chaque reconnexion, et empêcherait d'en lancer un nouveau sur le
        // dossier, puisqu'un appel déjà ouvert se rejoint.
        assertThat(registre.tous()).isEmpty();
        ArgumentCaptor<JournalAppel> trace = ArgumentCaptor.forClass(JournalAppel.class);
        verify(journal).save(trace.capture());
        assertThat(trace.getValue().getIssue()).isEqualTo("sans réponse");
    }

    @Test
    @DisplayName("un appel en cours n'est pas éteint par le balayage")
    void laConversationNEstPasCoupee() throws Exception {
        service.appeler(MEDECIN, BRANCHE, DOSSIER, List.of());
        Appel appel = registre.tous().iterator().next();
        service.accepter(LABO, appel.getId());
        vieillir(appel, 600);

        service.eteindreLesAppelsSansReponse();

        // Deux personnes en ligne depuis dix minutes : le balayage ne regarde
        // que les appels où personne n'a décroché.
        assertThat(registre.tous()).hasSize(1);
    }

    /** Recule le début d'un appel, que rien n'expose en écriture. */
    private static void vieillir(Appel appel, int secondes) throws Exception {
        var champ = Appel.class.getDeclaredField("debut");
        champ.setAccessible(true);
        champ.set(appel, java.time.LocalDateTime.now().minusSeconds(secondes));
    }
}
