package com.labo.anapath.report;

import com.labo.anapath.common.NomComplet;
import com.labo.anapath.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Photographie complète d'un compte-rendu signé, prise juste avant qu'on
 * l'écrase.
 *
 * <p>Le journal ({@link LogReport}) dit quels champs ont changé après la
 * signature, pas ce qu'ils contenaient. Or un diagnostic corrigé après coup
 * est précisément ce qu'une expertise ou un litige voudra relire. On garde
 * donc l'état d'avant, entier, à chaque modification d'un compte-rendu validé
 * ou livré.</p>
 *
 * <p>Pas d'{@code AuditableEntity} : la table est en ajout seul — ni
 * modification, ni suppression, ni horodatage de mise à jour qui ne voudrait
 * rien dire. Les textes sont copiés colonne à colonne, sous le nom qu'ils ont
 * dans {@code reports} : une version se lit sans décodeur.</p>
 */
@Entity
@Table(name = "report_versions")
@Getter
@Setter
@NoArgsConstructor
public class ReportVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    /** Numéro croissant par compte-rendu, 1 pour la première version écrasée. */
    @Column(name = "version", nullable = false)
    private int version;

    /** Libellé du titre au moment de la prise, pas son identifiant : un titre peut être renommé. */
    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_micro", columnDefinition = "TEXT")
    private String contentMicro;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "comment_sup", columnDefinition = "TEXT")
    private String commentSup;

    @Column(name = "description_supplementaire", columnDefinition = "TEXT")
    private String descriptionSupplementaire;

    @Column(name = "description_supplementaire_micro", columnDefinition = "TEXT")
    private String descriptionSupplementaireMicro;

    /** Noms des signataires, en clair, séparés par des virgules. */
    @Column(name = "signataires", columnDefinition = "TEXT")
    private String signataires;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    /** Qui a provoqué la prise de version, c'est-à-dire l'auteur de la modification. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_by")
    private User savedBy;

    /** Copie l'état courant du compte-rendu ; le numéro est posé par l'appelant au moment d'enregistrer. */
    static ReportVersion de(Report report, User auteur) {
        ReportVersion v = new ReportVersion();
        v.report = report;
        v.title = report.getTitleReport() != null ? report.getTitleReport().getName() : null;
        v.content = report.getContent();
        v.contentMicro = report.getContentMicro();
        v.comment = report.getComment();
        v.commentSup = report.getCommentSup();
        v.descriptionSupplementaire = report.getDescriptionSupplementaire();
        v.descriptionSupplementaireMicro = report.getDescriptionSupplementaireMicro();
        v.signataires = Stream.of(report.getSignatory1(), report.getSignatory2(), report.getSignatory3())
                .filter(Objects::nonNull)
                .map(u -> NomComplet.de(u.getLastname(), u.getFirstname()))
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        v.status = report.getStatus();
        v.savedAt = LocalDateTime.now();
        v.savedBy = auteur;
        return v;
    }
}
