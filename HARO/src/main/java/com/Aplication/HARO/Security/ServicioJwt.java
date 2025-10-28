// src/main/java/com/Aplication/HARO/Seguridad/ServicioJwt.java
package com.Aplication.HARO.Security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class ServicioJwt {

  private final SecretKey llave;
  private final long milisAcceso;
  private final long milisRefresh;
  private final String emisor;

  public ServicioJwt(
      @Value("${app.jwt.secret}") String secreto,
      @Value("${app.jwt.issuer:HARO}") String emisor,
      @Value("${app.jwt.access.minutes:60}") long minutosAcceso,
      @Value("${app.jwt.refresh.days:7}") long diasRefresh
  ) {
    byte[] raw;
    if (secreto.startsWith("base64:")) {
      raw = io.jsonwebtoken.io.Decoders.BASE64.decode(secreto.substring("base64:".length()));
    } else {
      raw = secreto.getBytes(StandardCharsets.UTF_8);
    }
    this.llave = Keys.hmacShaKeyFor(raw); // ≥ 256 bits
    this.milisAcceso = minutosAcceso * 60_000L;
    this.milisRefresh = diasRefresh * 24L * 60L * 60L * 1000L;
    this.emisor = emisor;
  }

  public String emitirTokenAcceso(String login, String rol, Long uid, Map<String, Object> extraClaims) {
    long ahora = System.currentTimeMillis();
    JwtBuilder builder = Jwts.builder()
        .setSubject(login)
        .setIssuer(emisor)
        .setIssuedAt(new Date(ahora))
        .setExpiration(new Date(ahora + milisAcceso))
        .signWith(llave, SignatureAlgorithm.HS256);

    if (rol != null) builder.claim("rol", rol);
    if (uid != null) builder.claim("uid", uid);
    if (extraClaims != null) builder.addClaims(extraClaims);
    return builder.compact();
  }

  public String emitirTokenRefresco(String login) {
    long ahora = System.currentTimeMillis();
    return Jwts.builder()
        .setSubject(login)
        .setIssuer(emisor)
        .setIssuedAt(new Date(ahora))
        .setExpiration(new Date(ahora + milisRefresh))
        .claim("typ", "refresh")
        .signWith(llave, SignatureAlgorithm.HS256)
        .compact();
  }

  public String extraerLogin(String token) {
    try { return parse(token).getBody().getSubject(); }
    catch (JwtException | IllegalArgumentException e) { return null; }
  }

  public boolean tokenValido(String token, UserDetails user) {
    try {
      Claims claims = parse(token).getBody();
      String subject = claims.getSubject();
      Date exp = claims.getExpiration();
      return subject != null
          && subject.equals(user.getUsername())
          && exp != null
          && exp.after(new Date());
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public Claims claims(String token) {
    return parse(token).getBody();
  }

  private Jws<Claims> parse(String token) {
    return Jwts.parserBuilder().setSigningKey(llave).build().parseClaimsJws(token);
  }
}
