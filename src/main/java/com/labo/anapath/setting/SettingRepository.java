package com.labo.anapath.setting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'accès aux paramètres de configuration.
 */
@Repository
public interface SettingRepository extends JpaRepository<Setting, UUID> {

    /**
     * Retourne une page de paramètres filtrés par filiale.
     *
     * @param branchId identifiant de la filiale
     * @param pageable paramètres de pagination
     * @return page de paramètres
     */
    Page<Setting> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Recherche un paramètre par sa clé et sa filiale.
     * Utilisé pour l'opération upsert afin de déterminer si le paramètre existe déjà.
     *
     * @param key      clé du paramètre
     * @param branchId identifiant de la filiale
     * @return paramètre correspondant si présent
     */
    Optional<Setting> findByKeyAndBranchId(String key, UUID branchId);

    /**
     * Retourne la ligne de réglages la plus ancienne d'une filiale.
     *
     * <p>Équivalent du singleton {@code Setting::first()} de Laravel, dont
     * {@code SettingController::report_store_placeholder()} et
     * {@code TestOrderController::updateStatus()} lisent et écrivent la colonne
     * {@code placeholder}. Le tri explicite rend le choix déterministe là où le
     * {@code LIMIT 1} sans {@code ORDER BY} de Laravel dépend de l'ordre physique.
     *
     * <p>Le départage par {@code id} est indispensable : la base migrée contient deux
     * lignes de réglages portant exactement le même {@code created_at}, un tri sur la
     * seule date renverrait donc l'une ou l'autre au hasard.</p>
     *
     * @param branchId identifiant de la filiale
     * @return la ligne de réglages la plus ancienne, si elle existe
     */
    Optional<Setting> findFirstByBranchIdOrderByCreatedAtAscIdAsc(UUID branchId);
}
