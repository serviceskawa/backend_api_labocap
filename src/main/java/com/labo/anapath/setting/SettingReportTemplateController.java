package com.labo.anapath.setting;

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
 * Contrôleur REST gérant les modèles de mise en page pour les rapports PDF.
 * <p>
 * Toutes les routes sont préfixées par {@code /api/v1/report-templates}.
 * Le contrôleur gère directement le repository car il n'y a pas de couche
 * service dédiée pour les modèles de rapport.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/report-templates")
@RequiredArgsConstructor
public class SettingReportTemplateController {

    private final SettingReportTemplateRepository settingReportTemplateRepository;

    /** DTO de réponse interne représentant un modèle de rapport (réplique Laravel + champs refonte). */
    record SettingReportTemplateResponseDto(
            UUID id,
            String title,        // champ Laravel principal (équiv. name)
            String description,  // champ Laravel
            String detail,       // champ Laravel
            String content,      // champ Laravel principal (équiv. header)
            String name,
            String header,
            String footer,
            String logoPath,
            UUID branchId,
            LocalDateTime createdAt
    ) {}

    /** DTO de requête interne pour la création ou la mise à jour d'un modèle de rapport. */
    @Getter
    @Setter
    static class SettingReportTemplateRequestDto {
        @NotBlank(message = "Le titre du modèle est obligatoire")
        private String title;

        private String description;
        private String detail;
        private String content;
        private String name;
        private String header;
        private String footer;
        private String logoPath;
    }

    /** Helper de conversion entité → DTO. */
    private SettingReportTemplateResponseDto toDto(SettingReportTemplate t) {
        return new SettingReportTemplateResponseDto(
                t.getId(),
                t.getTitle() != null ? t.getTitle() : t.getName(),
                t.getDescription(),
                t.getDetail(),
                t.getContent() != null ? t.getContent() : t.getHeader(),
                t.getName(),
                t.getHeader(),
                t.getFooter(),
                t.getLogoPath(),
                t.getBranchId(),
                t.getCreatedAt());
    }

    /**
     * Retourne la liste paginée des modèles de rapport de la filiale connectée.
     *
     * <p>Ouvert à {@code edit-reports} en plus de {@code view-settings} : les
     * modèles servent à rédiger les comptes rendus, mais seul le super-admin
     * détient {@code view-settings}. Docteurs, laborantins et secrétariat — les
     * quatre rôles qui écrivent réellement les comptes rendus — recevaient un
     * 403 et voyaient « Aucun template ». Même travers que le sélecteur de
     * signataires : une permission d'administration exigée pour remplir une
     * liste déroulante.</p>
     *
     * <p>La création, la modification et la suppression restent réservées à
     * {@code edit-settings} : seule la lecture est élargie.</p>
     *
     * @param page      numéro de page
     * @param size      taille de la page
     * @param principal utilisateur authentifié
     * @return page de modèles de rapport
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('view-settings', 'edit-reports')")
    public ResponseEntity<ApiResponse<PageResponse<SettingReportTemplateResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                settingReportTemplateRepository.findByBranchId(principal.getBranchId(),
                        PageRequest.of(page, size)).map(this::toDto))));
    }

    /**
     * Retourne le détail d'un modèle de rapport par son identifiant.
     *
     * @param id identifiant UUID du modèle
     * @return le modèle correspondant
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('view-settings', 'edit-reports')")
    public ResponseEntity<ApiResponse<SettingReportTemplateResponseDto>> findById(@PathVariable UUID id) {
        SettingReportTemplate t = settingReportTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modèle de rapport", id));
        return ResponseEntity.ok(ApiResponse.success(toDto(t)));
    }

    /**
     * Crée un nouveau modèle de rapport pour la filiale de l'utilisateur connecté.
     *
     * @param dto       données du modèle à créer
     * @param principal utilisateur authentifié
     * @return le modèle créé avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-settings')")
    @Transactional
    public ResponseEntity<ApiResponse<SettingReportTemplateResponseDto>> create(
            @Valid @RequestBody SettingReportTemplateRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        SettingReportTemplate t = new SettingReportTemplate();
        t.setBranchId(principal.getBranchId());
        t.setTitle(dto.getTitle());
        t.setDescription(dto.getDescription());
        t.setDetail(dto.getDetail());
        t.setContent(dto.getContent());
        // Compat avec colonnes refonte
        t.setName(dto.getName() != null ? dto.getName() : dto.getTitle());
        t.setHeader(dto.getHeader() != null ? dto.getHeader() : dto.getContent());
        t.setFooter(dto.getFooter());
        t.setLogoPath(dto.getLogoPath());
        SettingReportTemplate saved = settingReportTemplateRepository.save(t);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Modèle créé", toDto(saved)));
    }

    /**
     * Met à jour un modèle de rapport existant.
     *
     * @param id  identifiant UUID du modèle à modifier
     * @param dto nouvelles données
     * @return le modèle mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-settings')")
    @Transactional
    public ResponseEntity<ApiResponse<SettingReportTemplateResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody SettingReportTemplateRequestDto dto) {
        SettingReportTemplate t = settingReportTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modèle de rapport", id));
        t.setTitle(dto.getTitle());
        t.setDescription(dto.getDescription());
        t.setDetail(dto.getDetail());
        t.setContent(dto.getContent());
        t.setName(dto.getName() != null ? dto.getName() : dto.getTitle());
        t.setHeader(dto.getHeader() != null ? dto.getHeader() : dto.getContent());
        t.setFooter(dto.getFooter());
        t.setLogoPath(dto.getLogoPath());
        SettingReportTemplate saved = settingReportTemplateRepository.save(t);
        return ResponseEntity.ok(ApiResponse.success("Modèle mis à jour", toDto(saved)));
    }

    /**
     * Supprime un modèle de rapport par son identifiant.
     *
     * @param id identifiant UUID du modèle à supprimer
     * @return réponse vide confirmant la suppression
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-settings')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        settingReportTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modèle de rapport", id));
        settingReportTemplateRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Modèle supprimé", null));
    }
}
