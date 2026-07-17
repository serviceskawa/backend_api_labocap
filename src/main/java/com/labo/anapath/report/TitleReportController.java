package com.labo.anapath.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion des titres de section prédéfinis des comptes-rendus
 * ({@code /api/v1/title-reports}).
 *
 * <p>Les titres de rapport permettent de standardiser la structure des CRs anatomopathologiques
 * (ex. "MACROSCOPIE", "MICROSCOPIE", "CONCLUSION"). CRUD complet exposé, logique directement
 * dans le contrôleur (référentiel simple sans service dédié).
 */
@RestController
@RequestMapping("/api/v1/title-reports")
@RequiredArgsConstructor
public class TitleReportController {

    private final TitleReportRepository titleReportRepository;

    /** DTO de réponse interne pour un titre de rapport. */
    record TitleReportResponseDto(UUID id, String name, boolean isDefault, UUID branchId, LocalDateTime createdAt) {}

    /** DTO de requête interne pour la création ou mise à jour d'un titre de rapport. */
    @Getter
    @Setter
    static class TitleReportRequestDto {
        @NotBlank(message = "Le nom du titre est obligatoire")
        private String name;

        /**
         * Indique si ce titre doit devenir le titre par défaut. Optionnel, défaut {@code false}.
         *
         * <p>{@code @JsonProperty("isDefault")} est OBLIGATOIRE : sans lui, Jackson dérive le nom
         * de propriété « default » depuis le getter/setter Lombok ({@code isDefault()}/{@code setDefault()})
         * et ignore silencieusement la clé JSON « isDefault » envoyée par le front — la case
         * « par défaut » resterait alors sans effet en création comme en modification.
         */
        @JsonProperty("isDefault")
        private boolean isDefault = false;
    }

    /** Convertit une entité {@link TitleReport} en {@link TitleReportResponseDto}. */
    private TitleReportResponseDto toDto(TitleReport t) {
        return new TitleReportResponseDto(t.getId(), t.getName(), t.isDefault(),
                t.getBranchId(), t.getCreatedAt());
    }

    /**
     * Retourne la liste paginée des titres de rapport de la branche de l'utilisateur connecté.
     *
     * @param page      numéro de page (0-based, défaut 0)
     * @param size      taille de la page (défaut 20)
     * @param principal principal Spring Security contenant le branchId
     * @return page de {@link TitleReportResponseDto}
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<PageResponse<TitleReportResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                titleReportRepository.findByBranchId(principal.getBranchId(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .map(this::toDto))));
    }

    /**
     * Retourne le titre de rapport marqué comme "par défaut" pour la branche de l'utilisateur connecté.
     *
     * @param principal principal Spring Security contenant le branchId
     * @return le titre par défaut, ou {@code null} si aucun n'est défini
     */
    @GetMapping("/default")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<TitleReportResponseDto>> getDefault(
            @AuthenticationPrincipal UserPrincipal principal) {
        TitleReportResponseDto dto = titleReportRepository
                .findFirstByBranchIdAndIsDefaultTrue(principal.getBranchId())
                .map(this::toDto)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * Retourne un titre de rapport par son identifiant.
     *
     * @param id identifiant UUID du titre
     * @return le titre correspondant, ou 404 s'il n'existe pas
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<TitleReportResponseDto>> findById(@PathVariable UUID id) {
        TitleReport t = titleReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Titre de rapport", id));
        return ResponseEntity.ok(ApiResponse.success(toDto(t)));
    }

    /**
     * Crée un nouveau titre de rapport pour la branche de l'utilisateur connecté.
     *
     * @param dto       données du titre ({@code name} obligatoire)
     * @param principal principal Spring Security contenant le branchId
     * @return le titre créé avec le code HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<TitleReportResponseDto>> create(
            @Valid @RequestBody TitleReportRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        TitleReport t = new TitleReport();
        t.setBranchId(principal.getBranchId());
        t.setName(dto.getName());
        t.setDefault(dto.isDefault());
        TitleReport saved = titleReportRepository.save(t);
        if (dto.isDefault()) {
            // Nouveau titre par défaut : retirer le flag sur tous les autres (en l'excluant).
            titleReportRepository.unsetDefaultForBranchExcept(saved.getBranchId(), saved.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Titre créé", toDto(saved)));
    }

    /**
     * Met à jour le nom d'un titre de rapport existant.
     *
     * @param id  identifiant UUID du titre
     * @param dto nouvelles données
     * @return le titre mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<TitleReportResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody TitleReportRequestDto dto) {
        TitleReport t = titleReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Titre de rapport", id));
        t.setName(dto.getName());
        t.setDefault(dto.isDefault());
        TitleReport saved = titleReportRepository.save(t);
        if (dto.isDefault()) {
            // Ce titre devient LE défaut : on retire le flag sur tous les AUTRES uniquement,
            // APRÈS avoir sauvegardé celui-ci (flushAutomatically), et en l'excluant — sinon
            // un bulk update sur tous annulerait le défaut qu'on vient de poser.
            titleReportRepository.unsetDefaultForBranchExcept(saved.getBranchId(), saved.getId());
        }
        return ResponseEntity.ok(ApiResponse.success("Titre mis à jour", toDto(saved)));
    }

    /**
     * Supprime un titre de rapport. Les CRs qui l'utilisaient ne sont pas affectés
     * (la relation est nullifiée côté Report).
     *
     * @param id identifiant UUID du titre à supprimer
     * @return réponse vide 200 en cas de succès
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        titleReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Titre de rapport", id));
        titleReportRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Titre supprimé", null));
    }
}
