package com.labo.anapath.appel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * La trace d'un appel : qui, quand, combien de temps.
 *
 * <h2>Ce qu'on ne garde pas</h2>
 *
 * <p>La voix. Un appel entre un médecin et un technicien porte un avis médical,
 * et l'enregistrer demanderait le consentement de chacun à chaque fois. Le
 * conserver créerait surtout un fonds d'archives autrement plus sensible que
 * tout le reste de cette base — pour un besoin que personne n'a exprimé.</p>
 *
 * <p>Ce qui est gardé permet de dire qu'un échange a eu lieu sur un dossier, à
 * quelle heure et entre qui : c'est ce qu'on cherche quand on revient sur un
 * cas, et c'est ce que la discussion écrite conserve déjà pour le texte.</p>
 */
@Entity
@Table(name = "call_logs")
@Getter
@Setter
@NoArgsConstructor
public class JournalAppel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "test_order_id", nullable = false)
    private UUID testOrderId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "initiateur_id", nullable = false)
    private UUID initiateurId;

    /**
     * Les conviés, séparés par des virgules.
     *
     * <p>Une table de liaison serait plus propre en théorie ; en pratique cette
     * colonne ne se lit qu'en entier, pour afficher « appel entre untel et
     * untel ». Une jointure de plus pour un usage qui n'en fait jamais.</p>
     */
    @Column(name = "participants", length = 1024)
    private String participants;

    @Column(name = "debut", nullable = false)
    private LocalDateTime debut;

    @Column(name = "fin")
    private LocalDateTime fin;

    @Column(name = "secondes")
    private Long secondes;

    /** « terminé », « refusé », « sans réponse ». */
    @Column(name = "issue", length = 32)
    private String issue;
}
