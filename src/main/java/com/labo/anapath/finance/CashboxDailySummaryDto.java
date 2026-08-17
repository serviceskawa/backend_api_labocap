package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Encaissements à présenter au caissier lors d'une fermeture de caisse.
 *
 * <p>Montants et nombres sortent de la <b>même</b> requête, sur la même
 * fenêtre. L'écran comptait auparavant les opérations de caisse tandis qu'il
 * sommait les factures : deux sources, deux périodes, et un « 0 » affiché en
 * face de plusieurs millions.</p>
 *
 * @param depuis instant à partir duquel les encaissements sont comptés —
 *               l'ouverture de la session concernée. Restitué pour que l'écran
 *               puisse l'afficher : un caissier qui lit « 3 194 000 » alors
 *               qu'il a encaissé bien moins doit pouvoir constater que la
 *               période couvre plusieurs jours, et non douter de son comptage.
 */
public record CashboxDailySummaryDto(
        BigDecimal totalEspeces,
        BigDecimal totalMobileMoney,
        BigDecimal totalCheques,
        BigDecimal totalVirement,
        BigDecimal total,
        long nombreEspeces,
        long nombreMobileMoney,
        long nombreCheques,
        long nombreVirement,
        LocalDateTime depuis
) {}
