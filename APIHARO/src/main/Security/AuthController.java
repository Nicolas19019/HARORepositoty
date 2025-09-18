@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final EstudianteRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(AuthenticationManager authManager,
                          EstudianteRepository repo,
                          PasswordEncoder encoder,
                          JwtService jwt) {
        this.authManager = authManager;
        this.repo = repo;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    // Registrar estudiante
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Estudiante e) {
        if (repo.findByUsuario(e.getUsuario()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error","Usuario ya existe"));
        }
        e.setContrasena(encoder.encode(e.getContrasena()));
        repo.save(e);
        return ResponseEntity.ok(Map.of("message","Estudiante registrado"));
    }

    // Login y token
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> req) {
        String user = req.get("usuario");
        String pass = req.get("contrasena");
        authManager.authenticate(new UsernamePasswordAuthenticationToken(user, pass));
        String token = jwt.generate(user, List.of("ESTUDIANTE"));
        return ResponseEntity.ok(Map.of("token", token));
    }
}
