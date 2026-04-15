package com.Aplication.HARO.Security;

/**
 * Constantes de rol usadas por autenticacion y autorizacion.
 *
 * No es enum porque Spring Security consume estas cadenas para construir
 * autoridades con prefijo {@code ROLE_}.
 */
public final class Roles {
  private Roles() {}

  public static final String ADMIN = "ADMIN";
  public static final String PROFESOR = "TEACHER";
  public static final String ESTUDIANTE = "STUDENT";
}
