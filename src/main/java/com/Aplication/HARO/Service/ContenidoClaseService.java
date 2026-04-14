package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import com.Aplication.HARO.Model.ContenidoClase;
import com.Aplication.HARO.Repository.ContenidoClaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de contenidos del modulo de aprendizaje.
 *
 * Gestiona recursos asociados a clases, archivos subidos, previews, orden,
 * visibilidad y limpieza de almacenamiento.
 */
@Service
@Transactional
public class ContenidoClaseService {

    private static final Logger log = LoggerFactory.getLogger(ContenidoClaseService.class);

    /**
     * Metadatos básicos del archivo almacenado.
     */
    private record StoredObject(String url, String ext) {}

    /**
     * DTO de entrada para crear o actualizar contenido de clase.
     */
    public record ContentInput(
            String titulo,
            String tipo,
            String descripcion,
            String url,
            Integer orden,
            Boolean visible
    ) {}

    private static final Set<String> TIPOS_VALIDOS = Set.of("documento", "video", "imagen", "enlace");
    private static final Set<String> EXT_DIAPOSITIVA = Set.of("ppt", "pptx", "pps", "ppsm");
    private static final Set<String> EXT_PERMITIDAS = Set.of(
            "pdf", "mp4", "jpg", "jpeg", "png", "docx",
            "ppt", "pptx", "pps", "ppsm"
    );
    private static final long MAX_BYTES = 50L * 1024L * 1024L;

    private final ContenidoClaseRepository repository;
    private final ClaseAprendizajeService claseService;
    private final Path uploadDir;
    private final String storageProvider;
    private final String awsRegion;
    private final String s3Bucket;
    private final String s3BasePath;
    private final String s3PublicBaseUrl;
    private final String slideConverterCommand;
    private final S3Client s3Client;

