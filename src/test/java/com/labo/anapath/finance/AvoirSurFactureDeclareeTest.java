package com.labo.anapath.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qui fait qu'une facture est « déclarée ».
 *
 * <h2>Pourquoi trois marques</h2>
 *
 * <p>Deux parcours coexistent. La saisie manuelle héritée de Laravel pose
 * {@code codeNormalise} ; la passerelle FluidInvoice pose {@code codeMecef} et
 * {@code fluidinvoiceId}. N'en regarder qu'une laisserait passer les factures
 * déclarées par l'autre chemin — et l'on refuserait un avoir légitime, ou l'on
 * en accepterait un sur une facture qui n'a jamais rien déclaré.</p>
 */
class AvoirSurFactureDeclareeTest {

    private static boolean declaree(Invoice f) throws Exception {
        Method m = InvoiceServiceImpl.class.getDeclaredMethod("estNormalisee", Invoice.class);
        m.setAccessible(true);
        // La méthode ne lit que son argument : aucune dépendance à monter.
        var ctor = InvoiceServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object service = ctor.newInstance(new Object[ctor.getParameterCount()]);
        return (boolean) m.invoke(service, f);
    }

    private static Invoice facture() {
        return new Invoice();
    }

    @Test
    @DisplayName("une facture sans aucune marque n'est pas déclarée")
    void aucuneMarque() throws Exception {
        assertThat(declaree(facture())).isFalse();
    }

    @Test
    @DisplayName("chacune des trois marques suffit")
    void chaqueMarqueCompte() throws Exception {
        Invoice parLaPasserelle = facture();
        parLaPasserelle.setFluidinvoiceId("b3c1-…");
        assertThat(declaree(parLaPasserelle)).isTrue();

        Invoice avecMecef = facture();
        avecMecef.setCodeMecef("QOOH-IVEO-MYFF-3YOC-PNK7-WTSH");
        assertThat(declaree(avecMecef)).isTrue();

        // Le parcours d'autrefois : un code saisi à la main.
        Invoice saisieManuelle = facture();
        saisieManuelle.setCodeNormalise("111111111111111111111031");
        assertThat(declaree(saisieManuelle)).isTrue();
    }

    @Test
    @DisplayName("une marque faite d'espaces ne déclare rien")
    void leBlancNeDeclarePas() throws Exception {
        // Une colonne remplie d'espaces existe dans la base reprise de Laravel.
        // La prendre pour une déclaration autoriserait un avoir sur une facture
        // que la DGI n'a jamais vue.
        Invoice f = facture();
        f.setCodeNormalise("   ");
        f.setCodeMecef("");
        assertThat(declaree(f)).isFalse();
    }
}
