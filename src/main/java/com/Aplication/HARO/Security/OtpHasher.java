package com.Aplication.HARO.Security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Utilidad de seguridad para generar hashes de OTP.
 *
 * Evita almacenar codigos OTP en texto plano al producir un SHA-256 en formato
 * hexadecimal.
 */
public class OtpHasher {
  /**
   * Calcula SHA-256 hexadecimal para el valor recibido.
   */
  public static String sha256(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot hash OTP", e);
    }
  }
}