    public ContenidoClaseService(ContenidoClaseRepository repository,
                                 ClaseAprendizajeService claseService,
                                 @Value("${app.upload.dir:uploads}") String uploadDir,
                                 @Value("${storage.provider:local}") String storageProvider,
                                 @Value("${aws.region:us-east-2}") String awsRegion,
                                 @Value("${aws.access-key-id:}") String awsAccessKeyId,
                                 @Value("${aws.secret-access-key:}") String awsSecretAccessKey,
                                 @Value("${s3.bucket:}") String s3Bucket,
                                 @Value("${s3.base-path:clases/}") String s3BasePath,
                                 @Value("${s3.public-base-url:}") String s3PublicBaseUrl,
                                 @Value("${app.slides.converter.command:soffice}") String slideConverterCommand) {
        this.repository = repository;
        this.claseService = claseService;
        this.uploadDir = Path.of(uploadDir, "clases");
        this.storageProvider = (storageProvider == null ? "local" : storageProvider.trim().toLowerCase());
        this.awsRegion = awsRegion == null || awsRegion.isBlank() ? "us-east-2" : awsRegion.trim();
        this.s3Bucket = s3Bucket == null ? "" : s3Bucket.trim();
        this.s3BasePath = normalizeBasePath(s3BasePath);
        this.s3PublicBaseUrl = s3PublicBaseUrl == null ? "" : s3PublicBaseUrl.trim().replaceAll("/+$", "");
        this.slideConverterCommand = (slideConverterCommand == null || slideConverterCommand.isBlank())
                ? "soffice" : slideConverterCommand.trim();

        if ("s3".equals(this.storageProvider) && !isS3Enabled(this.storageProvider, this.s3Bucket, awsAccessKeyId, awsSecretAccessKey)) {
            throw new IllegalStateException("Falta configuracion S3: verifica AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY y S3_BUCKET");
        }

        if ("s3".equals(this.storageProvider)) {
            this.s3Client = S3Client.builder()
                    .region(Region.of(this.awsRegion))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(awsAccessKeyId.trim(), awsSecretAccessKey.trim())
                            )
                    )
                    .build();
        } else {
            this.s3Client = null;
        }
    }

    /**
     * Obtiene el listado usado por las vistas administrativas.
     */
    @Transactional(readOnly = true)
    public List<ContenidoClase> getByClaseForAdmin(Long claseId) {
        return repository.findByClase_IdOrderByOrdenAscIdAsc(claseId);
    }

    /**
     * Obtiene el registro solicitado por identificador o criterio de busqueda.
     */
    @Transactional(readOnly = true)
    public List<ContenidoClase> getByClasePublicada(Long claseId) {
        ClaseAprendizaje clase = claseService.requireById(claseId);
        if (!Boolean.TRUE.equals(clase.getVisible()) || !Boolean.TRUE.equals(clase.getPublicada())) {
            return List.of();
        }
        return repository.findByClase_IdAndVisibleTrueOrderByOrdenAscIdAsc(claseId);
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public ContenidoClase create(Long claseId, ContentInput in, MultipartFile archivo) {
        if (in == null) throw new IllegalArgumentException("Datos de contenido requeridos");
        ClaseAprendizaje clase = claseService.requireById(claseId);

        ContenidoClase row = new ContenidoClase();
        row.setClase(clase);
        merge(row, in, archivo, true);
        return repository.save(row);
    }

    /**
     * Actualiza el registro existente con los datos permitidos.
     */
    public ContenidoClase update(Long id, ContentInput in, MultipartFile archivo) {
        if (in == null) throw new IllegalArgumentException("Datos de contenido requeridos");
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        merge(row, in, archivo, false);
        return repository.save(row);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteLogico(Long id) {
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        row.setVisible(false);
        repository.save(row);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteFisico(Long id) {
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        deleteStoredFileByUrl(row.getUrl());
        deleteStoredFileByUrl(row.getPreviewUrl());
        repository.delete(row);
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteByClaseFisico(Long claseId) {
        List<ContenidoClase> rows = repository.findByClase_IdOrderByOrdenAscIdAsc(claseId);
        for (ContenidoClase row : rows) {
            deleteStoredFileByUrl(row.getUrl());
            deleteStoredFileByUrl(row.getPreviewUrl());
        }
        repository.deleteByClase_Id(claseId);
        repository.flush();
    }

    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteStoredFileByUrl(String rawUrl) {
        String url = trim(rawUrl);
        if (url.isBlank()) return;
        if (url.startsWith("/uploads/") || url.contains("/uploads/")) {
            deleteLocalByUrl(url);
            return;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            deleteS3ByUrl(url);
        }
    }

    /**
     * Aplica el estado o valor interno correspondiente.
     */
    private void applyPreviewIfNeeded(ContenidoClase row, MultipartFile archivo, String ext) {
        if (!EXT_DIAPOSITIVA.contains(ext)) {
            clearPreview(row);
            return;
        }
        try {
            String preview = convertSlideToPdfAndStore(archivo, row.getTipo(), row.getClase() != null ? row.getClase().getId() : null);
            if (preview == null || preview.isBlank()) {
                clearPreview(row);
                return;
            }
            row.setPreviewTipo("pdf");
            row.setPreviewUrl(preview);
        } catch (Exception ex) {
            // En produccion (Cloud Run) esto puede fallar si el contenedor no trae el conversor (libreoffice).
            log.warn("No se pudo generar preview PDF para contenido id={} tipo={} ext={}: {}",
                    row.getId(), row.getTipo(), ext, ex.getMessage());
            clearPreview(row);
        }
    }

    /**
     * Limpia informacion auxiliar segun las reglas del servicio.
     */
    private void clearPreview(ContenidoClase row) {
        row.setPreviewTipo(null);
        row.setPreviewUrl(null);
    }

    /**
     * Convierte el valor recibido al formato requerido por el servicio.
     */
    private String convertSlideToPdfAndStore(MultipartFile archivo, String tipo, Long claseId) throws IOException, InterruptedException {
        String original = archivo.getOriginalFilename() == null ? "archivo" : archivo.getOriginalFilename().trim();
        String ext = extension(original);
        if (!EXT_DIAPOSITIVA.contains(ext)) return null;

        Path workDir = Files.createTempDirectory("haro-slide-convert-");
        try {
            Path input = workDir.resolve("input." + ext);
            Files.copy(archivo.getInputStream(), input, StandardCopyOption.REPLACE_EXISTING);

            List<String> cmd = new ArrayList<>();
            cmd.add(slideConverterCommand);
            cmd.add("--headless");
            cmd.add("--convert-to");
            cmd.add("pdf");
            cmd.add("--outdir");
            cmd.add(workDir.toAbsolutePath().toString());
            cmd.add(input.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) return null;

            Path output = workDir.resolve("input.pdf");
            if (!Files.exists(output) || Files.size(output) == 0) return null;
            return storePreviewPdf(output, tipo, claseId);
        } finally {
            tryDeleteTempDirectory(workDir);
        }
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private String storePreviewPdf(Path pdfFile, String tipo, Long claseId) throws IOException {
        if ("s3".equals(storageProvider)) {
            return storePreviewPdfInS3(pdfFile, tipo, claseId);
        }
        return storePreviewPdfLocal(pdfFile, tipo, claseId);
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private String storePreviewPdfInS3(Path pdfFile, String tipo, Long claseId) throws IOException {
        String objectKey = buildStorageSubPath(tipo, claseId) + "preview-" + UUID.randomUUID() + ".pdf";
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(objectKey)
                .contentType("application/pdf")
                .contentDisposition("inline")
                .build();
        s3Client.putObject(put, RequestBody.fromBytes(Files.readAllBytes(pdfFile)));
        if (!s3PublicBaseUrl.isBlank()) {
            return s3PublicBaseUrl + "/" + objectKey;
        }
        return "https://" + s3Bucket + ".s3." + awsRegion + ".amazonaws.com/" + objectKey;
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private String storePreviewPdfLocal(Path pdfFile, String tipo, Long claseId) throws IOException {
        String relativeDir = buildStorageSubPath(tipo, claseId);
        Path targetDir = uploadDir.resolve(relativeDir);
        Files.createDirectories(targetDir);
        String safeName = "preview-" + UUID.randomUUID() + ".pdf";
        Path target = targetDir.resolve(safeName);
        Files.copy(Files.newInputStream(pdfFile), target, StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/clases/" + relativeDir + safeName;
    }

    /**
     * Aplica la operacion solo cuando el contexto lo permite.
     */
    private void tryDeleteTempDirectory(Path dir) {
        try {
            if (dir == null || !Files.exists(dir)) return;
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {
        }
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private void merge(ContenidoClase row, ContentInput in, MultipartFile archivo, boolean creating) {
        if (in.titulo() != null) row.setTitulo(trim(in.titulo()));
        if (in.tipo() != null) row.setTipo(trim(in.tipo()).toLowerCase());
        if (in.descripcion() != null) row.setDescripcion(trimToNull(in.descripcion()));
        if (in.orden() != null) row.setOrden(in.orden());
        if (in.visible() != null) row.setVisible(in.visible());

        boolean hasFile = archivo != null && !archivo.isEmpty();
        if (hasFile) {
            StoredObject stored = storeFile(archivo, row.getTipo(), row.getClase() != null ? row.getClase().getId() : null);
            row.setUrl(stored.url());
            applyPreviewIfNeeded(row, archivo, stored.ext());
        } else if (in.url() != null) {
            row.setUrl(trim(in.url()));
            clearPreview(row);
        }

        if (creating && row.getVisible() == null) row.setVisible(true);
        if (creating && row.getOrden() == null) row.setOrden(1);

        validate(row);
    }

    /**
     * Valida la informacion recibida antes de continuar el proceso.
     */
    private void validate(ContenidoClase row) {
        String titulo = trim(row.getTitulo());
        String tipo = trim(row.getTipo()).toLowerCase();
        String url = normalizeUrl(trim(row.getUrl()));

        if (titulo.isBlank()) throw new IllegalArgumentException("titulo es obligatorio");
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new IllegalArgumentException("tipo invalido. Usa: documento, video, imagen, enlace");
        }
        if (url.isBlank()) throw new IllegalArgumentException("url es obligatoria");
        if (row.getOrden() == null || row.getOrden() <= 0) {
            throw new IllegalArgumentException("orden debe ser mayor a 0");
        }

        row.setTitulo(titulo);
        row.setTipo(tipo);
        row.setUrl(url);
        if (row.getVisible() == null) row.setVisible(true);
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeUrl(String url) {
        if (url.isBlank()) return url;
        String lower = url.toLowerCase();
        if (lower.startsWith("blob:") || lower.startsWith("data:")) {
            throw new IllegalArgumentException("url invalida: no se permiten blob/data URLs; sube archivo o usa URL http(s).");
        }
        if (lower.startsWith("http://") || lower.startsWith("https://") || url.startsWith("/uploads/")) {
            return url;
        }
        throw new IllegalArgumentException("url invalida: usa http(s) o ruta /uploads/...");
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private StoredObject storeFile(MultipartFile file, String tipo, Long claseId) {
        if ("s3".equals(storageProvider)) {
            return storeFileInS3(file, tipo, claseId);
        }
        return storeFileLocal(file, tipo, claseId);
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private StoredObject storeFileInS3(MultipartFile file, String tipo, Long claseId) {
        try {
            if (file.getSize() <= 0) {
                throw new IllegalArgumentException("archivo vacio");
            }
            if (file.getSize() > MAX_BYTES) {
                throw new IllegalArgumentException("archivo excede 50MB");
            }

            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
            String ext = extension(original);
            if (!EXT_PERMITIDAS.contains(ext)) {
                throw new IllegalArgumentException("extension no permitida. Usa: pdf, mp4, jpg, jpeg, png, docx, ppt, pptx, pps, ppsm");
            }

            String objectKey = buildStorageSubPath(tipo, claseId) + UUID.randomUUID() + "." + ext;
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = inferContentType(ext);
            }

            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentDisposition("inline")
                    .build();

            s3Client.putObject(put, RequestBody.fromBytes(file.getBytes()));

            String publicUrl;
            if (!s3PublicBaseUrl.isBlank()) {
                publicUrl = s3PublicBaseUrl + "/" + objectKey;
            } else {
                publicUrl = "https://" + s3Bucket + ".s3." + awsRegion + ".amazonaws.com/" + objectKey;
            }
            return new StoredObject(publicUrl, ext);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo en S3", e);
        }
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private StoredObject storeFileLocal(MultipartFile file, String tipo, Long claseId) {
        try {
            if (file.getSize() <= 0) {
                throw new IllegalArgumentException("archivo vacio");
            }
            if (file.getSize() > MAX_BYTES) {
                throw new IllegalArgumentException("archivo excede 50MB");
            }

            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
            String ext = extension(original);
            if (!EXT_PERMITIDAS.contains(ext)) {
                throw new IllegalArgumentException("extension no permitida. Usa: pdf, mp4, jpg, jpeg, png, docx, ppt, pptx, pps, ppsm");
            }

            String relativeDir = buildStorageSubPath(tipo, claseId);
            Path targetDir = uploadDir.resolve(relativeDir);
            Files.createDirectories(targetDir);
            String safeName = UUID.randomUUID() + "." + ext;
            Path target = targetDir.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredObject("/uploads/clases/" + relativeDir + safeName, ext);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo", e);
        }
    }

    /**
     * Limpia informacion auxiliar segun las reglas del servicio.
     */
    private void deleteLocalByUrl(String url) {
        try {
            String marker = "/uploads/";
            int idx = url.indexOf(marker);
            String rel = idx >= 0 ? url.substring(idx + marker.length()) : url;
            Path root = Path.of(uploadDir.getParent() == null ? "uploads" : uploadDir.getParent().toString())
                    .toAbsolutePath().normalize();
            Path target = root.resolve(rel).normalize();
            if (target.startsWith(root) && Files.exists(target)) {
                Files.delete(target);
            }
        } catch (Exception ignored) {
            // No bloquea la eliminacion fisica de DB si el archivo ya no existe.
        }
    }

    /**
     * Limpia informacion auxiliar segun las reglas del servicio.
     */
    private void deleteS3ByUrl(String url) {
        if (s3Client == null || s3Bucket.isBlank()) return;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();

            String key = extractKeyFromS3Url(host, path);
            if (key.isBlank()) return;

            DeleteObjectRequest req = DeleteObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(req);
        } catch (Exception ignored) {
            // No bloquea la eliminacion fisica de DB si el objeto ya no existe.
        }
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String extractKeyFromS3Url(String host, String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.isBlank()) return "";

        // virtual-hosted-style: <bucket>.s3.<region>.amazonaws.com/<key>
        if (host.startsWith(s3Bucket + ".s3")) {
            return p;
        }

        // path-style: s3.<region>.amazonaws.com/<bucket>/<key>
        if (host.startsWith("s3.") || host.equals("s3.amazonaws.com")) {
            String prefix = s3Bucket + "/";
            if (p.startsWith(prefix)) {
                return p.substring(prefix.length());
            }
        }
        return "";
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trimToNull(String s) {
        String out = trim(s);
        return out.isBlank() ? null : out;
    }

    /**
     * Evalua una condicion del flujo y devuelve el resultado.
     */
    private boolean isS3Enabled(String provider, String bucket, String access, String secret) {
        if (!"s3".equals(provider)) return false;
        return bucket != null && !bucket.isBlank()
                && access != null && !access.isBlank()
                && secret != null && !secret.isBlank();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeBasePath(String raw) {
        String p = raw == null ? "clases/" : raw.trim();
        if (p.isBlank()) p = "clases/";
        if (p.startsWith("/")) p = p.substring(1);
        if (!p.endsWith("/")) p = p + "/";
        return p;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String inferContentType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "mp4" -> "video/mp4";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "pps" -> "application/vnd.ms-powerpoint";
            case "ppsm" -> "application/vnd.ms-powerpoint.slideshow.macroEnabled.12";
            default -> "application/octet-stream";
        };
    }

    /**
     * Construye el valor auxiliar necesario para el flujo del servicio.
     */
    private String buildStorageSubPath(String tipo, Long claseId) {
        String tipoSafe = sanitizeSegment(tipo);
        String claseSafe = claseId == null ? "sin-clase" : "clase-" + claseId;
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        return s3BasePath + claseSafe + "/" + tipoSafe + "/" + year + "/" + month + "/";
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String sanitizeSegment(String raw) {
        String out = raw == null ? "otros" : raw.trim().toLowerCase();
        if (out.isBlank()) out = "otros";
        out = out.replaceAll("[^a-z0-9_-]", "-");
        out = out.replaceAll("-{2,}", "-");
        if (out.startsWith("-")) out = out.substring(1);
        if (out.endsWith("-")) out = out.substring(0, out.length() - 1);
        return out.isBlank() ? "otros" : out;
    }
}
