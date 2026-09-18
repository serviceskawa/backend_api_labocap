package com.labo.anapath.common.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corps de {@code POST /api/v1/sms} : la charge utile chiffrée, et rien d'autre.
 *
 * <p>Les messages ne circulent pas en clair. Ils sont enveloppés avec un
 * {@code nonce} et un {@code timestamp}, puis chiffrés par
 * {@link FluidPayPayloadCipher} — un {@code messages} placé à côté du champ
 * chiffré est ignoré par le serveur, qui répond que le champ est manquant.</p>
 *
 * @param encryptedData charge utile en {@code base64(IV ‖ chiffré)}
 */
public record FluidPaySmsRequest(@JsonProperty("encrypted_data") String encryptedData) {
}
