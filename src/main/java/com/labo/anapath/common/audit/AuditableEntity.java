package com.labo.anapath.common.audit;

import com.labo.anapath.common.branch.BranchContext;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Classe de base commune à toutes les entités JPA du LIS.
 * <p>
 * Fournit automatiquement :
 * <ul>
 *   <li>Un identifiant UUID généré côté base de données.</li>
 *   <li>L'identifiant de la branche (multi-tenant) auquel appartient l'enregistrement.</li>
 *   <li>Les horodatages de création et de dernière modification (gérés par Spring Data JPA Auditing).</li>
 *   <li>Les UUIDs de l'utilisateur créateur et du dernier modificateur.</li>
 *   <li>Un champ {@code deletedAt} pour la suppression logique (soft delete).</li>
 * </ul>
 * Toute entité métier doit étendre cette classe plutôt que de redéclarer ces champs.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    /** Identifiant unique de l'entité, généré automatiquement en UUID v4. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identifiant de la branche (agence/site) à laquelle appartient cet enregistrement. */
    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    /** Date et heure de création, renseignée automatiquement par JPA Auditing. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Date et heure de dernière modification, mise à jour automatiquement par JPA Auditing. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** UUID de l'utilisateur ayant créé l'enregistrement (non modifiable après insertion). */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** UUID du dernier utilisateur ayant modifié l'enregistrement. */
    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Date et heure de suppression logique.
     * {@code null} tant que l'enregistrement est actif ; renseigné lors d'un soft delete.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Estampe automatiquement la branche à la création si elle n'a pas déjà été
     * renseignée explicitement — analogue du hook {@code creating} du
     * {@code BranchScopeTrait} de Laravel qui pose {@code branch_id = selected_branch_id}.
     * La valeur provient de {@link BranchContext} (branche active de la requête).
     */
    @PrePersist
    protected void stampBranchOnCreate() {
        if (branchId == null) {
            branchId = BranchContext.get();
        }
    }
}
