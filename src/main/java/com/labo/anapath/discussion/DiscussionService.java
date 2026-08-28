package com.labo.anapath.discussion;

import com.labo.anapath.common.NomComplet;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderAssignmentDetailRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.labo.anapath.discussion.DiscussionDtos.*;

/**
 * Le fil de discussion d'un dossier.
 *
 * <h2>Qui participe</h2>
 *
 * <p>Le fil naît à la première ouverture, avec les personnes que le dossier
 * désigne déjà : le médecin à qui la demande est affectée, et le technicien qui
 * a composé le lot. Quiconque écrit ensuite y entre à son tour.</p>
 *
 * <p>Taguer quelqu'un l'ajoute au fil. C'est ainsi que se traduit la règle de
 * la maquette — « un médecin tagué sur un cas qui n'est pas le sien reçoit ce
 * message » : plutôt que de rejouer la règle à chaque lecture, on l'inscrit une
 * fois, au moment où elle s'applique.</p>
 *
 * <h2>Ce que le fil ne fait pas</h2>
 *
 * <p>Il ne donne accès à rien. Lire un fil suppose déjà le droit de consulter
 * la demande ; ce service ne sert que la conversation, jamais le contenu
 * médical.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscussionService {

    private final DiscussionRepository discussions;
    private final DiscussionMessageRepository messages;
    private final DiscussionParticipantRepository participants;
    private final DiscussionLectureRepository lectures;
    private final TestOrderRepository testOrderRepository;
    private final TestOrderAssignmentDetailRepository detailRepository;
    private final UserRepository userRepository;
    private final com.labo.anapath.testorder.FileStorageService fichiers;
    private final com.labo.anapath.mobile.NotificationsPush notifications;
    private final com.labo.anapath.mobile.MobileDeviceRepository appareils;


    /**
     * Au-delà, ce n'est plus une note dictée entre deux portes.
     *
     * <p>Dix mégaoctets laissent largement plusieurs minutes de parole
     * compressée, et bornent ce qu'un fil peut coûter au disque comme au forfait
     * de celui qui le consulte.</p>
     */
    private static final long TAILLE_MAX = 10L * 1024 * 1024;

    /**
     * Le fil d'un dossier, créé s'il n'existe pas.
     *
     * <p>Créé et non refusé : la maquette veut qu'un médecin puisse écrire le
     * premier, y compris quand rien n'a encore été dit.</p>
     */
    @Transactional
    public FilDto fil(UUID testOrderId, UUID lecteurId, UUID branchId) {
        TestOrder demande = testOrderRepository.findByIdAndBranchId(testOrderId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", testOrderId));
        Discussion fil = ouvrirOuCreer(demande, lecteurId, branchId);

        List<DiscussionMessage> liste =
                messages.findByDiscussionIdOrderByCreatedAtAsc(fil.getId());
        Map<UUID, User> gens = chargerLesGens(fil, liste);
        Set<UUID> nonLus = Set.copyOf(lectures.nonLusDuFil(fil.getId(), lecteurId));

        return new FilDto(
                fil.getId(),
                demande.getId(),
                demande.getCode(),
                fil.getParticipants().stream()
                        .map(p -> new ParticipantDto(p.getUserId(),
                                nomDe(gens.get(p.getUserId())), p.getRole()))
                        .toList(),
                liste.stream().map(m -> versDto(m, gens, fil, nonLus)).toList());
    }

    /** Poste un message, et fait entrer au fil qui écrit — et qui est tagué. */
    @Transactional
    public MessageDto poster(UUID testOrderId, NouveauMessage nouveau,
                             UUID auteurId, UUID branchId) {
        String contenu = nouveau.contenu() == null ? "" : nouveau.contenu().trim();
        if (contenu.isEmpty()) {
            throw new BusinessException("Un message vide ne s'envoie pas.");
        }
        String type = nouveau.type() == null ? DiscussionMessage.TEXTE : nouveau.type().trim();
        if (!List.of(DiscussionMessage.TEXTE, DiscussionMessage.PHOTO,
                     DiscussionMessage.AUDIO).contains(type)) {
            throw new BusinessException("Type de message inconnu : « " + type + " ».");
        }

        TestOrder demande = testOrderRepository.findByIdAndBranchId(testOrderId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", testOrderId));
        Discussion fil = ouvrirOuCreer(demande, auteurId, branchId);

        // Taguer quelqu'un le fait entrer au fil : c'est la traduction de la
        // règle d'adressage, inscrite une fois plutôt que rejouée à chaque
        // lecture. Sans cela, le tagué ne verrait jamais le message qui le
        // nomme.
        if (nouveau.taggedUserId() != null) {
            userRepository.findById(nouveau.taggedUserId())
                    .ifPresent(u -> ajouterAuFil(fil, u.getId(), roleDe(u)));
        }

        DiscussionMessage message = messages.save(new DiscussionMessage(
                fil, auteurId, type, contenu, nouveau.taggedUserId()));

        // Son auteur l'a lu par définition. L'omettre ferait apparaître un badge
        // à celui qui vient d'écrire.
        lectures.save(new DiscussionLecture(message.getId(), auteurId));

        log.info("Message posté : dossier={} auteur={} type={}", testOrderId, auteurId, type);
        Map<UUID, User> gens = chargerLesGens(fil, List.of(message));
        prevenir(fil, demande, message, gens);
        return versDto(message, gens, fil, Set.of());
    }

    /**
     * Poste une note vocale ou une photo.
     *
     * <p>Le fichier est rangé comme les clichés d'un bon d'examen — même
     * stockage, même chiffrement au repos, même point d'entrée protégé pour le
     * relire. Le message ne garde que son nom ; l'URL se compose à la
     * lecture.</p>
     */
    @Transactional
    public MessageDto posterFichier(UUID testOrderId,
                                    org.springframework.web.multipart.MultipartFile fichier,
                                    String type, UUID taggedUserId,
                                    UUID auteurId, UUID branchId) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new BusinessException(
                    "Ce fichier dépasse 10 Mo. Une note vocale plus courte passera.");
        }

        String voulu = type == null ? DiscussionMessage.AUDIO : type.trim();
        // Le format se lit dans le fichier, pas dans ce que le client annonce.
        // Les clients mentent — `http.MultipartFile` déclare
        // « application/octet-stream » par défaut, quelle que soit l'extension —
        // et un type déclaré ne protège de rien : il suffit de le changer.
        byte[] tete = new byte[16];
        try (java.io.InputStream flux = fichier.getInputStream()) {
            flux.readNBytes(tete, 0, 16);
        } catch (java.io.IOException e) {
            throw new BusinessException("Le fichier n'a pas pu être lu.");
        }
        boolean accepte = DiscussionMessage.AUDIO.equals(voulu)
                ? estUnAudio(tete)
                : DiscussionMessage.PHOTO.equals(voulu) && estUneImage(tete);
        if (!accepte) {
            throw new BusinessException(DiscussionMessage.AUDIO.equals(voulu)
                    ? "Ce fichier n'est pas une note vocale reconnue."
                    : "Ce fichier n'est pas une image JPG ou PNG.");
        }

        TestOrder demande = testOrderRepository.findByIdAndBranchId(testOrderId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", testOrderId));
        Discussion fil = ouvrirOuCreer(demande, auteurId, branchId);

        if (taggedUserId != null) {
            userRepository.findById(taggedUserId)
                    .ifPresent(u -> ajouterAuFil(fil, u.getId(), roleDe(u)));
        }

        String nom;
        try {
            // Rangé à part des clichés de bons d'examen : ce ne sont pas les
            // mêmes pièces, et les mêler compliquerait toute reprise ou tout
            // ménage ultérieur.
            nom = fichiers.store(fichier, "discussions");
        } catch (java.io.IOException e) {
            throw new BusinessException("Le fichier n'a pas pu être enregistré.");
        }

        DiscussionMessage message = messages.save(
                new DiscussionMessage(fil, auteurId, voulu, nom, taggedUserId));
        lectures.save(new DiscussionLecture(message.getId(), auteurId));

        log.info("Fichier posté au fil : dossier={} auteur={} type={} taille={}",
                testOrderId, auteurId, voulu, fichier.getSize());
        Map<UUID, User> gens = chargerLesGens(fil, List.of(message));
        prevenir(fil, demande, message, gens);
        return versDto(message, gens, fil, Set.of());
    }

    /**
     * Marque tout le fil comme lu pour cette personne.
     *
     * <p>Ouvrir la discussion vaut lecture de tout ce qu'elle contient : c'est
     * ce que la maquette décrit, et c'est ce qu'un lecteur attend — un badge
     * qui survit à l'ouverture se lit comme un défaut.</p>
     */
    @Transactional
    public void marquerLu(UUID testOrderId, UUID lecteurId) {
        discussions.findByTestOrderId(testOrderId).ifPresent(fil -> {
            List<DiscussionLecture> aPoser = lectures.nonLusDuFil(fil.getId(), lecteurId)
                    .stream()
                    .map(id -> new DiscussionLecture(id, lecteurId))
                    .toList();
            if (!aPoser.isEmpty()) lectures.saveAll(aPoser);
        });
    }

    /** Ce que cette personne n'a pas lu, dossier par dossier. */
    @Transactional(readOnly = true)
    public List<NonLusDto> nonLus(UUID lecteurId) {
        return messages.compterNonLus(lecteurId).stream()
                .map(l -> new NonLusDto((UUID) l[0], ((Number) l[1]).longValue()))
                .toList();
    }

    /**
     * Prévient les participants, sauf celui qui vient d'écrire.
     *
     * <p>Les participants portent déjà les règles d'adressage de la maquette :
     * le médecin affecté et les techniciens du dossier y entrent à l'ouverture,
     * et taguer quelqu'un l'y fait entrer. Les recalculer ici les ferait
     * diverger de ce que compte le badge des non-lus, avec le pire des
     * résultats : une notification pour un message qui n'apparaît nulle
     * part.</p>
     *
     * <h2>Ce que la notification tait</h2>
     *
     * <p>Le contenu du message. Elle s'affiche sur un écran verrouillé, parfois
     * sous les yeux d'un tiers — dans un couloir, sur une table. Elle nomme donc
     * l'auteur et le dossier, et laisse le reste derrière le déverrouillage.
     * C'est un geste de plus, et c'est le prix de ne pas exposer une
     * conversation médicale à qui passe.</p>
     *
     * <p>Un tag est signalé comme tel : être nommé change l'urgence, et le
     * taire ferait manquer ce que la maquette veut justement mettre en avant.</p>
     */
    private void prevenir(Discussion fil, TestOrder demande,
                          DiscussionMessage message, Map<UUID, User> gens) {
        try {
            if (!notifications.estActif()) return;
            List<UUID> destinataires = fil.getParticipants().stream()
                    .map(DiscussionParticipant::getUserId)
                    .filter(id -> !id.equals(message.getAuthorId()))
                    .toList();
            if (destinataires.isEmpty()) return;

            List<String> jetons = appareils.jetonsDe(destinataires);
            if (jetons.isEmpty()) return;

            // Un nom introuvable ne doit pas donner « null a écrit… » : mieux
            // vaut une tournure impersonnelle qu'un mot qui trahit un défaut.
            String auteur = nomDe(gens.get(message.getAuthorId()));
            if (auteur == null || auteur.isBlank()) auteur = "Quelqu'un";
            String quoi = switch (message.getType()) {
                case DiscussionMessage.PHOTO -> "a envoyé une photo";
                case DiscussionMessage.AUDIO -> "a envoyé une note vocale";
                default -> "a écrit dans la discussion";
            };
            String corps = message.getTaggedUserId() != null
                    ? auteur + " vous a nommé dans la discussion"
                    : auteur + " " + quoi;

            notifications.prevenir(
                    jetons,
                    "Dossier " + demande.getCode(),
                    corps,
                    Map.of("testOrderId", demande.getId().toString(),
                           "codeDemande", demande.getCode() == null ? "" : demande.getCode()));
        } catch (Exception e) {
            // Une notification qui échoue ne doit jamais empêcher un message
            // d'être posté. La conversation est la donnée ; l'alerte n'en est
            // que l'écho.
            log.warn("Notification du fil impossible", e);
        }
    }

    // ── Interne ─────────────────────────────────────────────────────────

    /**
     * Le fichier porte-t-il la signature d'un format audio attendu ?
     *
     * <p>Quatre conteneurs suffisent à couvrir ce que produisent Android et
     * iOS : MP4 — celui de l'application —, MP3, Ogg et WAV. Le reste est
     * refusé plutôt que deviné.</p>
     */
    private boolean estUnAudio(byte[] t) {
        // MP4 / M4A : « ftyp » en quatrième octet, la taille de boîte devant.
        if (t[4] == 'f' && t[5] == 't' && t[6] == 'y' && t[7] == 'p') return true;
        // MP3 : une étiquette ID3, ou une trame brute (0xFF 0xEx/0xFx).
        if (t[0] == 'I' && t[1] == 'D' && t[2] == '3') return true;
        if ((t[0] & 0xFF) == 0xFF && (t[1] & 0xE0) == 0xE0) return true;
        // Ogg.
        if (t[0] == 'O' && t[1] == 'g' && t[2] == 'g' && t[3] == 'S') return true;
        // WAV : conteneur RIFF portant la marque WAVE.
        if (t[0] == 'R' && t[1] == 'I' && t[2] == 'F' && t[3] == 'F'
                && t[8] == 'W' && t[9] == 'A' && t[10] == 'V' && t[11] == 'E') {
            return true;
        }
        // Matroska / WebM.
        return (t[0] & 0xFF) == 0x1A && (t[1] & 0xFF) == 0x45
                && (t[2] & 0xFF) == 0xDF && (t[3] & 0xFF) == 0xA3;
    }

    /** Signature JPEG ({@code FF D8 FF}) ou PNG ({@code 89 50 4E 47}). */
    private boolean estUneImage(byte[] t) {
        boolean jpeg = (t[0] & 0xFF) == 0xFF && (t[1] & 0xFF) == 0xD8
                && (t[2] & 0xFF) == 0xFF;
        boolean png = (t[0] & 0xFF) == 0x89 && t[1] == 'P' && t[2] == 'N' && t[3] == 'G';
        return jpeg || png;
    }

    private Discussion ouvrirOuCreer(TestOrder demande, UUID venantId, UUID branchId) {
        Discussion fil = discussions.findByTestOrderId(demande.getId())
                .orElseGet(() -> discussions.save(
                        new Discussion(demande.getId(), branchId)));

        // Les personnes que le dossier désigne déjà : le médecin affecté et le
        // technicien qui a composé le lot. Les ajouter à l'ouverture évite
        // qu'un fil naisse sans destinataire — un message au groupe n'irait
        // alors à personne.
        detailRepository.findByTestOrderId(demande.getId())
                .map(com.labo.anapath.testorder.TestOrderAssignmentDetail::getTestOrderAssignment)
                .ifPresent(lot -> {
                    if (lot.getUser() != null) {
                        ajouterAuFil(fil, lot.getUser().getId(), DiscussionParticipant.MEDECIN);
                    }
                    if (lot.getCreatedBy() != null) {
                        userRepository.findById(lot.getCreatedBy())
                                .ifPresent(u -> ajouterAuFil(fil, u.getId(), roleDe(u)));
                    }
                });

        userRepository.findById(venantId).ifPresent(u -> ajouterAuFil(fil, u.getId(), roleDe(u)));
        return fil;
    }

    private void ajouterAuFil(Discussion fil, UUID userId, String role) {
        if (userId == null) return;
        if (participants.findByDiscussionIdAndUserId(fil.getId(), userId).isPresent()) return;
        DiscussionParticipant p = participants.save(
                new DiscussionParticipant(fil, userId, role));
        fil.getParticipants().add(p);
    }

    /**
     * Sous quelle casquette cette personne parle.
     *
     * <p>Décidé par le rôle, comme les parcours de l'application. Qui n'est pas
     * médecin est technicien : le fil ne connaît que ces deux voix, et un
     * troisième libellé n'apprendrait rien à qui lit.</p>
     */
    private String roleDe(User u) {
        boolean medecin = u.getRoles().stream()
                .anyMatch(r -> "docteur".equalsIgnoreCase(r.getSlug()));
        return medecin ? DiscussionParticipant.MEDECIN : DiscussionParticipant.TECHNICIEN;
    }

    private Map<UUID, User> chargerLesGens(Discussion fil, List<DiscussionMessage> liste) {
        Set<UUID> ids = new java.util.HashSet<>();
        fil.getParticipants().forEach(p -> ids.add(p.getUserId()));
        liste.forEach(m -> {
            ids.add(m.getAuthorId());
            if (m.getTaggedUserId() != null) ids.add(m.getTaggedUserId());
        });
        Map<UUID, User> gens = new HashMap<>();
        for (User u : userRepository.findAllById(new ArrayList<>(ids))) {
            gens.put(u.getId(), u);
        }
        return gens;
    }

    private String nomDe(User u) {
        return u == null ? null : NomComplet.de(u.getLastname(), u.getFirstname());
    }

    private MessageDto versDto(DiscussionMessage m, Map<UUID, User> gens,
                               Discussion fil, Set<UUID> nonLus) {
        String role = fil.getParticipants().stream()
                .filter(p -> p.getUserId().equals(m.getAuthorId()))
                .map(DiscussionParticipant::getRole)
                .findFirst()
                .orElse(DiscussionParticipant.TECHNICIEN);
        // Le message garde le nom du fichier ; l'écran reçoit une URL. Stocker
        // l'URL en base la figerait — un changement de préfixe casserait tous
        // les messages anciens.
        String contenu = DiscussionMessage.TEXTE.equals(m.getType())
                ? m.getContent()
                : fichiers.getUrl(m.getContent());
        return new MessageDto(
                m.getId(), m.getAuthorId(), nomDe(gens.get(m.getAuthorId())), role,
                m.getType(), contenu,
                m.getTaggedUserId(), nomDe(gens.get(m.getTaggedUserId())),
                // Un identifiant absent vaut « lu » : le seul message qui puisse
                // en manquer est celui qu'on vient d'écrire, et son auteur l'a
                // lu par définition. Interroger l'ensemble avec un nul lèverait,
                // `Set.of` refusant `contains(null)`.
                m.getCreatedAt(), m.getId() == null || !nonLus.contains(m.getId()));
    }
}
