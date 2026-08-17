package com.labo.anapath.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Preuve d'appareil accompagnant une validation médicale.
 *
 * <p>Corps facultatif de {@code POST /reports/{id}/validate}. Absent, la
 * validation se comporte comme avant et n'est adossée qu'à la session — c'est
 * le cas du web. Présent, il est vérifié strictement : appareil actif,
 * appartenant bien à l'auteur, horodatage frais, signature valide. Une preuve
 * fournie mais fausse fait échouer l'acte plutôt que d'être ignorée.</p>
 *
 * @param deviceId  appareil enrôlé qui signe
 * @param signedAt  instant du geste, signé lui aussi — c'est ce qui empêche de
 *                  rejouer une signature capturée
 * @param signature signature base64 du condensé recomposé par le serveur
 */
public record ValidationSigneeDto(
        @NotNull UUID deviceId,
        @NotNull LocalDateTime signedAt,
        @NotBlank String signature) {
}
