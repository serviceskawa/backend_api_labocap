package com.labo.anapath.discussion;

import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderAssignmentDetailRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les règles du fil de discussion.
 *
 * <p>Trois d'entre elles échoueraient en silence. Un message vide accepté
 * remplirait le fil de lignes sans contenu que personne ne saurait retirer. Un
 * type inconnu passerait jusqu'à l'écran, qui ne saurait pas l'afficher. Et un
 * auteur qui n'aurait pas « lu » son propre message verrait un badge s'allumer
 * en écrivant — ce qui ferait douter de tous les autres badges.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscussionServiceTest {

    @Mock private DiscussionRepository discussions;
    @Mock private DiscussionMessageRepository messages;
    @Mock private DiscussionParticipantRepository participants;
    @Mock private DiscussionLectureRepository lectures;
    @Mock private TestOrderRepository testOrderRepository;
    @Mock private TestOrderAssignmentDetailRepository detailRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.labo.anapath.testorder.FileStorageService fichiers;

    @InjectMocks private DiscussionService service;

    private final UUID DEMANDE = UUID.randomUUID();
    private final UUID BRANCHE = UUID.randomUUID();
    private final UUID AUTEUR = UUID.randomUUID();

    @BeforeEach
    void poser() {
        TestOrder demande = new TestOrder();
        demande.setCode("26-0155");
        // L'identifiant compte : `ouvrirOuCreer` cherche le fil par lui, et un
        // nul ferait manquer le stub — le service repartirait alors sur un fil
        // absent, ce qui n'arrive jamais en vrai.
        poserId(demande, DEMANDE);
        when(testOrderRepository.findByIdAndBranchId(DEMANDE, BRANCHE))
                .thenReturn(Optional.of(demande));

        Discussion fil = new Discussion(DEMANDE, BRANCHE);
        when(discussions.findByTestOrderId(DEMANDE)).thenReturn(Optional.of(fil));

        User auteur = new User();
        auteur.setLastname("AGBO");
        auteur.setFirstname("Marc");
        when(userRepository.findById(AUTEUR)).thenReturn(Optional.of(auteur));

        when(detailRepository.findByTestOrderId(DEMANDE)).thenReturn(Optional.empty());
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** Pose l'identifiant d'une entité auditée, que rien n'expose en écriture. */
    private static void poserId(Object entite, UUID id) {
        try {
            var champ = entite.getClass().getSuperclass().getDeclaredField("id");
            champ.setAccessible(true);
            champ.set(entite, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("un message vide ne s'envoie pas")
    void messageVide() {
        for (String contenu : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service.poster(DEMANDE,
                    new DiscussionDtos.NouveauMessage("texte", contenu, null),
                    AUTEUR, BRANCHE))
                    .isInstanceOf(BusinessException.class);
        }
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("un type inconnu est refusé plutôt que servi à l'écran")
    void typeInconnu() {
        assertThatThrownBy(() -> service.poster(DEMANDE,
                new DiscussionDtos.NouveauMessage("video", "bonjour", null),
                AUTEUR, BRANCHE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("video");
    }

    @Test
    @DisplayName("les trois types prévus passent")
    void lesTroisTypes() {
        for (String type : List.of("texte", "photo", "audio")) {
            var dto = service.poster(DEMANDE,
                    new DiscussionDtos.NouveauMessage(type, "quelque chose", null),
                    AUTEUR, BRANCHE);
            assertThat(dto.type()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("l'auteur a lu son propre message")
    void lAuteurALuSonMessage() {
        service.poster(DEMANDE,
                new DiscussionDtos.NouveauMessage("texte", "Lame reçue.", null),
                AUTEUR, BRANCHE);

        // Sans cette ligne, un badge s'allumerait chez celui qui vient
        // d'écrire — et ferait douter de tous les autres.
        ArgumentCaptor<DiscussionLecture> capte =
                ArgumentCaptor.forClass(DiscussionLecture.class);
        verify(lectures).save(capte.capture());
        assertThat(capte.getValue().getUserId()).isEqualTo(AUTEUR);
    }

    @Test
    @DisplayName("le type par défaut est le texte")
    void typeParDefaut() {
        var dto = service.poster(DEMANDE,
                new DiscussionDtos.NouveauMessage(null, "bonjour", null),
                AUTEUR, BRANCHE);

        assertThat(dto.type()).isEqualTo("texte");
    }

    @Test
    @DisplayName("un format non prévu est refusé, quelle que soit son extension")
    void formatRefuse() throws Exception {
        // Liste blanche et non liste noire : un fil de discussion médical n'a
        // pas à devenir un canal de transfert de fichiers quelconques.
        var fichier = new org.springframework.mock.web.MockMultipartFile(
                "file", "note.exe", "application/octet-stream", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.posterFichier(
                DEMANDE, fichier, "audio", null, AUTEUR, BRANCHE))
                .isInstanceOf(BusinessException.class);

        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("un fichier trop lourd est refusé avant d'être écrit")
    void fichierTropLourd() {
        // Refusé avant l'écriture : accepter puis effacer laisserait le disque
        // se remplir le temps du transfert, et c'est justement le cas qu'on
        // veut borner.
        var gros = new org.springframework.mock.web.MockMultipartFile(
                "file", "long.m4a", "audio/mp4", new byte[11 * 1024 * 1024]);

        assertThatThrownBy(() -> service.posterFichier(
                DEMANDE, gros, "audio", null, AUTEUR, BRANCHE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10 Mo");

        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("une note vocale acceptée garde le nom du fichier, pas son URL")
    void laNoteVocalePasse() throws Exception {
        var note = new org.springframework.mock.web.MockMultipartFile(
                "file", "note.m4a", "audio/mp4", new byte[] {1, 2, 3});
        when(fichiers.store(any(), any())).thenReturn("discussions/abc.m4a");
        when(fichiers.getUrl("discussions/abc.m4a"))
                .thenReturn("/api/v1/files/discussions/abc.m4a");

        var dto = service.posterFichier(DEMANDE, note, "audio", null, AUTEUR, BRANCHE);

        // En base le nom ; à l'écran l'URL. Stocker l'URL la figerait — un
        // changement de préfixe casserait tous les messages anciens.
        ArgumentCaptor<DiscussionMessage> capte =
                ArgumentCaptor.forClass(DiscussionMessage.class);
        verify(messages).save(capte.capture());
        assertThat(capte.getValue().getContent()).isEqualTo("discussions/abc.m4a");
        assertThat(dto.contenu()).isEqualTo("/api/v1/files/discussions/abc.m4a");
    }
}
