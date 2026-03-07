
package com.Aplication.HARO.Security;

/**
 * Constantes de rol. No es enum: solo cadenas.
 * Spring Security espera "ROLE_*" en las autoridades.
 */
public final class Roles {
  private Roles() {}
  public static final String ADMIN     = "ADMIN";
  public static final String PROFESOR  = "TEACHER"; // si prefieres "PROFESOR", úsalo; solo sé consistente
  public static final String ESTUDIANTE= "STUDENT";
}
