package com.labo.anapath.common.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Codes à usage unique fondés sur l'heure (RFC 6238), ceux des applications
 * d'authentification : SHA-1, pas de 30 s, six chiffres.
 *
 * <p>Remplace la bibliothèque {@code googleauth} (sans version depuis 2021,
 * lot 4). Une quarantaine de lignes de bibliothèque standard, et plus rien à
 * surveiller.</p>
 */
@Component
public class Totp {

    static final long PAS_SECONDES = 30;
    private static final int CHIFFRES = 6;
    private static final String ALPHABET_BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom alea = new SecureRandom();

    /** Un secret neuf : 20 octets aléatoires, en base32 comme l'attendent les applications. */
    public String nouveauSecret() {
        byte[] octets = new byte[20];
        alea.nextBytes(octets);
        return base32(octets);
    }

    /** Le code attendu à cet instant. */
    public int code(String secretBase32, long instantMs) {
        return codeDuPas(secretBase32, pas(instantMs));
    }

    /** Le code est-il celui de l'instant présent, à un pas près (dérive d'horloge du téléphone) ? */
    public boolean verifier(String secretBase32, int code) {
        long courant = pas(System.currentTimeMillis());
        for (long pas = courant - 1; pas <= courant + 1; pas++) {
            if (codeDuPas(secretBase32, pas) == code) return true;
        }
        return false;
    }

    /** Le code est-il exactement celui du pas qui contient cet instant ? */
    public boolean correspondAuPas(String secretBase32, int code, long instantMs) {
        return codeDuPas(secretBase32, pas(instantMs)) == code;
    }

    private static long pas(long instantMs) {
        return instantMs / 1000 / PAS_SECONDES;
    }

    private static int codeDuPas(String secretBase32, long pas) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decoderBase32(secretBase32), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(pas).array());
            int offset = h[h.length - 1] & 0x0F;
            int tronque = ((h[offset] & 0x7F) << 24) | ((h[offset + 1] & 0xFF) << 16)
                    | ((h[offset + 2] & 0xFF) << 8) | (h[offset + 3] & 0xFF);
            return tronque % 1_000_000;
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA1 indisponible", e);
        }
    }

    static String base32(byte[] octets) {
        StringBuilder sb = new StringBuilder();
        int tampon = 0, bits = 0;
        for (byte b : octets) {
            tampon = (tampon << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET_BASE32.charAt((tampon >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(ALPHABET_BASE32.charAt((tampon << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] decoderBase32(String texte) {
        String s = texte.trim().toUpperCase().replace("=", "").replace(" ", "");
        byte[] sortie = new byte[s.length() * 5 / 8];
        int tampon = 0, bits = 0, i = 0;
        for (char c : s.toCharArray()) {
            int v = ALPHABET_BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Secret base32 invalide");
            tampon = (tampon << 5) | v;
            bits += 5;
            if (bits >= 8) {
                sortie[i++] = (byte) ((tampon >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return sortie;
    }
}
