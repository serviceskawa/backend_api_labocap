package com.labo.anapath.common.module;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Modules optionnels activés pour ce déploiement, lus sous le préfixe {@code app.modules}.
 *
 * <p>Chaque laboratoire a son propre déploiement : un module s'active par variable
 * d'environnement et redémarrage, sans reconstruire l'image. Le schéma, lui, est le
 * même partout — les migrations Flyway du module s'appliquent qu'il soit activé ou non.
 *
 * <pre>{@code
 * app:
 *   modules:
 *     biology: ${MODULE_BIOLOGY:false}
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "app.modules")
@Getter
@Setter
public class ModulesProperties {

    /** Biologie clinique. Désactivée par défaut : le déploiement reste purement anapath. */
    private boolean biology = false;
}
