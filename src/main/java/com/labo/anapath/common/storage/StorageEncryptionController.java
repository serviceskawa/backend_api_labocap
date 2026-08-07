package com.labo.anapath.common.storage;

import com.labo.anapath.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Déclenchement manuel du rattrapage du chiffrement.
 *
 * <p>Une opération d'exploitation, faite une fois : d'où {@code edit-settings},
 * la permission la plus proche de ce droit dans ce projet, plutôt qu'une
 * permission métier.</p>
 *
 * <p>Le mode par défaut est la <b>simulation</b>. Un appel sans paramètre ne
 * touche à rien : il compte ce qu'il y aurait à faire. Modifier des milliers de
 * fichiers ne doit pas être ce qui arrive quand on se trompe d'URL.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/storage/encryption")
@RequiredArgsConstructor
public class StorageEncryptionController {

    private final StorageEncryptionBackfill rattrapage;

    /**
     * @param simulation à laisser vrai pour compter sans rien modifier
     * @param limite     plafond de fichiers à chiffrer par passage, 0 pour tous
     */
    @PostMapping("/backfill")
    @PreAuthorize("hasAuthority('edit-settings')")
    public ResponseEntity<ApiResponse<StorageEncryptionBackfill.Bilan>> backfill(
            @RequestParam(defaultValue = "true") boolean simulation,
            @RequestParam(defaultValue = "0") int limite) {
        StorageEncryptionBackfill.Bilan bilan = rattrapage.rattraper(simulation, limite);
        return ResponseEntity.ok(ApiResponse.success(
                simulation ? "Simulation : aucun fichier modifié" : "Rattrapage effectué",
                bilan));
    }
}
