package com.labo.anapath.biology.results;

import com.labo.anapath.common.dto.PageResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Saisie des résultats de biologie : liste de travail, feuille de saisie, valeurs,
 * cultures et validation technique.
 *
 * <p>La validation biologique, la réouverture, le PDF et les notifications ne sont
 * pas ici (B6, B7).</p>
 */
public interface BiologyResultService {

    /**
     * Liste de travail des analyses de biologie de la succursale.
     *
     * @param status     état recherché ({@code PENDING}, {@code ENTERED}, {@code TECH_VALIDATED}), ou {@code null}
     * @param categoryId catégorie d'analyse, ou {@code null}
     * @param from       création du bon à partir de ce jour (inclus), ou {@code null}
     * @param to         création du bon jusqu'à ce jour (inclus), ou {@code null}
     */
    PageResponse<BiologyWorklistRowDto> worklist(String status, UUID categoryId, LocalDate from, LocalDate to,
                                                 int page, int size, UUID branchId);

    /** Feuille de saisie complète d'un bon de biologie validé. */
    BiologyWorksheetDto worksheet(UUID testOrderId, UUID branchId);

    /** Enregistre les valeurs d'une analyse PANEL ; renvoie la feuille à jour. */
    BiologyWorksheetDto savePanel(UUID testOrderId, UUID labTestId, BiologyPanelResultsRequestDto dto,
                                  UUID userId, UUID branchId);

    /** Enregistre les résultats d'une analyse CULTURE ; renvoie la feuille à jour. */
    BiologyWorksheetDto saveCulture(UUID testOrderId, UUID labTestId, BiologyCultureResultsRequestDto dto,
                                    UUID userId, UUID branchId);

    /** Validation technique d'une analyse (sans effet en ONE_STEP) ; renvoie la feuille à jour. */
    BiologyWorksheetDto validateTechnically(UUID testOrderId, UUID labTestId, UUID userId, UUID branchId);

    /** Annulation de la validation technique d'une analyse ; renvoie la feuille à jour. */
    BiologyWorksheetDto cancelTechnicalValidation(UUID testOrderId, UUID labTestId, UUID userId, UUID branchId);
}
