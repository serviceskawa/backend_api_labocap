package com.labo.anapath.testorder;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.contract.Contrat;
import com.labo.anapath.doctor.Doctor;
import com.labo.anapath.doctor.Hospital;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.test.TypeOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité centrale du LIS : représente un bon d'examen anatomopathologique.
 *
 * <p>Cycle de vie (machine d'état) :
 * <ul>
 *   <li>{@link TestOrderStatus#PENDING} — bon créé, en attente de validation</li>
 *   <li>{@link TestOrderStatus#VALIDATED} — validé par un technicien ; un code unique est généré
 *       (format {@code EX26-0001}), un {@link com.labo.anapath.report.Report} DRAFT est créé
 *       et la facturation est déclenchée</li>
 *   <li>{@link TestOrderStatus#DELIVERED} — résultats remis au patient</li>
 *   <li>{@link TestOrderStatus#CANCELLED} — bon annulé</li>
 * </ul>
 *
 * <p>Le champ {@code code} est {@code NULL} tant que le bon n'est pas validé, puis unique
 * par branche et par année civile.
 */
@Entity
@Table(name = "test_orders")
@SQLDelete(sql = "UPDATE test_orders SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class TestOrder extends AuditableEntity {

    /** Code unique du bon d'examen (ex. {@code EX26-0001}). Null tant que le bon est PENDING. */
    @Column(name = "code", unique = true, length = 50)
    private String code;

    /** Statut courant dans la machine d'état du bon d'examen. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TestOrderStatus status = TestOrderStatus.PENDING;

    /** Date à laquelle le prélèvement anatomopathologique a été effectué. */
    @Column(name = "prelevement_date", nullable = false)
    private LocalDate prelevementDate;

    /** Référence interne de l'hôpital prescripteur (numéro de dossier externe, etc.). */
    @Column(name = "reference_hopital", length = 255)
    private String referenceHopital;

    /** Indique si le bon est marqué comme urgent, afin de prioriser le traitement. */
    @Column(name = "is_urgent")
    private Boolean isUrgent = false;

    /** Référence d'archivage physique du prélèvement (numéro de bloc, cassette, etc.). */
    @Column(name = "archive", length = 255)
    private String archive;

    /** Sous-total avant remise (somme des prix unitaires des analyses). */
    @Column(name = "subtotal")
    private Double subtotal;

    /** Remise globale en valeur absolue appliquée sur le sous-total. */
    @Column(name = "discount")
    private Double discount;

    /** Montant total TTC après remise. */
    @Column(name = "total")
    private Double total;

    /** Identifiant du médecin anatomopathologiste auquel le bon est attribué pour lecture. */
    @Column(name = "attribuate_doctor_id")
    private UUID attribuateDoctorId;

    /** Identifiant du technicien/utilisateur auquel le bon est assigné pour traitement. */
    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    /** Analyses affiliées complémentaires (stockées en texte libre, héritage du système Laravel). */
    @Column(name = "test_affiliate", columnDefinition = "TEXT")
    private String testAffiliate;

    /** Noms des fichiers joints (images macroscopiques, etc.), sérialisés en texte. */
    @Column(name = "files_name", columnDefinition = "TEXT")
    private String filesName;

    /** Option tarifaire spécifique négociée avec le contrat. */
    @Column(name = "option")
    private Boolean option;

    /** Statut de l'appel téléphonique de notification du patient. */
    @Column(name = "status_appel", length = 255)
    private String statusAppel;

    /** Date et heure d'assignation du bon à un technicien. */
    @Column(name = "assignment_date")
    private LocalDateTime assignmentDate;

    /**
     * Patient concerné par cet examen anatomopathologique.
     *
     * <p>{@link NotFound} : les entités référencées ici sont en soft delete
     * ({@code @SQLRestriction("deleted_at IS NULL")}). Une fois la cible supprimée,
     * la clé étrangère du bon continue de pointer dessus alors que la ligne est
     * devenue invisible : sans {@code IGNORE}, l'initialisation du proxy lève une
     * {@code EntityNotFoundException} et fait échouer toute la liste des bons.
     * On résout donc l'association à {@code null}, ce qui reproduit le comportement
     * d'Eloquent côté Laravel (relation soft-deletée = null).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** Médecin prescripteur (peut être null si la demande est directe). */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    /** Hôpital d'origine du prélèvement (peut être null pour les patients externes). */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    /**
     * Contrat de facturation associé — obligatoire pour la validation du bon.
     * Détermine le mode de facturation (individuelle ou groupée).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "contrat_id")
    private Contrat contrat;

    /** Type de bon d'examen (biopsie, cytologie, etc.). */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "type_order_id")
    private TypeOrder typeOrder;

    /** Liste des analyses demandées dans ce bon, avec prix et remise individuels. */
    @OneToMany(mappedBy = "testOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetailTestOrder> details = new ArrayList<>();
}
