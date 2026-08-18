package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Normalisation d'une facture par FluidInvoice.
 *
 * <p>La passerelle enchaîne les trois étapes e-MCF et rend, en un appel, le code
 * MECeF/DGI, les éléments de sécurité et le lien vers le document normalisé.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FluidInvoiceServiceImpl implements FluidInvoiceService {

    /** Type e-MCF d'une facture de vente. */
    private static final String TYPE_VENTE = "FV";

    /** Type e-MCF d'un avoir. */
    private static final String TYPE_AVOIR = "FA";

    /** Valeur de {@code statusInvoice} marquant un avoir. */
    private static final int STATUT_AVOIR = 1;

    private final InvoiceRepository invoiceRepository;
    private final FluidInvoiceClient client;
    private final FluidInvoiceProperties properties;
    private final FinanceMapper financeMapper;

    @Override
    @Transactional
    public InvoiceResponseDto normaliser(UUID invoiceId, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));

        if (!branchId.equals(invoice.getBranchId())) {
            throw new ResourceNotFoundException("Facture", invoiceId);
        }
        // Une facture normalisée l'est définitivement : la renvoyer produirait un
        // second document fiscal pour une seule vente.
        if (invoice.getNormalizedUrl() != null) {
            throw new InvalidOperationException("Cette facture est déjà normalisée.");
        }

        boolean estAvoir = invoice.getStatusInvoice() == STATUT_AVOIR;
        FluidInvoiceRequestDto payload = construirePayload(invoice, estAvoir);

        // Clé stable, dérivée de la facture : un double clic, un rejeu réseau ou
        // une reprise après timeout retombent sur la même facture chez l'éditeur.
        String idempotencyKey = "labocap-" + invoice.getId();

        FluidInvoiceResponseDto reponse = estAvoir
                ? client.emettreAvoir(payload, idempotencyKey)
                : client.emettre(payload, idempotencyKey);

        appliquer(invoice, reponse);
        return financeMapper.toInvoiceResponseDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] telechargerDocument(UUID invoiceId, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));

        if (!branchId.equals(invoice.getBranchId())) {
            throw new ResourceNotFoundException("Facture", invoiceId);
        }
        if (invoice.getFluidinvoiceId() == null) {
            throw new InvalidOperationException(
                    "Cette facture n'a pas été normalisée par FluidInvoice.");
        }
        return client.telechargerDocument(invoice.getFluidinvoiceId());
    }

    // -------------------------------------------------------------------------
    // Construction de la charge utile
    // -------------------------------------------------------------------------

    private FluidInvoiceRequestDto construirePayload(Invoice invoice, boolean estAvoir) {
        List<FluidInvoiceRequestDto.Item> items = lignes(invoice);
        if (items.isEmpty()) {
            throw new InvalidOperationException(
                    "Une facture sans ligne ne peut pas être normalisée.");
        }

        String reference = null;
        String originalInvoiceId = null;
        if (estAvoir) {
            Invoice originale = invoice.getReference();
            if (originale == null) {
                throw new InvalidOperationException(
                        "Cet avoir n'est rattaché à aucune facture de vente.");
            }
            // L'éditeur accepte l'un ou l'autre. On envoie le code MECeF quand la
            // vente a été normalisée hors FluidInvoice (ancien flux direct SYGMEF),
            // et l'identifiant FluidInvoice sinon.
            reference = originale.getCodeMecef();
            originalInvoiceId = originale.getFluidinvoiceId();
            if (reference == null && originalInvoiceId == null) {
                throw new InvalidOperationException(
                        "La facture de vente d'origine doit être normalisée avant son avoir.");
            }
        }

        return new FluidInvoiceRequestDto(
                estAvoir ? TYPE_AVOIR : TYPE_VENTE,
                items,
                acheteur(invoice),
                new FluidInvoiceRequestDto.Operator(properties.getOperatorName()),
                reglement(invoice),
                reference,
                originalInvoiceId);
    }

    /**
     * Les lignes de la facture.
     *
     * <p>{@code price} porte le prix unitaire <b>remise déduite</b> : l'API n'a
     * pas de champ remise, et {@code InvoiceDetail.total} vaut déjà
     * {@code max(0, prix - remise)} pour une quantité toujours égale à 1.</p>
     */
    private List<FluidInvoiceRequestDto.Item> lignes(Invoice invoice) {
        List<FluidInvoiceRequestDto.Item> items = new ArrayList<>();
        if (invoice.getDetails() == null) {
            return items;
        }
        for (InvoiceDetail detail : invoice.getDetails()) {
            items.add(new FluidInvoiceRequestDto.Item(
                    detail.getTestName(),
                    enFrancs(detail.getTotal()),
                    BigDecimal.valueOf(Math.max(1, detail.getQuantity())),
                    properties.getTaxGroup()));
        }
        return items;
    }

    /**
     * L'acheteur, ou null s'il n'est pas identifié.
     *
     * <p>Sans IFU : les patients n'en ont pas, et l'IFU du laboratoire est celui
     * du vendeur — il est porté par le compte associé à la clé API, jamais par
     * cette charge utile.</p>
     */
    private FluidInvoiceRequestDto.Client acheteur(Invoice invoice) {
        String nom = invoice.getClientName();
        if (nom == null || nom.isBlank()) {
            return null;
        }
        return new FluidInvoiceRequestDto.Client(null, nom, null, invoice.getClientAddress());
    }

    /** Le règlement, ou null tant que la facture n'est pas payée. */
    private List<FluidInvoiceRequestDto.Payment> reglement(Invoice invoice) {
        if (invoice.getPayment() == null || invoice.getPayment().isBlank()) {
            return null;
        }
        return List.of(new FluidInvoiceRequestDto.Payment(
                invoice.getPayment(), enFrancs(invoice.getTotal())));
    }

    /** Montant en francs CFA entiers, l'API n'acceptant pas de décimales. */
    private long enFrancs(BigDecimal montant) {
        return montant == null ? 0L : montant.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    // -------------------------------------------------------------------------
    // Report de la réponse sur la facture
    // -------------------------------------------------------------------------

    private void appliquer(Invoice invoice, FluidInvoiceResponseDto reponse) {
        invoice.setFluidinvoiceId(reponse.getId());

        // L'API ne renvoie aucun lien — ni à la création, ni sur la fiche
        // détaillée : sa réponse se limite à id, uid, status, type,
        // integrity_hash et created_at (vérifié sur l'environnement de test).
        // Le document vit en revanche derrière /v1/invoices/{id}/pdf, endpoint
        // non documenté. On construit donc l'adresse à partir de l'identifiant
        // rendu, en gardant la priorité à un lien explicite si l'éditeur venait
        // à en publier un.
        String lien = reponse.documentUrl();
        if (lien == null) {
            lien = client.urlDocument(reponse.getId());
        }
        invoice.setNormalizedUrl(lien);

        FluidInvoiceResponseDto.Security securite = reponse.getSecurity();
        if (securite != null) {
            invoice.setCodeMecef(securite.getCodeMECeFDGI());
            invoice.setCounters(securite.getCounters());
            invoice.setDateGenerate(securite.getDateTime());
            invoice.setNim(securite.getNim());
            invoice.setQrcode(securite.getQrCode());
            // `codeNormalise` n'est délibérément pas touché : il appartient à
            // « Terminer la facture », fonctionnalité distincte de celle-ci.
            // Les deux champs se ressemblent mais ne relèvent pas du même geste
            // métier — l'un est saisi par le caissier à l'encaissement, l'autre
            // rendu par la DGI à la normalisation.
        }

        log.info("Facture {} normalisée — codeMecef={}, document={}",
                invoice.getCode(), reponse.codeMecef(), lien);
    }
}
