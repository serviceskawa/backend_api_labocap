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

    private final DiscussionRepositories.Discussions discussions;
    private final DiscussionRepositories.Messages messages;
    private final DiscussionRepositories.Participants participants;
    private final DiscussionRepositories.Lectures lectures;
    private final TestOrderRepository testOrderRepository;
    private final TestOrderAssignmentDetailRepository detailRepository;
    private final UserRepository userRepository;

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

    // ── Interne ─────────────────────────────────────────────────────────

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
        return new MessageDto(
                m.getId(), m.getAuthorId(), nomDe(gens.get(m.getAuthorId())), role,
                m.getType(), m.getContent(),
                m.getTaggedUserId(), nomDe(gens.get(m.getTaggedUserId())),
                // Un identifiant absent vaut « lu » : le seul message qui puisse
                // en manquer est celui qu'on vient d'écrire, et son auteur l'a
                // lu par définition. Interroger l'ensemble avec un nul lèverait,
                // `Set.of` refusant `contains(null)`.
                m.getCreatedAt(), m.getId() == null || !nonLus.contains(m.getId()));
    }
}
