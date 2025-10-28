package com.Aplication.HARO.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Repository.EstudianteRepository;

@Service
public class EstudianteService {

	@Autowired
	private EstudianteRepository repository;

	public List<Estudiante> getAllEstudiantes() {
		return repository.findAll();
	}

	public Optional<Estudiante> getEstudianteById(long id) {
		return repository.findById(id);
	}

	public Estudiante createEstudiante(Estudiante e) {
		// por si llega con id desde el front
		e.setId(null);
		return repository.save(e);
	}

	public Estudiante updateEstudiante(long id, Estudiante incoming) {
		Estudiante db = repository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));

		// Copiar campos editables (incluye categoria y tipoEstudiante)
		db.setNombre(incoming.getNombre());
		db.setApellido(incoming.getApellido());
		db.setTipoDocumento(incoming.getTipoDocumento());
		db.setNumeroDocumento(incoming.getNumeroDocumento());
		db.setTelefono(incoming.getTelefono());
		db.setEmail(incoming.getEmail());
		db.setDireccion(incoming.getDireccion());
		db.setEstado(incoming.getEstado());
		db.setUsuario(incoming.getUsuario());
		db.setContrasena(incoming.getContrasena());
		db.setCategoria(incoming.getCategoria());
		db.setTipoEstudiante(incoming.getTipoEstudiante());

		return repository.save(db);
	}

	public Optional<Estudiante> buscarPorNumeroDocumento(String numeroDocumento) {
		return repository.findByNumeroDocumento(numeroDocumento);
	}

	public boolean existePorNumeroDocumento(String numeroDocumento) {
		return repository.existsByNumeroDocumento(numeroDocumento);
	}

	public void deleteEstudiante(long id) {
		repository.deleteById(id);
	}
}
