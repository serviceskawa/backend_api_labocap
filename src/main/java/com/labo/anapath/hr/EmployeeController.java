package com.labo.anapath.hr;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Contrôleur REST exposant les opérations CRUD sur les employés du laboratoire.
 * <p>
 * Toutes les routes sont préfixées par {@code /api/v1/employees}.
 * La consultation requiert l'autorité {@code view-hr} et la gestion
 * requiert {@code manage-employees}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Retourne la liste paginée des employés de la filiale de l'utilisateur connecté.
     *
     * @param page      numéro de page
     * @param size      taille de la page
     * @param principal utilisateur authentifié
     * @return page d'employés
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(employeeService.findAll(page, size, principal.getBranchId())));
    }

    /**
     * Retourne le détail d'un employé par son identifiant.
     *
     * @param id identifiant UUID de l'employé
     * @return l'employé correspondant
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(employeeService.findById(id)));
    }

    /**
     * Crée un nouvel employé rattaché à la filiale de l'utilisateur connecté.
     *
     * @param dto       données de l'employé à créer
     * @param principal utilisateur authentifié
     * @return l'employé créé avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> create(
            @Valid @RequestBody EmployeeRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employé créé", employeeService.create(dto, principal.getBranchId())));
    }

    /**
     * Met à jour les informations d'un employé existant.
     *
     * @param id  identifiant UUID de l'employé à modifier
     * @param dto nouvelles données
     * @return l'employé mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody EmployeeRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Employé mis à jour", employeeService.update(id, dto)));
    }

    /**
     * Téléverse la photo de profil d'un employé (multipart).
     *
     * @param id   identifiant UUID de l'employé
     * @param file fichier image
     * @return l'employé mis à jour, avec son nouveau {@code photoUrl}
     */
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> uploadPhoto(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponse.success("Photo mise à jour", employeeService.uploadPhoto(id, file)));
    }

    /**
     * Supprime (logiquement) un employé par son identifiant.
     *
     * @param id identifiant UUID de l'employé à supprimer
     * @return réponse vide confirmant la suppression
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        employeeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Employé supprimé", null));
    }

    /**
     * Retourne la liste allégée des employés pour alimenter les dropdowns (ex. : assignation
     * macroscopie). Accessible avec l'autorité {@code view-reports} pour ne pas exiger
     * {@code view-employees} sur les écrans de workflow.
     *
     * @param principal utilisateur authentifié
     * @return liste de tous les employés de la branche (max 200)
     */
    @GetMapping("/for-macro")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<java.util.List<EmployeeResponseDto>>> listForMacro(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                employeeService.findAll(0, 200, principal.getBranchId()).content()));
    }
}
