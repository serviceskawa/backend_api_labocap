package com.labo.anapath.testorder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * Les étiquettes d'une ligne d'affectation, entre la base et le reste du code.
 *
 * <p>Elles sont rangées en tableau JSON dans une colonne texte. La lecture et
 * l'écriture vivaient dans le service des affectations, en méthodes privées —
 * il a suffi qu'un second appelant en ait besoin, le suivi d'un dossier, pour
 * que la question se pose de les recopier. Un seul endroit vaut mieux : deux
 * décodages finissent par diverger sur le cas limite, et c'est toujours celui
 * qu'on n'avait pas prévu.</p>
 *
 * <p>Ni l'un ni l'autre ne lève. Une colonne illisible rend une liste vide : le
 * suivi d'un dossier ne doit pas s'interrompre parce qu'une étiquette a été
 * mal écrite un jour.</p>
 */
public final class Etiquettes {

    /** Au-delà, ce n'est plus une étiquette collée sur un contenant. */
    static final int LONGUEUR_MAX = 40;

    private Etiquettes() {}

    /**
     * Sérialise, ou rend nul.
     *
     * <p>Une liste vide s'enregistre comme nulle plutôt que comme « [] » : les
     * deux se lisent pareil, et un nul distingue à l'œil, en base, une
     * affectation sans étiquette d'une affectation antérieure à leur
     * existence.</p>
     */
    public static String encoder(ObjectMapper mapper, List<String> etiquettes) {
        if (etiquettes == null || etiquettes.isEmpty()) return null;
        List<String> propres = etiquettes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();
        if (propres.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(propres);
        } catch (JsonProcessingException e) {
            throw new com.labo.anapath.common.exception.BusinessException(
                    "Erreur de sérialisation des étiquettes");
        }
    }

    /** Relit le tableau, ou rend une liste vide. */
    @SuppressWarnings("unchecked")
    public static List<String> decoder(ObjectMapper mapper, String brut) {
        if (brut == null || brut.isBlank()) return List.of();
        try {
            return mapper.readValue(brut, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
