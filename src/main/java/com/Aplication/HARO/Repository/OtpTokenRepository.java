package com.Aplication.HARO.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Aplication.HARO.Model.OtpToken;
import java.time.Instant;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
  Optional<OtpToken> findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(String email, String purpose);
  Optional<OtpToken> findTopByEmailAndPurposeAndOtpHashAndConsumedAtIsNullOrderByIdDesc(String email, String purpose, String otpHash);
  long deleteByEmailAndPurposeAndConsumedAtIsNull(String email, String purpose);
  long deleteByEmailAndPurposeAndExpiresAtBefore(String email, String purpose, Instant now);
}
