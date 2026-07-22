package com.labo.anapath.common.branch;

import java.util.UUID;

/**
 * Porte-contexte de la branche (agence/site) active pour la requête HTTP courante.
 * <p>
 * Équivalent de la valeur de session {@code selected_branch_id} de l'app Laravel :
 * la branche choisie par l'utilisateur y est stockée le temps de la requête et lue
 * par le {@code @PrePersist} de {@link com.labo.anapath.common.audit.AuditableEntity}
 * pour estamper automatiquement {@code branch_id} à la création (analogue du
 * {@code creating} du {@code BranchScopeTrait} Laravel).
 * </p>
 * <p>
 * Alimenté par {@link BranchContextFilter} à partir de l'en-tête {@code X-Branch-Id},
 * puis nettoyé en fin de requête pour ne pas fuiter d'un thread réutilisé à l'autre.
 * </p>
 */
public final class BranchContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private BranchContext() {
    }

    /**
     * Positionne la branche active pour le thread courant.
     *
     * @param branchId UUID de la branche sélectionnée et validée
     */
    public static void set(UUID branchId) {
        CURRENT.set(branchId);
    }

    /**
     * Retourne la branche active du thread courant, ou {@code null} si aucune n'est définie.
     *
     * @return UUID de la branche active, ou {@code null}
     */
    public static UUID get() {
        return CURRENT.get();
    }

    /**
     * Efface la branche active du thread courant. À appeler en fin de requête.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
