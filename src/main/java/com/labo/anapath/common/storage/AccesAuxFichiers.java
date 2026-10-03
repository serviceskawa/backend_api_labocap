package com.labo.anapath.common.storage;

import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * La règle de lecture d'un fichier : celle de l'entité qui le possède.
 *
 * <p>Permission métier d'abord — la même que celle du {@code GET} de l'entité,
 * pour qu'un écran qui a le droit de lister les dépenses ait celui d'en ouvrir
 * la preuve, et pas plus. Agence ensuite : la route des fichiers s'ouvre en
 * navigation directe (nouvel onglet), sans l'en-tête {@code X-Branch-Id} ; on
 * vérifie donc que la personne a accès à l'agence du fichier, et non qu'elle y
 * travaille à l'instant.</p>
 */
@Component
@RequiredArgsConstructor
public class AccesAuxFichiers {

    /** Type d'entité → permission exigée par le GET de cette entité. */
    static final Map<String, String> PERMISSION_PAR_TYPE = Map.ofEntries(
            Map.entry(FichierStocke.TEST_ORDER, "view-test-orders"),
            Map.entry(FichierStocke.DISCUSSION_MESSAGE, "view-test-orders"),
            Map.entry(FichierStocke.CONSULTATION_FILE, "view-consultations"),
            Map.entry(FichierStocke.EMPLOYEE, "view-employees"),
            Map.entry(FichierStocke.EMPLOYEE_DOCUMENT, "view-employees"),
            Map.entry(FichierStocke.DOC, "view-documentation-categories"),
            Map.entry(FichierStocke.DOC_VERSION, "view-documentation-categories"),
            Map.entry(FichierStocke.EXPENSE, "view-expence-details"),
            Map.entry(FichierStocke.REFUND_REQUEST, "view-refund-requests"),
            Map.entry(FichierStocke.BANK_DEPOSIT, "view-cashbox-adds"),
            Map.entry(FichierStocke.CASHBOX_VOUCHER, "view-cashbox-tickets"));

    private final BranchRepository branchRepository;

    /** Lève {@link AccessDeniedException} (403) si la personne ne peut pas lire ce fichier. */
    public void exiger(FichierStocke fichier, UserPrincipal personne) {
        String permission = PERMISSION_PAR_TYPE.get(fichier.getEntityType());
        // Un type que ce composant ne connaît pas est un oubli de code : on
        // refuse plutôt que de servir, c'est le sens de tout ce lot.
        boolean autorise = permission != null && personne.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permission::equals);
        if (!autorise) {
            throw new AccessDeniedException("Vous n'avez pas le droit de consulter ce fichier.");
        }
        if (!branchRepository.hasBranchAccess(personne.getId(), fichier.getBranchId())) {
            throw new AccessDeniedException("Ce fichier appartient à une autre agence.");
        }
    }
}
