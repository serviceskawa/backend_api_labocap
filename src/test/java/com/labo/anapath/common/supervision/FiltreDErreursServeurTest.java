package com.labo.anapath.common.supervision;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static com.labo.anapath.common.supervision.CompteurDAlertes.Type.ERREUR_SERVEUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltreDErreursServeurTest {

    private final CompteurDAlertes compteur = new CompteurDAlertes();
    private final FiltreDErreursServeur filtre = new FiltreDErreursServeur(compteur);

    private long erreurs() {
        return compteur.maximumSur(ERREUR_SERVEUR, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("un 500 est compté, un 200 et un 404 ne le sont pas")
    void statuts() throws Exception {
        for (int statut : new int[] {200, 404, 500, 503}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filtre.doFilter(new MockHttpServletRequest("GET", "/api/v1/x"), response,
                    new MockFilterChain() {
                        @Override
                        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                            ((MockHttpServletResponse) res).setStatus(statut);
                        }
                    });
        }
        assertThat(erreurs()).isEqualTo(2);
    }

    @Test
    @DisplayName("une exception échappée est comptée puis relancée")
    void exception() {
        assertThatThrownBy(() -> filtre.doFilter(new MockHttpServletRequest("GET", "/api/v1/x"),
                new MockHttpServletResponse(), (req, res) -> { throw new ServletException("boum"); }))
                .isInstanceOf(ServletException.class);
        assertThat(erreurs()).isEqualTo(1);
    }
}
