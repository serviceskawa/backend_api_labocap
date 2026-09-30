package com.labo.anapath.biology;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Option de culture : une rubrique du compte-rendu de culture et les valeurs
 * qu'elle peut prendre (ex. : « Aspect » → « Clair », « Trouble », « Hématique »).
 *
 * <p>Référentiel de la succursale ; chaque analyse CULTURE retient les options
 * qui la concernent via {@link LabTestCultureOption}.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "biology_culture_options")
@SQLDelete(sql = "UPDATE biology_culture_options SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyCultureOption extends AuditableEntity {

    /** Libellé de la rubrique (ex. : « Aspect »), unique dans la succursale. */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Valeurs proposées (tableau JSON) ; vide ou {@code null} = saisie libre. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", columnDefinition = "jsonb")
    private List<String> choices;

    /** Rang d'affichage par défaut, croissant. */
    @Column(name = "position", nullable = false)
    private int position;
}
