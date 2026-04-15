package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Token temporal de verificacion por correo.
 *
 * Guarda el hash del codigo OTP, su proposito, vencimiento, consumo e intentos
 * para controlar verificaciones sin persistir el codigo en texto plano.
 */
@Entity
@Table(name = "otp_token",
       indexes = {
         @Index(name = "idx_otp_email_purpose", columnList = "email,purpose"),
         @Index(name = "idx_otp_expires", columnList = "expires_at")
})
public class OtpToken {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 190)
  private String email;

  @Column(nullable = false, length = 40)
  private String purpose; // EMAIL_VERIFY u otro uso de verificacion.

  @Column(name = "otp_hash", nullable = false, length = 128)
  private String otpHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(nullable = false)
  private int attempts = 0;

  @Column(name = "sent_at")
  private Instant sentAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }
  public String getOtpHash() { return otpHash; }
  public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getConsumedAt() { return consumedAt; }
  public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public Instant getSentAt() { return sentAt; }
  public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
