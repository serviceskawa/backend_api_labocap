package com.labo.anapath.testorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'urgence décidée à l'accueil.
 *
 * <h2>Deux notions qu'il ne faut pas confondre</h2>
 *
 * <p>Le <em>retard</em> se constate : un dossier ouvert depuis plus de dix-huit
 * jours sans compte rendu validé. L'<em>urgence</em> se décide : quelqu'un a
 * coché la case en enregistrant le bon, parce que ce cas doit passer devant.</p>
 *
 * <p>Un dossier peut être l'un, l'autre, les deux, ou aucun. Les fondre en un
 * seul compteur ferait perdre l'information qui déclenche l'action.</p>
 */
class UrgenceMarqueeTest {

    private static boolean urgent(TestOrder demande) throws Exception {
        Method m = TestOrderAssignmentServiceImpl.class
                .getDeclaredMethod("estMarqueUrgent", TestOrder.class);
        m.setAccessible(true);
        var ctor = TestOrderAssignmentServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        // La méthode ne lit que son argument : aucune dépendance à monter.
        Object service = ctor.newInstance(new Object[ctor.getParameterCount()]);
        return (boolean) m.invoke(service, demande);
    }

    private static TestOrder bon(boolean urgent, TestOrderStatus statut) {
        TestOrder t = new TestOrder();
        t.setIsUrgent(urgent);
        t.setStatus(statut);
        return t;
    }

    @Test
    @DisplayName("un bon marqué urgent et non remis l'est")
    void urgentEtEnCours() throws Exception {
        assertThat(urgent(bon(true, TestOrderStatus.PENDING))).isTrue();
        assertThat(urgent(bon(true, TestOrderStatus.VALIDATED))).isTrue();
    }

    @Test
    @DisplayName("une fois remis, il cesse de l'être")
    void remisDoncPlusUrgent() throws Exception {
        // L'urgence portait sur le délai, et ce délai est tenu. La laisser
        // vivre après la remise ferait grossir un compteur que plus rien ne
        // peut faire baisser — et qu'on finirait par ne plus regarder.
        assertThat(urgent(bon(true, TestOrderStatus.DELIVERED))).isFalse();
    }

    @Test
    @DisplayName("un bon annulé n'est pas urgent non plus")
    void annuleDoncPlusUrgent() throws Exception {
        // Un dossier annulé n'attend plus rien de personne.
        assertThat(urgent(bon(true, TestOrderStatus.CANCELLED))).isFalse();
    }

    @Test
    @DisplayName("sans la case cochée, rien n'est urgent")
    void sansLaCase() throws Exception {
        assertThat(urgent(bon(false, TestOrderStatus.PENDING))).isFalse();
        // Le drapeau absent vaut « non urgent », pas une donnée manquante à
        // interpréter : un bon sur deux de la base reprise l'a à nul.
        TestOrder sansDrapeau = new TestOrder();
        sansDrapeau.setIsUrgent(null);
        sansDrapeau.setStatus(TestOrderStatus.PENDING);
        assertThat(urgent(sansDrapeau)).isFalse();
        assertThat(urgent(null)).isFalse();
    }
}
