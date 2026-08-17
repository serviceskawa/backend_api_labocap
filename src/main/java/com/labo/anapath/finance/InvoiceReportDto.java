package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de réponse pour le rapport des factures.
 * <p>
 * Réplique la vue Laravel {@code invoice.reports} : pour une période donnée,
 * expose les totaux de ventes, avoirs, chiffre d'affaires (CA = ventes - avoirs)
 * et encaissements (factures de vente payées), avec le détail par contrat.
 * </p>
 * <p>
 * La période peut désormais couvrir plusieurs mois. Dans ce cas {@code months}
 * porte une ligne par mois et les totaux de tête récapitulent l'ensemble ; pour
 * un appel mois unique (paramètres {@code year}/{@code month} historiques),
 * {@code months} est vide et seuls les totaux sont renseignés.
 * </p>
 *
 * <h2>Deux limites que ce rapport hérite du modèle de données</h2>
 * <ul>
 *   <li>Les <b>encaissements</b> sont datés par {@code updated_at} : la table ne
 *       porte aucune date de règlement, {@code paid} n'est qu'un booléen. Une
 *       facture réglée en mars puis modifiée en août compte dans les
 *       encaissements d'août, et le même rapport rejoué plus tard peut donc
 *       rendre d'autres chiffres.</li>
 *   <li>Les <b>ventes</b> sont datées par {@code created_at}, alors que la liste
 *       des factures affiche la colonne {@code date} saisie à la création. Une
 *       facture datée du 31 juillet mais créée le 1er août apparaît en juillet
 *       dans la liste et en août dans le rapport.</li>
 * </ul>
 * <p>
 * Ces deux comportements sont ceux du rapport mensuel existant ; la période les
 * conserve à l'identique pour ne pas déplacer des chiffres déjà lus par le
 * laboratoire. Les corriger relève d'une décision métier, pas d'un ajustement.
 * </p>
 *
 * @param period       libellé de la période, ex. « Mai 2026 » ou « 1 janv. – 15 août 2026 »
 * @param totalSales   somme des factures de vente créées sur la période (status_invoice=0)
 * @param totalCredits somme des avoirs créés sur la période (status_invoice=1)
 * @param turnover     chiffre d'affaires (ventes - avoirs)
 * @param collections  encaissements (ventes payées, cf. limite ci-dessus)
 * @param byContracts  détail des ventes par contrat, sur la période entière
 * @param months       ventilation mensuelle ; vide pour un rapport mono-mois
 */
public record InvoiceReportDto(
        String period,
        BigDecimal totalSales,
        BigDecimal totalCredits,
        BigDecimal turnover,
        BigDecimal collections,
        List<ContractTotal> byContracts,
        List<MonthlyRow> months
) {
    /**
     * Constructeur de compatibilité — rapport mono-mois, sans ventilation.
     * Conservé pour que les appels existants n'aient pas à passer une liste vide.
     */
    public InvoiceReportDto(String period, BigDecimal totalSales, BigDecimal totalCredits,
                            BigDecimal turnover, BigDecimal collections,
                            List<ContractTotal> byContracts) {
        this(period, totalSales, totalCredits, turnover, collections, byContracts, List.of());
    }

    /**
     * Total d'un contrat pour la période.
     *
     * @param contractName nom du contrat, « Sans contrat » si la facture n'est rattachée à aucun
     * @param total        somme des factures de vente créées sur la période pour ce contrat
     */
    public record ContractTotal(String contractName, BigDecimal total) {}

    /**
     * Une ligne du rapport : un mois civil de la période.
     * <p>
     * Les mois sans aucune facture sont présents avec des montants nuls. Les
     * omettre laisserait un trou dans la suite des mois, qui se lirait comme une
     * donnée manquante plutôt que comme un mois sans activité.
     * </p>
     *
     * @param year        année civile
     * @param month       mois civil, de 1 à 12
     * @param label       libellé prêt à afficher, ex. « Mars 2026 »
     * @param sales       ventes du mois
     * @param credits     avoirs du mois
     * @param turnover    chiffre d'affaires du mois (ventes - avoirs)
     * @param collections encaissements du mois
     */
    public record MonthlyRow(
            int year,
            int month,
            String label,
            BigDecimal sales,
            BigDecimal credits,
            BigDecimal turnover,
            BigDecimal collections
    ) {}
}
