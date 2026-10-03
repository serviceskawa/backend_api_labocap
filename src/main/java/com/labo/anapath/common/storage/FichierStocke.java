package com.labo.anapath.common.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Le rattachement d'un fichier stocké à l'entité qui le possède.
 *
 * <p>C'est ce qui permet à {@link FileController} d'appliquer, avant de servir
 * un fichier, la règle de lecture de son propriétaire : permission métier et
 * agence. Un fichier qui n'est rattaché à rien n'est plus servi.</p>
 *
 * <p>L'identifiant est <b>déduit du chemin</b> (UUID v3, MD5 du chemin) plutôt
 * que tiré au sort : le DTO qui renvoie un chemin peut donner son
 * {@code fileId} sans requête, la reprise SQL de l'existant (V102) calcule le
 * même identifiant que le code, et l'enregistrement d'un fichier déjà connu
 * est idempotent. La route par identifiant n'est pas un secret : c'est la
 * permission qui garde, pas l'adresse.</p>
 */
@Entity
@Table(name = "stored_files")
@Getter
@NoArgsConstructor
public class FichierStocke {

    public static final String TEST_ORDER = "TEST_ORDER";
    public static final String DISCUSSION_MESSAGE = "DISCUSSION_MESSAGE";
    public static final String CONSULTATION_FILE = "CONSULTATION_FILE";
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String EMPLOYEE_DOCUMENT = "EMPLOYEE_DOCUMENT";
    public static final String DOC = "DOC";
    public static final String DOC_VERSION = "DOC_VERSION";
    public static final String EXPENSE = "EXPENSE";
    public static final String REFUND_REQUEST = "REFUND_REQUEST";
    public static final String BANK_DEPOSIT = "BANK_DEPOSIT";
    public static final String CASHBOX_VOUCHER = "CASHBOX_VOUCHER";

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Relatif à la racine du stockage, tel que les services de stockage le rendent. */
    @Column(name = "path", nullable = false, unique = true, columnDefinition = "TEXT")
    private String path;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public FichierStocke(String path, String entityType, UUID entityId, UUID branchId) {
        this.id = idPour(path);
        this.path = path;
        this.entityType = entityType;
        this.entityId = entityId;
        this.branchId = branchId;
    }

    /** L'identifiant d'un fichier, déduit de son chemin ; nul si le chemin l'est. */
    public static UUID idPour(String path) {
        if (path == null || path.isBlank()) return null;
        return UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
    }
}
