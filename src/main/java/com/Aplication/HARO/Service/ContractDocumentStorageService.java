package com.Aplication.HARO.Service;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio de almacenamiento de contratos firmados.
 *
 * Valida metadatos, construye rutas y guarda archivos PDF firmados en el bucket
 * configurado, con soporte de eliminacion compensatoria.
 */
@Service
public class ContractDocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(ContractDocumentStorageService.class);

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.of("America/Bogota"));

    private final String storageProvider;
    private final Path uploadDir;
    private final String awsRegion;
    private final String s3Bucket;
    private final String s3BasePath;
    private final String s3PublicBaseUrl;
    private final S3Client s3Client;

    /**
     * Registro con metadatos del documento almacenado.
     */
    public record StoredDocument(
            String objectKey,
            String publicUrl,
            String fileName,
            String signerFolder
    ) {}

    public ContractDocumentStorageService(@Value("${storage.provider:local}") String storageProvider,
                                          @Value("${app.upload.dir:uploads}") String uploadDir,
                                          @Value("${aws.region:us-east-2}") String awsRegion,
                                          @Value("${aws.access-key-id:}") String awsAccessKeyId,
                                          @Value("${aws.secret-access-key:}") String awsSecretAccessKey,
                                          @Value("${s3.bucket:}") String s3Bucket,
                                          @Value("${chatbot.contract.storage.base-path:contratos/}") String contractStorageBasePath,
                                          @Value("${s3.public-base-url:}") String s3PublicBaseUrl) {
        this.storageProvider = safe(storageProvider).toLowerCase(Locale.ROOT);
        this.uploadDir = Path.of(safe(uploadDir), "contracts").toAbsolutePath().normalize();
        this.awsRegion = safe(awsRegion).isBlank() ? "us-east-2" : safe(awsRegion);
        this.s3Bucket = safe(s3Bucket);
        this.s3BasePath = normalizeBasePath(contractStorageBasePath);
        this.s3PublicBaseUrl = safe(s3PublicBaseUrl).replaceAll("/+$", "");

        if ("s3".equals(this.storageProvider)) {
            if (this.s3Bucket.isBlank() || safe(awsAccessKeyId).isBlank() || safe(awsSecretAccessKey).isBlank()) {
                throw new IllegalStateException("Falta configuracion S3 para contratos");
            }
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
     * Almacena el archivo recibido y devuelve sus metadatos.
     */
    public StoredDocument storeSignedContract(MultipartFile file,
                                              String signerName,
                                              String documento,
                                              String contractName,
                                              String sede,
                                              String categoryCode) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Archivo PDF requerido");
        }

        String sedeFolder = slugSede(sede);
        String categoryFolder = slugCategory(categoryCode);
        String signerFolder = slug(signerName, "firmante");
        String docFolder = slug(documento, "sin-documento");
        String contractSlug = slug(contractName, "contrato");
        String fileName = contractSlug + "-" + TS_FORMAT.format(Instant.now()) + ".pdf";
        String relativeFolder = sedeFolder + "/" + signerFolder + "/" + docFolder + "/" + categoryFolder + "/";

        if ("s3".equals(storageProvider)) {
            return storeInS3(file, relativeFolder, fileName, signerFolder);
        }
        return storeLocal(file, relativeFolder, fileName, signerFolder);
    }

    /** Best-effort rollback when an upload must be rejected after storage. */
    /**
     * Elimina o desactiva el registro segun la regla del servicio.
     */
    public void deleteStoredContract(String objectKey) {
        String key = safe(objectKey);
        if (key.isBlank()) {
            return;
        }
        if ("s3".equals(storageProvider)) {
            if (s3Client == null || s3Bucket.isBlank()) {
                return;
            }
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(key)
                        .build());
            } catch (Exception ex) {
                log.warn("No se pudo borrar contrato en S3 key={}: {}", key, ex.getMessage());
            }
            return;
        }

        try {
            Path resolved = uploadDir.resolve(key).normalize();
            if (!resolved.startsWith(uploadDir)) {
                log.warn("Se omitio borrado de contrato fuera del uploadDir: {}", resolved);
                return;
            }
            Files.deleteIfExists(resolved);
        } catch (Exception ex) {
            log.warn("No se pudo borrar contrato local key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private StoredDocument storeInS3(MultipartFile file,
                                     String relativeFolder,
                                     String fileName,
                                     String signerFolder) {
        try {
            String objectKey = s3BasePath + relativeFolder + fileName;
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(objectKey)
                    .contentType("application/pdf")
                    .build();
            s3Client.putObject(put, RequestBody.fromBytes(file.getBytes()));

            String publicUrl;
            if (!s3PublicBaseUrl.isBlank()) {
                publicUrl = s3PublicBaseUrl + "/" + objectKey;
            } else {
                publicUrl = "https://" + s3Bucket + ".s3." + awsRegion + ".amazonaws.com/" + objectKey;
            }
            return new StoredDocument(objectKey, publicUrl, fileName, signerFolder);
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo almacenar contrato en S3", ex);
        }
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private StoredDocument storeLocal(MultipartFile file,
                                      String relativeFolder,
                                      String fileName,
                                      String signerFolder) {
        try {
            Path targetDir = uploadDir.resolve(relativeFolder).normalize();
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(fileName).normalize();
            Files.write(target, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String objectKey = relativeFolder + fileName;
            String publicUrl = "/uploads/contracts/" + objectKey.replace("\\", "/");
            return new StoredDocument(objectKey, publicUrl, fileName, signerFolder);
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo almacenar contrato en disco", ex);
        }
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeBasePath(String raw) {
        String p = safe(raw).replace("\\", "/");
        p = p.replaceAll("^/+", "");
        p = p.replaceAll("/+", "/");
        if (!p.endsWith("/")) p += "/";
        return p;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String slug(String raw, String fallback) {
        String v = safe(raw).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (v.isBlank()) return fallback;
        return v.length() > 80 ? v.substring(0, 80) : v;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String slugSede(String rawSede) {
        String normalized = safe(rawSede).toLowerCase(Locale.ROOT);
        if (normalized.contains("1 de mayo") || normalized.contains("primero de mayo")) {
            return "1-de-mayo";
        }
        if (normalized.contains("eden") || normalized.contains("edén")) {
            return "el-eden";
        }
        return slug(rawSede, "sin-sede");
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String slugCategory(String rawCategoryCode) {
        String normalized = safe(rawCategoryCode).toUpperCase(Locale.ROOT);
        if ("A2".equals(normalized) || "B1".equals(normalized) || "C1".equals(normalized)) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return slug(rawCategoryCode, "sin-categoria");
    }
}

