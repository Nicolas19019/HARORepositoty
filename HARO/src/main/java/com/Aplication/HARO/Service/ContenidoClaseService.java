package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ClaseAprendizaje;
import com.Aplication.HARO.Model.ContenidoClase;
import com.Aplication.HARO.Repository.ContenidoClaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ContenidoClaseService {

    public record ContentInput(
            String titulo,
            String tipo,
            String descripcion,
            String url,
            Integer orden,
            Boolean visible
    ) {}

    private static final Set<String> TIPOS_VALIDOS = Set.of("documento", "video", "imagen", "enlace");
    private static final Set<String> EXT_PERMITIDAS = Set.of("pdf", "mp4", "jpg", "jpeg", "png", "docx");
    private static final long MAX_BYTES = 50L * 1024L * 1024L;

    private final ContenidoClaseRepository repository;
    private final ClaseAprendizajeService claseService;
    private final Path uploadDir;
    private final String storageProvider;
    private final String awsRegion;
    private final String s3Bucket;
    private final String s3BasePath;
    private final String s3PublicBaseUrl;
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
                                 @Value("${s3.public-base-url:}") String s3PublicBaseUrl) {
        this.repository = repository;
        this.claseService = claseService;
        this.uploadDir = Path.of(uploadDir, "clases");
        this.storageProvider = (storageProvider == null ? "local" : storageProvider.trim().toLowerCase());
        this.awsRegion = awsRegion == null || awsRegion.isBlank() ? "us-east-2" : awsRegion.trim();
        this.s3Bucket = s3Bucket == null ? "" : s3Bucket.trim();
        this.s3BasePath = normalizeBasePath(s3BasePath);
        this.s3PublicBaseUrl = s3PublicBaseUrl == null ? "" : s3PublicBaseUrl.trim().replaceAll("/+$", "");

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

    @Transactional(readOnly = true)
    public List<ContenidoClase> getByClaseForAdmin(Long claseId) {
        return repository.findByClase_IdOrderByOrdenAscIdAsc(claseId);
    }

    @Transactional(readOnly = true)
    public List<ContenidoClase> getByClasePublicada(Long claseId) {
        ClaseAprendizaje clase = claseService.requireById(claseId);
        if (!Boolean.TRUE.equals(clase.getVisible()) || !Boolean.TRUE.equals(clase.getPublicada())) {
            return List.of();
        }
        return repository.findByClase_IdAndVisibleTrueOrderByOrdenAscIdAsc(claseId);
    }

    public ContenidoClase create(Long claseId, ContentInput in, MultipartFile archivo) {
        if (in == null) throw new IllegalArgumentException("Datos de contenido requeridos");
        ClaseAprendizaje clase = claseService.requireById(claseId);

        ContenidoClase row = new ContenidoClase();
        row.setClase(clase);
        merge(row, in, archivo, true);
        return repository.save(row);
    }

    public ContenidoClase update(Long id, ContentInput in, MultipartFile archivo) {
        if (in == null) throw new IllegalArgumentException("Datos de contenido requeridos");
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        merge(row, in, archivo, false);
        return repository.save(row);
    }

    public void deleteLogico(Long id) {
        ContenidoClase row = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Contenido no encontrado: " + id));
        row.setVisible(false);
        repository.save(row);
    }

    private void merge(ContenidoClase row, ContentInput in, MultipartFile archivo, boolean creating) {
        if (in.titulo() != null) row.setTitulo(trim(in.titulo()));
        if (in.tipo() != null) row.setTipo(trim(in.tipo()).toLowerCase());
        if (in.descripcion() != null) row.setDescripcion(trimToNull(in.descripcion()));
        if (in.orden() != null) row.setOrden(in.orden());
        if (in.visible() != null) row.setVisible(in.visible());

        boolean hasFile = archivo != null && !archivo.isEmpty();
        if (hasFile) {
            row.setUrl(storeFile(archivo, row.getTipo(), row.getClase() != null ? row.getClase().getId() : null));
        } else if (in.url() != null) {
            row.setUrl(trim(in.url()));
        }

        if (creating && row.getVisible() == null) row.setVisible(true);
        if (creating && row.getOrden() == null) row.setOrden(1);

        validate(row);
    }

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

    private String storeFile(MultipartFile file, String tipo, Long claseId) {
        if ("s3".equals(storageProvider)) {
            return storeFileInS3(file, tipo, claseId);
        }
        return storeFileLocal(file, tipo, claseId);
    }

    private String storeFileInS3(MultipartFile file, String tipo, Long claseId) {
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
                throw new IllegalArgumentException("extension no permitida. Usa: pdf, mp4, jpg, jpeg, png, docx");
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
                    .build();

            s3Client.putObject(put, RequestBody.fromBytes(file.getBytes()));

            if (!s3PublicBaseUrl.isBlank()) {
                return s3PublicBaseUrl + "/" + objectKey;
            }
            return "https://" + s3Bucket + ".s3." + awsRegion + ".amazonaws.com/" + objectKey;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo en S3", e);
        }
    }

    private String storeFileLocal(MultipartFile file, String tipo, Long claseId) {
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
                throw new IllegalArgumentException("extension no permitida. Usa: pdf, mp4, jpg, jpeg, png, docx");
            }

            String relativeDir = buildStorageSubPath(tipo, claseId);
            Path targetDir = uploadDir.resolve(relativeDir);
            Files.createDirectories(targetDir);
            String safeName = UUID.randomUUID() + "." + ext;
            Path target = targetDir.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/clases/" + relativeDir + safeName;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo", e);
        }
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String trimToNull(String s) {
        String out = trim(s);
        return out.isBlank() ? null : out;
    }

    private boolean isS3Enabled(String provider, String bucket, String access, String secret) {
        if (!"s3".equals(provider)) return false;
        return bucket != null && !bucket.isBlank()
                && access != null && !access.isBlank()
                && secret != null && !secret.isBlank();
    }

    private String normalizeBasePath(String raw) {
        String p = raw == null ? "clases/" : raw.trim();
        if (p.isBlank()) p = "clases/";
        if (p.startsWith("/")) p = p.substring(1);
        if (!p.endsWith("/")) p = p + "/";
        return p;
    }

    private String inferContentType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "mp4" -> "video/mp4";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private String buildStorageSubPath(String tipo, Long claseId) {
        String tipoSafe = sanitizeSegment(tipo);
        String claseSafe = claseId == null ? "sin-clase" : "clase-" + claseId;
        LocalDate now = LocalDate.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        return s3BasePath + claseSafe + "/" + tipoSafe + "/" + year + "/" + month + "/";
    }

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
