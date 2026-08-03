package com.labo.anapath.setting;

import com.labo.anapath.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identité visuelle de l'application, exposée <b>sans authentification</b>.
 * <p>
 * Le front a besoin du logo et du nom du labo <i>avant</i> toute connexion (écrans de
 * login, mot de passe oublié, réinitialisation) — moments où aucun JWT n'existe et où
 * {@code GET /api/v1/setting-apps} est donc inaccessible. Ce contrôleur comble ce trou,
 * et corrige au passage un effet de bord : {@code /setting-apps} est gardé par
 * {@code view-settings}, si bien qu'un utilisateur sans cette permission (un technicien,
 * par exemple) ne recevait jamais le logo — même une fois connecté.
 * </p>
 * <p>
 * <b>Surface volontairement minimale.</b> Seules les clés de {@link #PUBLIC_KEYS} sont
 * renvoyées : ce sont des éléments d'habillage déjà visibles par n'importe quel visiteur
 * de la page de connexion. La table {@code setting_apps} contient par ailleurs des
 * réglages sensibles (identifiants SMTP, jetons d'API…) qui ne doivent jamais transiter
 * par cette route — d'où une liste blanche explicite plutôt qu'une liste noire.
 * </p>
 * <p>
 * <b>Choix de la branche.</b> Sans utilisateur authentifié, aucune branche active n'est
 * connue (pas d'en-tête {@code X-Branch-Id} sur l'écran de login). On retient donc, pour
 * chaque clé, la valeur de la branche la plus ancienne — la branche mère, dont l'identité
 * visuelle porte la marque du laboratoire. Une fois connecté et la branche choisie, le
 * front repasse sur {@code /setting-apps} et affiche l'habillage propre à cette branche.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/public/branding")
@RequiredArgsConstructor
public class PublicBrandingController {

    /**
     * Liste blanche des clés {@code setting_apps} publiables. Toute clé absente d'ici
     * reste réservée aux appels authentifiés portant la permission {@code view-settings}.
     */
    static final Set<String> PUBLIC_KEYS = Set.of("app_name", "logo", "logo_white", "favicon");

    private final SettingAppRepository settingAppRepository;

    /**
     * Renvoie l'habillage public sous forme de map clé → valeur, dans le même format que
     * {@code /setting-apps} côté front (une simple map, pas une page).
     * <p>
     * Réponse mise en cache 5 minutes : les logos sont stockés en data-URI et pèsent lourd,
     * alors qu'ils ne changent qu'à la main depuis l'écran Paramètres.
     * </p>
     *
     * @return les clés publiques renseignées (les valeurs vides sont omises)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> branding() {
        List<SettingApp> rows = settingAppRepository.findByKeyInOrderByCreatedAtAsc(PUBLIC_KEYS);

        Map<String, String> branding = new LinkedHashMap<>();
        for (SettingApp row : rows) {
            String value = row.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            // La requête est triée par ancienneté : pour une clé donnée, le premier
            // gagnant est la valeur de la branche mère. putIfAbsent fige ce choix.
            branding.putIfAbsent(row.getKey(), value);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.success(branding));
    }
}
