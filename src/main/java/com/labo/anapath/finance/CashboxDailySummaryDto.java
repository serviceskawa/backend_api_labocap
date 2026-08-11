package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Encaissements à présenter au caissier lors d'une fermeture de caisse.
 *
 * @param depuis instant à partir duquel les encaissements sont comptés —
 *               l'ouverture de la session concernée. Restitué pour que l'écran
 *               puisse l'afficher : un caissier qui lit « 2 100 000 » alors
 *               qu'il a encaissé 876 000 doit pouvoir constater que la période
 *               couvre plusieurs jours, et non douter de son propre comptage.
 */
public record CashboxDailySummaryDto(
        BigDecimal totalEspeces,
        BigDecimal totalMobileMoney,
        BigDecimal totalCheques,
        BigDecimal totalVirement,
        BigDecimal total,
        LocalDateTime depuis
) {}
