package com.labo.anapath.testorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qui rend un dossier en retard aux yeux du médecin.
 *
 * <p>À ne pas confondre avec l'urgence marquée à l'accueil, éprouvée dans
 * {@link UrgenceMarqueeTest} : le retard se constate, l'urgence se décide.</p>
 *
 * <h2>Une seule définition</h2>
 *
 * <p>La même que celle de l'alerte par courriel, mot pour mot : créé il y a plus
 * de {@code app.alerts.report.days} jours, et compte rendu ni validé ni remis.
 * Deux définitions de l'urgence — une pour l'écran, une pour le courriel —
 * finiraient par se contredire, et c'est le médecin qui arbitrerait entre un
 * écran rouge et une boîte aux lettres muette.</p>
 */
class UrgenceDuDossierTest {

    private static final int SEUIL = 18;

    private static boolean enRetard(TestOrder demande, String etatCompteRendu)
            throws Exception {
        // Neuf dépendances, aucune touchée : « estUrgent » ne lit qu'une date
        // et une chaîne. Les monter serait du décor.
        var service = new TestOrderAssignmentServiceImpl(
                null, null, null, null, null, null, null, null, null);
        var champ = TestOrderAssignmentServiceImpl.class
                .getDeclaredField("joursAvantAlerte");
        champ.setAccessible(true);
        champ.set(service, SEUIL);
        Method m = TestOrderAssignmentServiceImpl.class.getDeclaredMethod(
                "estEnRetard", TestOrder.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, demande, etatCompteRendu);
    }

    private static TestOrder demandeAgeeDe(int jours) throws Exception {
        TestOrder t = new TestOrder();
        var champ = TestOrder.class.getSuperclass().getDeclaredField("createdAt");
        champ.setAccessible(true);
        champ.set(t, LocalDateTime.now().minusDays(jours));
        return t;
    }

    @Test
    @DisplayName("passé le délai sans compte rendu terminé, le dossier est urgent")
    void auDelaDuSeuil() throws Exception {
        assertThat(enRetard(demandeAgeeDe(SEUIL + 5), "DRAFT")).isTrue();
        assertThat(enRetard(demandeAgeeDe(SEUIL + 5), "PENDING_REVIEW")).isTrue();
        // Aucun compte rendu du tout : c'est le cas le plus en retard.
        assertThat(enRetard(demandeAgeeDe(SEUIL + 5), null)).isTrue();
    }

    @Test
    @DisplayName("un compte rendu validé éteint l'urgence, quel que soit l'âge")
    void leCompteRenduTermineEteintTout() throws Exception {
        // C'est le travail attendu qui est fait : le dossier peut dormir.
        assertThat(enRetard(demandeAgeeDe(400), "VALIDATED")).isFalse();
        assertThat(enRetard(demandeAgeeDe(400), "DELIVERED")).isFalse();
    }

    @Test
    @DisplayName("en deçà du délai, rien n'est urgent")
    void enDecaDuSeuil() throws Exception {
        // Sans cette borne, tout dossier ouvert serait rouge dès le premier
        // jour — et une alerte permanente n'alerte plus de rien.
        assertThat(enRetard(demandeAgeeDe(1), "DRAFT")).isFalse();
        assertThat(enRetard(demandeAgeeDe(SEUIL - 1), null)).isFalse();
    }

    @Test
    @DisplayName("une demande sans date n'est pas déclarée urgente")
    void sansDate() throws Exception {
        // Prudence : une date absente est une donnée manquante, pas un retard.
        // La traiter comme un retard peindrait en rouge des dossiers sains.
        assertThat(enRetard(new TestOrder(), "DRAFT")).isFalse();
        assertThat(enRetard(null, null)).isFalse();
    }
}
