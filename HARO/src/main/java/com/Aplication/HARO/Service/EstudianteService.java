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
		// por si acaso llegó id seteado desde otro lado
		e.setId(null);
		return repository.save(e);
	}

	public Estudiante updateEstudiante(long id, Estudiante incoming) {
		Estudiante db = repository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Estudiante no encontrado: " + id));

		// aplica solo los campos editables (no toques id)
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

		return repository.save(db); // ahora sí, update correcto
	}

	public void deleteEstudiante(long id) {
		repository.deleteById(id);
	}
}
