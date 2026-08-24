package com.labo.anapath.testorder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Une ligne de la file de travail d'un médecin.
 *
 * <p>Elle porte trois informations que la maquette distingue soigneusement, et
 * qu'il ne faut pas mélanger :</p>
 *
 * <ul>
 *   <li>{@code labels} — les étiquettes du prélèvement, propres à cette
 *       demande ;</li>
 *   <li>{@code note} — la consigne écrite pour cette demande ;</li>
 *   <li>{@code assignmentNote} — celle du lot entier, partagée par toutes ses
 *       demandes, et donc jamais répétée en clair sur chaque ligne.</li>
 * </ul>
 *
 * @param detailId       la ligne d'affectation, seule chose qu'on puisse
 *                       désigner pour changer son statut
 * @param testOrderId    la demande, pour ouvrir sa fiche
 * @param code           le code de la demande, ce que le médecin lit
 * @param patientName    nom puis prénoms, comme partout
 * @param docteurStatus  où en est le médecin — jamais le statut du compte rendu
 * @param reportStatus   où en est le document, qui avance à son propre rythme
 * @param assignmentCode le lot d'où vient cette demande
 * @param assignmentDate la date du lot, qui donne son rang dans la file
 */
public record DemandeDuMedecinDto(
        UUID detailId,
        UUID testOrderId,
        String code,
        String patientName,
        String docteurStatus,
        String reportStatus,
        List<String> labels,
        String note,
        String assignmentCode,
        LocalDate assignmentDate,
        String assignmentNote) {
}
