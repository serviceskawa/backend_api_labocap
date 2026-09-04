package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
    private final ApplicationEventPublisher eventPublisher;
    private final InvoiceService invoiceService;

    @Override
    @Transactional
    public InvoiceResponseDto normaliser(UUID invoiceId, UUID branchId,
                                         String modeDePaiement) {
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


        // Encaisser AVANT de déclarer, et non l'inverse.
        //
        // La charge utile porte un bloc « payment » que le serveur ne lit que
        // sur la facture : tant qu'elle n'est pas réglée, ce bloc est absent
        // et la DGI reçoit une facture annoncée non réglée. Déclarer d'abord
        // enverrait une déclaration fausse, qu'aucun encaissement ultérieur ne
        // corrigerait — un document fiscal ne se reprend pas.
        //
        // Un avoir échappe à cela : il contrepasse, il n'encaisse rien.
        if (invoice.getStatusInvoice() != STATUT_AVOIR
                && !Boolean.TRUE.equals(invoice.getPaid())) {
            if (modeDePaiement == null || modeDePaiement.isBlank()) {
                throw new InvalidOperationException(
                        "Le mode de paiement est requis : il part avec la "
                        + "déclaration à la DGI.");
            }
            InvoiceStatusUpdateDto reglement = new InvoiceStatusUpdateDto();
            reglement.setPayment(modeDePaiement.trim());
            // On passe par l'encaissement plutôt que de poser les champs à la
            // main : lui seul crédite la caisse, trace l'opération et clôt le
            // contrat à facture unique. Recopier cette logique ici la ferait
            // diverger au premier changement.
            invoiceService.markAsPaid(invoiceId, reglement, branchId);
            invoice = invoiceRepository.findById(invoiceId).orElseThrow();
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
        Invoice normalisee = invoiceRepository.save(invoice);

        // Le client est prévenu par SMS une fois la transaction validée : voir
        // InvoiceValidatedEvent pour ce que coûterait un envoi synchrone. Le SMS
        // n'est pas dupliqué si la facture avait déjà été encaissée.
        eventPublisher.publishEvent(new InvoiceValidatedEvent(normalisee.getId()));

        return financeMapper.toInvoiceResponseDto(normalisee);
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

    /**
     * Première valeur réellement renseignée, ou {@code null} si aucune ne l'est.
     *
     * <p>Une chaîne vide n'est pas une valeur : la reprise des données Laravel a
     * laissé des colonnes vides là où l'absence se lirait mieux en {@code NULL},
     * et les transmettre telles quelles fait échouer l'appel côté éditeur.</p>
     */
    private static String premierRenseigne(String... valeurs) {
        for (String valeur : valeurs) {
            if (StringUtils.hasText(valeur)) {
                return valeur;
            }
        }
        return null;
    }

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
            //
            // Le code de normalisation vit dans deux colonnes selon le chemin
            // emprunté : code_mecef quand la machine e-MECeF a répondu, et
            // code_normalise quand le caissier l'a saisi à la main, normalisation
            // automatique désactivée (voir InvoiceServiceImpl#markAsPaid). Ne
            // regarder que la première laissait sans référence des avoirs dont la
            // vente était pourtant bien normalisée.
            reference = premierRenseigne(originale.getCodeMecef(), originale.getCodeNormalise());
            originalInvoiceId = StringUtils.hasText(originale.getFluidinvoiceId())
                    ? originale.getFluidinvoiceId()
                    : null;

            // Le vide compte comme l'absence : les factures reprises de Laravel
            // portent une chaîne vide plutôt que NULL, et une référence vide
            // partait telle quelle chez l'éditeur, qui la refusait par un
            // MISSING_REFERENCE incompréhensible pour le caissier.
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
                    // Idem que sur le document imprimé : un seul libellé.
                    detail.nomAFacturer(),
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
            // Le champ hérité reçoit le même code.
            //
            // Deux parcours mènent à une facture déclarée : la saisie manuelle
            // d'autrefois, qui renseigne « codeNormalise », et cette
            // passerelle. Tout ce qui lit l'ancien champ — le document
            // imprimé, la recherche par code — ignorerait les factures venues
            // d'ici si on ne le remplissait pas. On ne l'écrase jamais : une
            // saisie humaine antérieure fait foi.
            if (invoice.getCodeNormalise() == null || invoice.getCodeNormalise().isBlank()) {
                invoice.setCodeNormalise(securite.getCodeMECeFDGI());
            }
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
