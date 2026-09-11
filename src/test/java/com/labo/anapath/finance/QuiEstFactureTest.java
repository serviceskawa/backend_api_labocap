package com.labo.anapath.finance;

import com.labo.anapath.patient.Patient;
import com.labo.anapath.testorder.TestOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qui la facture nomme, et pourquoi ce n'est pas toujours le patient.
 *
 * <h2>Soigner et payer sont deux choses</h2>
 *
 * <p>Une clinique adresse son patient au laboratoire et règle l'examen. La
 * facture doit alors porter son nom, son adresse et son IFU, et la déclaration
 * à la DGI dire la même chose. Jusqu'ici deux voies seulement existaient : la
 * facture au patient, ou bien au client d'un contrat groupé — ce qui impose une
 * facture cumulative et ne se décide pas demande par demande.</p>
 *
 * <h2>Ce qui rendait la chose fragile</h2>
 *
 * <p>La validation d'un bon réécrit l'identité de la facture depuis le patient,
 * à chaque fois. Une adresse de facturation choisie au moment de déclarer
 * disparaissait donc au geste suivant, sans que rien ne le signale — d'où la
 * marque qui la fige.</p>
 */
class QuiEstFactureTest {

    /** Le service réduit à ce que cette règle lui demande : rien. */
    private static void poser(Invoice facture, TestOrder demande) throws Exception {
        Class<?> classe = Class.forName(
                "com.labo.anapath.testorder.TestOrderServiceImpl");
        Method m = classe.getDeclaredMethod(
                "poserLIdentiteDeFacturation", Invoice.class, TestOrder.class);
        m.setAccessible(true);
        var ctor = classe.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object service = ctor.newInstance(new Object[ctor.getParameterCount()]);
        m.invoke(service, facture, demande);
    }

    private static TestOrder demandeDe(String nomEtablissement, String ifu) {
        Patient p = new Patient();
        p.setLastname("DOTOU");
        p.setFirstname("Justine");
        p.setAdresse("Cotonou, Akpakpa");
        TestOrder t = new TestOrder();
        t.setPatient(p);
        t.setFactureANom(nomEtablissement);
        t.setFactureAAdresse(nomEtablissement == null ? null : "Cotonou, Ganhi");
        t.setFactureAIfu(ifu);
        return t;
    }

    @Test
    @DisplayName("sans établissement nommé, la facture va au patient")
    void leCasOrdinaire() throws Exception {
        Invoice f = new Invoice();
        poser(f, demandeDe(null, null));

        assertThat(f.getClientName()).isEqualTo("DOTOU Justine");
        assertThat(f.getClientAddress()).isEqualTo("Cotonou, Akpakpa");
        // Un patient n'a pas d'IFU, et en laisser un d'une facturation
        // précédente déclarerait cette vente au nom d'un établissement qu'elle
        // ne concerne plus.
        assertThat(f.getClientIfu()).isNull();
    }

    @Test
    @DisplayName("un établissement nommé prend la place du patient")
    void laCliniquePaie() throws Exception {
        Invoice f = new Invoice();
        poser(f, demandeDe("CLINIQUE LA PROVIDENCE", "3201900123456"));

        assertThat(f.getClientName()).isEqualTo("CLINIQUE LA PROVIDENCE");
        assertThat(f.getClientAddress()).isEqualTo("Cotonou, Ganhi");
        assertThat(f.getClientIfu()).isEqualTo("3201900123456");
    }

    @Test
    @DisplayName("un établissement sans IFU reste facturable")
    void sansIfu() throws Exception {
        // La DGI n'exige l'identifiant que pour désigner un acheteur
        // professionnel ; refuser la facture faute d'IFU bloquerait une vente
        // que rien n'empêche.
        Invoice f = new Invoice();
        poser(f, demandeDe("CENTRE DE SANTÉ SAINT-JEAN", null));

        assertThat(f.getClientName()).isEqualTo("CENTRE DE SANTÉ SAINT-JEAN");
        assertThat(f.getClientIfu()).isNull();
    }

    @Test
    @DisplayName("une identité saisie à la main n'est jamais réécrite")
    void lIdentiteFigeeTientBon() throws Exception {
        Invoice f = new Invoice();
        f.setClientName("CLINIQUE LOUIS PASTEUR");
        f.setClientAddress("Porto-Novo");
        f.setClientIfu("3201900999999");
        f.setFacturationFigee(true);

        // Le bon est revalidé : sans la marque, le patient reprendrait sa place
        // et la facture changerait de destinataire en silence.
        poser(f, demandeDe(null, null));

        assertThat(f.getClientName()).isEqualTo("CLINIQUE LOUIS PASTEUR");
        assertThat(f.getClientAddress()).isEqualTo("Porto-Novo");
        assertThat(f.getClientIfu()).isEqualTo("3201900999999");
    }
}
