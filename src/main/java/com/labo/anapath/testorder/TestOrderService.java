package com.labo.anapath.testorder;

import com.labo.anapath.common.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Interface de service pour la gestion des bons d'examen anatomopathologiques.
 *
 * <p>Définit le contrat métier du workflow central du LIS :
 * création, consultation, modification, suppression et transitions d'état des bons d'examen.
 */
public interface TestOrderService {

    /**
     * Retourne la liste paginée des bons d'examen d'une branche, filtrée selon les critères fournis.
     *
     * @param page     numéro de page (0-based)
     * @param size     taille de la page
     * @param filter   critères de filtrage (statut, patient, médecin, dates, urgence, texte)
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return page de {@link TestOrderResponseDto}
     */
    PageResponse<TestOrderResponseDto> findAll(int page, int size, TestOrderFilterDto filter, UUID branchId);

    /**
     * Retourne la liste paginée des bons d'examen de la section Immunohistochimie
     * (types {@code immuno-interne} et {@code immuno-exterme}), filtrée selon les
     * critères fournis.
     *
     * @param page     numéro de page (0-based)
     * @param size     taille de la page
     * @param filter   critères de filtrage
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return page de {@link TestOrderResponseDto}
     */
    PageResponse<TestOrderResponseDto> findAllImmuno(int page, int size, TestOrderFilterDto filter, UUID branchId);

    /**
     * Retourne le nombre de bons immuno dont le rapport est en statut DRAFT
     * (ou inexistant), pour alimenter le badge sidebar.
     *
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return nombre de bons immuno en attente
     */
    long countImmunoPending(UUID branchId);

    /**
     * Retourne un bon d'examen par son identifiant, filtré par branche (isolation multi-tenant).
     *
     * @param id       identifiant UUID du bon
     * @param branchId identifiant de la branche (sécurité multi-tenant)
     * @return le DTO correspondant
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si le bon n'existe pas ou n'appartient pas à la branche
     */
    TestOrderResponseDto findById(UUID id, UUID branchId);

    /**
     * Crée un nouveau bon d'examen au statut PENDING pour la branche spécifiée.
     *
     * @param dto      données du bon à créer
     * @param branchId identifiant de la branche propriétaire
     * @return le bon créé
     */
    TestOrderResponseDto create(TestOrderRequestDto dto, UUID branchId);

    /**
     * Met à jour un bon d'examen existant. Seuls les bons PENDING peuvent être modifiés.
     *
     * @param id       identifiant UUID du bon
     * @param dto      nouvelles données
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le bon mis à jour
     */
    TestOrderResponseDto update(UUID id, TestOrderRequestDto dto, UUID branchId);

    /**
     * Supprime (soft delete) un bon d'examen non encore validé.
     *
     * @param id       identifiant UUID du bon à supprimer
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @throws com.labo.anapath.common.exception.InvalidOperationException si le bon est déjà validé
     */
    void delete(UUID id, UUID branchId);

    /**
     * Déclenche le workflow de validation du bon d'examen.
     *
     * <p>Séquence d'opérations lors du passage à VALIDATED :
     * <ol>
     *   <li>Vérification que le contrat est renseigné</li>
     *   <li>Génération du code unique avec protection contre les race conditions</li>
     *   <li>Création ou mise à jour du {@link com.labo.anapath.report.Report} lié</li>
     *   <li>Journalisation dans {@link com.labo.anapath.report.LogReport}</li>
     *   <li>Facturation individuelle ou groupée selon le type de contrat</li>
     * </ol>
     *
     * @param id       identifiant UUID du bon
     * @param status   valeur attendue : {@code "VALIDATED"}
     * @param userId   identifiant de l'utilisateur effectuant la validation
     * @param branchId identifiant de la branche (sécurité multi-tenant)
     * @return le bon validé avec son code généré
     */
    TestOrderResponseDto updateStatus(UUID id, String status, UUID userId, UUID branchId);

    /**
     * Marque un bon d'examen comme livré (statut DELIVERED).
     *
     * <p>Seuls les bons au statut {@code VALIDATED} peuvent être livrés.
     * Positionne également {@code report.isDelivered = true} sur le compte-rendu lié.
     *
     * @param id       identifiant UUID du bon à livrer
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le bon mis à jour
     * @throws com.labo.anapath.common.exception.InvalidOperationException si le bon n'est pas VALIDATED
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si le bon ou son compte-rendu est introuvable
     */
    TestOrderResponseDto markAsDelivered(UUID id, UUID branchId);

    /**
     * Assigne un médecin pathologiste à un bon d'examen.
     *
     * <p>Positionne {@code attribuateDoctorId}, {@code assignedToUserId} (même valeur)
     * et {@code assignmentDate = now()} sur le bon. Aucun guard sur le statut.
     *
     * @param id       identifiant UUID du bon
     * @param doctorId identifiant UUID du médecin/utilisateur à assigner
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le bon mis à jour
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si le bon est introuvable
     */
    TestOrderResponseDto assignDoctor(UUID id, UUID doctorId, UUID branchId);

    List<String> uploadImages(UUID id, UUID branchId, List<MultipartFile> files);

    List<ImageDto> getImages(UUID id, UUID branchId);

    void deleteImage(UUID id, int index, UUID branchId);

    // -------------------------------------------------------------------------
    // Myspace
    // -------------------------------------------------------------------------

    /**
     * Retourne les statistiques des bons d'examen assignés à l'utilisateur connecté.
     *
     * @param userId   identifiant de l'utilisateur connecté
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return DTO de statistiques (total, pending, validated, urgent, late)
     */
    MyspaceStatsDto getMyspaceStats(UUID userId, UUID branchId);

    /**
     * Retourne la liste paginée des bons d'examen assignés à l'utilisateur connecté,
     * avec filtres optionnels sur le statut et une recherche textuelle.
     *
     * @param userId   identifiant de l'utilisateur connecté
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @param page     numéro de page (0-based)
     * @param size     taille de la page
     * @param status      filtre optionnel sur le statut
     * @param typeOrderId filtre optionnel sur le type d'examen
     * @param priority    filtre optionnel sur la priorité ({@code urgent} ou {@code late})
     * @param from        date de début optionnelle (YYYY-MM-DD, sur created_at)
     * @param to          date de fin optionnelle (YYYY-MM-DD, sur created_at)
     * @param search      recherche textuelle optionnelle (code du bon ou nom du patient)
     * @return page de {@link TestOrderResponseDto}
     */
    PageResponse<TestOrderResponseDto> getMyspaceOrders(UUID userId, UUID branchId, int page, int size,
                                                        TestOrderStatus status, UUID typeOrderId,
                                                        String priority, String from, String to, String search);

    // -------------------------------------------------------------------------
    // Tarification contractuelle
    // -------------------------------------------------------------------------

    /**
     * Retourne la tarification d'une analyse pour un contrat donné.
     *
     * <p>Si l'analyse est référencée dans le contrat, retourne le prix négocié et la remise.
     * Sinon, retourne uniquement le prix catalogue avec une remise nulle.
     *
     * @param contratId  identifiant du contrat
     * @param labTestId  identifiant de l'analyse
     * @param branchId   identifiant de la branche (isolation multi-tenant, pour récupérer l'analyse)
     * @return DTO de tarification
     */
    DiscountDto getDiscount(UUID contratId, UUID labTestId, UUID branchId);
}
