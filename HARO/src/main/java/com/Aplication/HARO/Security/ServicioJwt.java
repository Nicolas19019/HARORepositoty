// src/main/java/com/Aplication/HARO/Security/ServicioJwt.java
package com.Aplication.HARO.Security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class ServicioJwt {

  private final SecretKey llave;
  private final long milisAcceso;
  private final long milisRefresh;
  private final String emisor;

  public ServicioJwt(
      @Value("${app.jwt.secret}") String secreto,
      @Value("${app.jwt.issuer:HARO}") String emisor,
      @Value("${app.jwt.access.minutes:15}") long minutosAcceso,
      @Value("${app.jwt.refresh.days:30}") long diasRefresh
  ) {
    byte[] raw;
    if (secreto.startsWith("base64:")) {
      raw = Decoders.BASE64.decode(secreto.substring("base64:".length()));
    } else {
      raw = secreto.getBytes(StandardCharsets.UTF_8); // recomienda ≥32 bytes reales
    }
    this.llave = Keys.hmacShaKeyFor(raw);
    this.milisAcceso = minutosAcceso * 60_000L;
    this.milisRefresh = diasRefresh * 24L * 60L * 60L * 1000L;
    this.emisor = emisor;
  }

  // ACCESS: typ=access + jti; incluye rol/uid si se pasan
  public String emitirTokenAcceso(String login, String rol, Long uid, String jti) {
    long ahora = System.currentTimeMillis();
    JwtBuilder b = Jwts.builder()
        .setSubject(login)
        .setIssuer(emisor)
        .setIssuedAt(new Date(ahora))
        .setExpiration(new Date(ahora + milisAcceso))
        .setId(jti != null ? jti : UUID.randomUUID().toString())
        .claim("typ", "access")
        .signWith(llave, SignatureAlgorithm.HS256);

    if (rol != null) b.claim("rol", rol);
    if (uid != null) b.claim("uid", uid);
    return b.compact();
  }

  // REFRESH: typ=refresh + jti
  public String emitirTokenRefresco(String login) {
    long ahora = System.currentTimeMillis();
    return Jwts.builder()
        .setSubject(login)
        .setIssuer(emisor)
        .setIssuedAt(new Date(ahora))
        .setExpiration(new Date(ahora + milisRefresh))
        .setId(UUID.randomUUID().toString())
        .claim("typ", "refresh")
        .signWith(llave, SignatureAlgorithm.HS256)
        .compact();
  }

  public Claims claims(String token) {
    return Jwts.parserBuilder().setSigningKey(llave).build().parseClaimsJws(token).getBody();
  }

  public String extraerLogin(String token) {
    try { return claims(token).getSubject(); }
    catch (JwtException | IllegalArgumentException e) { return null; }
  }

  public boolean tokenValido(String token, org.springframework.security.core.userdetails.UserDetails user) {
    try {
      Claims c = claims(token);
      String subject = c.getSubject();
      Date exp = c.getExpiration();
      return subject != null
          && subject.equals(user.getUsername())
          && exp != null
          && exp.after(new Date());
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public long getAccessTtlSeconds()  { return milisAcceso  / 1000L; }
  public long getRefreshTtlSeconds() { return milisRefresh / 1000L; }
}
