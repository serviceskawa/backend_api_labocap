package com.labo.anapath.client;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST exposant les opérations CRUD sur les clients institutionnels.
 * <p>
 * Les données sont isolées par agence : le {@code branchId} est extrait du
 * principal authentifié ({@link UserPrincipal}) et transmis au service.
 * La consultation nécessite l'autorité {@code view-clients} ; la création,
 * la modification et la suppression exigent respectivement {@code create-clients},
 * {@code edit-clients} et {@code delete-clients}, comme dans l'application Laravel.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    /**
     * Retourne la liste paginée des clients de l'agence de l'utilisateur connecté.
     *
     * @param page      numéro de page (commence à 0)
     * @param size      nombre d'éléments par page (défaut : 20)
     * @param principal principal de l'utilisateur authentifié (fournit le branchId)
     * @return page de {@link ClientResponseDto}
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-clients')")
    public ResponseEntity<ApiResponse<PageResponse<ClientResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(clientService.findAll(page, size, principal.getBranchId())));
    }

    /**
     * Retourne un client par son identifiant unique.
     *
     * @param id identifiant UUID du client
     * @return le DTO du client trouvé
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-clients')")
    public ResponseEntity<ApiResponse<ClientResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clientService.findById(id)));
    }

    /**
     * Recherche des clients par nom (recherche partielle, insensible à la casse),
     * limités à l'agence de l'utilisateur connecté.
     *
     * @param q         terme de recherche
     * @param principal principal de l'utilisateur authentifié
     * @return liste des clients correspondants
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('view-clients')")
    public ResponseEntity<ApiResponse<List<ClientResponseDto>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(clientService.search(q, principal.getBranchId())));
    }

    /**
     * Crée un nouveau client rattaché à l'agence de l'utilisateur connecté.
     *
     * @param dto       données du client à créer
     * @param principal principal de l'utilisateur authentifié
     * @return le DTO du client créé avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('create-clients')")
    public ResponseEntity<ApiResponse<ClientResponseDto>> create(
            @Valid @RequestBody ClientRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        ClientResponseDto created = clientService.create(dto, principal.getBranchId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client créé", created));
    }

    /**
     * Met à jour un client existant.
     * Vérifie que le client appartient bien à l'agence de l'utilisateur connecté.
     *
     * @param id        identifiant du client à modifier
     * @param dto       nouvelles données du client
     * @param principal principal de l'utilisateur authentifié
     * @return le DTO du client mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-clients')")
    public ResponseEntity<ApiResponse<ClientResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ClientRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Client mis à jour", clientService.update(id, dto, principal.getBranchId())));
    }

    /**
     * Supprime (logiquement) un client.
     * La suppression est refusée si le client possède des contrats actifs.
     *
     * @param id identifiant du client à supprimer
     * @return réponse vide confirmant la suppression
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-clients')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        clientService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Client supprimé", null));
    }
}
