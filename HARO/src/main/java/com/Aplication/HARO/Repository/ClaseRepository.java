package com.Aplication.HARO.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.Clase;

import java.time.LocalDate;
import java.util.List;


@Repository
public interface ClaseRepository extends JpaRepository<Clase, Long> {

	 // Por fecha exacta
    List<Clase> findByFecha(LocalDate fecha);

    // Por rango (opcional)
    List<Clase> findByFechaBetween(LocalDate desde, LocalDate hasta);

}
