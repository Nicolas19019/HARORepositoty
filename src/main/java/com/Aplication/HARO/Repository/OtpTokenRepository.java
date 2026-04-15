package com.Aplication.HARO.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Aplication.HARO.Model.OtpToken;
import java.time.Instant;
import java.util.Optional;

/**
 * Acceso a tokens OTP de verificacion.
 *
 * Ubica el ultimo codigo activo, valida por hash y elimina tokens pendientes o
 * vencidos por correo y proposito.
 */
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
  Optional<OtpToken> findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(String email, String purpose);
  Optional<OtpToken> findTopByEmailAndPurposeAndOtpHashAndConsumedAtIsNullOrderByIdDesc(String email, String purpose, String otpHash);
  long deleteByEmailAndPurposeAndConsumedAtIsNull(String email, String purpose);
  long deleteByEmailAndPurposeAndExpiresAtBefore(String email, String purpose, Instant now);
}
