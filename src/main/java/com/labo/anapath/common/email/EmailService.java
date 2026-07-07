package com.labo.anapath.common.email;

public interface EmailService {

    /**
     * Envoie le code OTP 2FA par email de manière asynchrone.
     *
     * @param to        adresse email du destinataire
     * @param firstname prénom pour personnaliser le message
     * @param otp       code OTP à 6 chiffres en clair (affiché dans l'email)
     */
    void sendOtp(String to, String firstname, String otp);

    /**
     * Envoie l'alerte "macro non faite" pour une demande d'examen.
     *
     * @param to            adresse email du destinataire (technicien)
     * @param testOrderCode code de la demande d'examen concernée
     * @param labName       nom du laboratoire (en-tête / signature)
     */
    void sendMacroAlert(String to, String testOrderCode, String labName);

    /**
     * Notifie les administrateurs qu'une demande de congé vient d'être déposée.
     * (Réplique Laravel : {@code NotificationAdminTimeOffMail}, déclenché à la création.)
     *
     * @param to           adresse email d'un administrateur
     * @param employeeName nom complet de l'employé demandeur
     * @param startDate    date de début du congé (texte déjà formaté)
     * @param endDate      date de fin du congé (texte déjà formaté)
     * @param labName      nom du laboratoire (en-tête / signature)
     */
    void sendTimeoffRequestToAdmin(String to, String employeeName,
                                   String startDate, String endDate, String labName);

    /**
     * Notifie l'employé que sa demande de congé a été reçue et traitée (validée).
     * (Réplique Laravel : {@code NotificationEmployeTimeOffMail}, déclenché à l'approbation.)
     *
     * @param to           adresse email de l'employé
     * @param employeeName nom complet de l'employé
     * @param startDate    date de début du congé (texte déjà formaté)
     * @param endDate      date de fin du congé (texte déjà formaté)
     * @param labName      nom du laboratoire (en-tête / signature)
     */
    void sendTimeoffApprovedToEmployee(String to, String employeeName,
                                       String startDate, String endDate, String labName);

    /**
     * Notifie un utilisateur qu'il a été ajouté comme relecteur d'un compte-rendu.
     * (Réplique Laravel : {@code AssignedReviewMail}.)
     *
     * @param to            adresse email du relecteur
     * @param reviewerName  nom complet du relecteur
     * @param reportTitle   titre / référence du compte-rendu
     * @param testOrderCode code du bon d'examen associé
     * @param labName       nom du laboratoire (en-tête / signature)
     */
    void sendAssignedReview(String to, String reviewerName, String reportTitle,
                            String testOrderCode, String labName);

    /**
     * Notifie le support qu'un nouveau ticket vient d'être créé.
     * (Réplique Laravel : {@code NotificationCreateNewTicket}.)
     *
     * @param to            adresse email du support
     * @param ticketCode    code du ticket créé
     * @param createdByName nom complet de l'auteur du ticket
     * @param labName       nom du laboratoire (en-tête / signature)
     */
    void sendNewTicketAlert(String to, String ticketCode, String createdByName, String labName);

    /**
     * Envoie l'alerte "compte-rendu non fait" pour une demande d'examen en retard.
     * (Réplique Laravel : {@code MailReportNonFait}, seuil 18 jours.)
     *
     * @param to            adresse email de l'utilisateur assigné
     * @param doctorName    nom complet du destinataire
     * @param testOrderCode code de la demande d'examen concernée
     * @param days          ancienneté (jours) affichée dans le message
     * @param labName       nom du laboratoire (en-tête / signature)
     */
    void sendReportNonFaitAlert(String to, String doctorName, String testOrderCode,
                                int days, String labName);

    /**
     * Notifie un utilisateur qu'un document lui a été partagé.
     * (Réplique Laravel : {@code ShareDocMail}.)
     *
     * @param to            adresse email du destinataire
     * @param recipientName nom complet du destinataire
     * @param sharerName    nom complet de la personne qui partage (propriétaire du document)
     * @param docTitle      titre du document partagé
     * @param labName       nom du laboratoire (en-tête / signature)
     */
    void sendShareDoc(String to, String recipientName, String sharerName,
                      String docTitle, String labName);
}
