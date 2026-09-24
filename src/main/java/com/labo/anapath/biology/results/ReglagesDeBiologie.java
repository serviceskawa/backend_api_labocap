package com.labo.anapath.biology.results;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Réglages de biologie de la succursale ({@code setting_apps}, amorcés par V98).
 *
 * <p>Une clé absente ou illisible retombe sur la valeur par défaut de V98 : un
 * réglage mal saisi à l'écran Paramètres ne bloque pas la paillasse. Pour le mode de
 * validation, une valeur inconnue retombe sur {@code TWO_STEP}, le circuit le plus
 * prudent.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReglagesDeBiologie {

    static final String CLE_MODE = "bio_validation_mode";
    static final String CLE_LIBELLES_ANTIBIOGRAMME = "bio_antibiogram_labels";
    static final String CLE_LIBELLES_INDICATEURS = "bio_flag_labels";

    static final Map<String, String> ANTIBIOGRAMME_PAR_DEFAUT = ordonnee(
            "S", "Sensible", "I", "Intermédiaire", "R", "Résistant");
    static final Map<String, String> INDICATEURS_PAR_DEFAUT = ordonnee(
            "L", "Bas", "H", "Haut", "LL", "Critique bas", "HH", "Critique haut");

    private final SettingAppRepository settingAppRepository;
    private final ObjectMapper objectMapper;

    /** @return le circuit de validation de la succursale */
    public BiologyValidationMode mode(UUID branchId) {
        String v = valeur(CLE_MODE, branchId);
        if (v == null || v.isBlank()) {
            return BiologyValidationMode.TWO_STEP;
        }
        try {
            return BiologyValidationMode.valueOf(v.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Réglage {} illisible ({}) pour la succursale {} : TWO_STEP retenu.", CLE_MODE, v, branchId);
            return BiologyValidationMode.TWO_STEP;
        }
    }

    /** @return libellés S/I/R de l'antibiogramme (défauts complétés) */
    public Map<String, String> libellesAntibiogramme(UUID branchId) {
        return libelles(CLE_LIBELLES_ANTIBIOGRAMME, branchId, ANTIBIOGRAMME_PAR_DEFAUT);
    }

    /** @return libellés des indicateurs L/H/LL/HH (défauts complétés) */
    public Map<String, String> libellesIndicateurs(UUID branchId) {
        return libelles(CLE_LIBELLES_INDICATEURS, branchId, INDICATEURS_PAR_DEFAUT);
    }

    private Map<String, String> libelles(String cle, UUID branchId, Map<String, String> defauts) {
        Map<String, String> resultat = new LinkedHashMap<>(defauts);
        String v = valeur(cle, branchId);
        if (v == null || v.isBlank()) {
            return resultat;
        }
        try {
            Map<String, String> lus = objectMapper.readValue(v, new TypeReference<Map<String, String>>() { });
            if (lus != null) {
                lus.forEach((k, l) -> {
                    if (k != null && l != null && !l.isBlank()) {
                        resultat.put(k.strip().toUpperCase(Locale.ROOT), l.strip());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Réglage {} illisible pour la succursale {} : libellés par défaut.", cle, branchId);
        }
        return resultat;
    }

    private String valeur(String cle, UUID branchId) {
        return settingAppRepository.findByKeyAndBranchId(cle, branchId).map(SettingApp::getValue).orElse(null);
    }

    private static Map<String, String> ordonnee(String... paires) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            m.put(paires[i], paires[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
