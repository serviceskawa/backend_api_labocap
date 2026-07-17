package com.labo.anapath.inventory;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entité représentant un mouvement de stock (entrée, sortie ou ajustement)
 * pour un article du laboratoire.
 * <p>
 * Chaque mouvement est traçable et met à jour automatiquement la quantité
 * de l'article associé lors de sa création via {@link MovementServiceImpl}.
 * </p>
 */
@Entity
@Table(name = "movements")
@Getter
@Setter
@NoArgsConstructor
public class Movement extends AuditableEntity {

    /**
     * L'article peut avoir été supprimé (soft delete) alors que ses mouvements
     * subsistent : sans {@code IGNORE}, le filtre {@code deleted_at IS NULL} de
     * {@link Article} fait échouer le chargement et met en erreur toute la liste.
     * Laravel se contente d'ignorer ces lignes (`@if($movement->article)`).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Article article;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private MovementType type;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "movement_date")
    private LocalDate movementDate;
}
