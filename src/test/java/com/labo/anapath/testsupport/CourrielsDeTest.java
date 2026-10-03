package com.labo.anapath.testsupport;

import com.labo.anapath.common.email.EmailServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

/**
 * Le service de courriel des tests : retient le dernier code de connexion au
 * lieu de l'envoyer.
 *
 * <p>Le code n'existe nulle part ailleurs en clair — la base n'en garde que
 * l'empreinte. C'est donc ici, à la sortie, que le test le lit pour franchir
 * le challenge comme le ferait une personne devant sa boîte aux lettres.</p>
 *
 * <p>Les autres courriels suivent le chemin normal : le serveur SMTP de test
 * n'existe pas, l'envoi échoue et est journalisé, comme avant.</p>
 */
@Component
@Primary
public class CourrielsDeTest extends EmailServiceImpl {

    // Statique : @Async fait du bean un proxy d'interface, qu'on ne peut injecter
    // sous ce type. Le test lit ici, sans injection.
    private static volatile String dernierCode;

    public CourrielsDeTest(JavaMailSender mailSender, TemplateEngine templateEngine, Environment environment) {
        super(mailSender, templateEngine, environment);
    }

    @Override
    public void sendOtp(String to, String firstname, String otp) {
        dernierCode = otp;
    }

    public static String dernierCode() {
        return dernierCode;
    }
}
