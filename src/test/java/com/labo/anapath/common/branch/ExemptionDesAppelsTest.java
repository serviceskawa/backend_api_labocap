package com.labo.anapath.common.branch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La signalisation des appels échappe au choix de branche.
 *
 * <h2>Ce que l'absence d'exemption a coûté</h2>
 *
 * <p>La poignée de main WebSocket était authentifiée par le filtre JWT, puis
 * refusée en 428 par le filtre de branche — faute d'un en-tête {@code
 * X-Branch-Id} qu'une ouverture de WebSocket ne peut pas porter. Le code des
 * appels n'était jamais atteint : aucune trace, ni de la liaison, ni du refus.
 * On a cherché du côté du proxy, du pare-feu et du relais avant de trouver.</p>
 *
 * <h2>Pourquoi c'est sans risque</h2>
 *
 * <p>La branche n'est pas perdue : la poignée de main la lit dans le jeton.
 * Choisir une branche parmi plusieurs est un geste du web ; un appareil mobile
 * est enrôlé sur une seule et n'en change jamais.</p>
 */
class ExemptionDesAppelsTest {

    private static boolean exempte(String chemin) throws Exception {
        Method m = BranchContextFilter.class.getDeclaredMethod(
                "isExempt", jakarta.servlet.http.HttpServletRequest.class);
        m.setAccessible(true);
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.setRequestURI(chemin);
        // Aucune dépendance n'est touchée par ce seul contrôle de chemin.
        return (boolean) m.invoke(new BranchContextFilter(null, null), requete);
    }

    @Test
    @DisplayName("la signalisation des appels est exemptée")
    void lesAppelsSontExemptes() throws Exception {
        assertThat(exempte("/ws/appels")).isTrue();
    }

    @Test
    @DisplayName("les routes ordinaires exigent toujours une branche")
    void leResteNeLEstPas() throws Exception {
        // L'exemption retire un contrôle d'isolation entre laboratoires : elle
        // doit rester étroite. L'élargir par mégarde à « /w » ou à la racine
        // ouvrirait bien plus que la signalisation.
        assertThat(exempte("/api/v1/test-orders")).isFalse();
        assertThat(exempte("/api/v1/reports/123")).isFalse();
        assertThat(exempte("/wsx/quelque-chose")).isFalse();
    }
}
