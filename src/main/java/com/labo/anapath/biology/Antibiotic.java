package com.labo.anapath.biology;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Antibiotique testé sur un germe isolé (antibiogramme).
 *
 * <p>Référentiel de la succursale, commun à toutes les analyses CULTURE. Le nom
 * est unique parmi les antibiotiques actifs de la succursale.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}) : un
 * antibiogramme déjà rendu garde son libellé.</p>
 */
@Entity
@Table(name = "antibiotics")
@SQLDelete(sql = "UPDATE antibiotics SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Antibiotic extends AuditableEntity {

    /** Dénomination commune (ex. : « Amoxicilline »). */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Nom commercial usuel (ex. : « Clamoxyl »). */
    @Column(name = "commercial_name", length = 150)
    private String commercialName;

    /** Famille (ex. : « Bêta-lactamines »). */
    @Column(name = "family", length = 100)
    private String family;

    /** Code court (ex. : « AMX »), unique dans la succursale s'il est renseigné. */
    @Column(name = "code", length = 50)
    private String code;

    /** Rang d'affichage dans l'antibiogramme, croissant. */
    @Column(name = "position", nullable = false)
    private int position;
}
