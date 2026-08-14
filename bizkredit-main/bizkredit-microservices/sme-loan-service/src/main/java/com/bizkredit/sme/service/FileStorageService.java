package com.bizkredit.sme.service;

import com.bizkredit.sme.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

// Saves uploaded document files to a folder on the local disk
// (application.properties: app.upload-dir, defaults to ./uploads/documents)
// and stores just the resulting path in the ApplicationDocument row -
// this is the standard "files on disk, path in the database" pattern,
// deliberately not storing raw file bytes as a BLOB in MySQL (bloats
// the database, slows backups, doesn't scale) and not using S3 (out of
// scope per the original requirements doc's Phase 1 assumptions).
@Slf4j
@Service
public class FileStorageService {

    @Value("${app.upload-dir:./uploads/documents}")
    private String uploadDir;

    // Extensions a real credit document upload would plausibly use.
    // Rejecting anything else blocks accidental (or deliberate) upload
    // of executables/scripts through this endpoint.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx"
    );

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was provided");
        }

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document"
        );
        String extension = getExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BadRequestException(
                    "Unsupported file type: ." + extension
                            + ". Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // Generated filename avoids collisions between two applicants
            // uploading files with the same original name, and avoids any
            // path-traversal risk from a crafted original filename.
            String storedName = UUID.randomUUID() + "." + extension;
            Path targetPath = uploadPath.resolve(storedName).normalize();

            if (!targetPath.getParent().equals(uploadPath)) {
                throw new BadRequestException("Invalid file path");
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Stored uploaded file: {} ({} bytes) -> {}",
                    originalName, file.getSize(), targetPath);

            return new StoredFile(targetPath.toString(), originalName, file.getSize());

        } catch (IOException e) {
            log.error("Failed to store uploaded file", e);
            throw new BadRequestException("Could not save uploaded file: " + e.getMessage());
        }
    }

    // Loads a previously stored file's bytes back off disk for download.
    public byte[] read(String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

            // Defends against a filePath value that somehow points
            // outside the upload directory (defense in depth - store()
            // already only ever generates paths inside uploadDir).
            if (!path.startsWith(uploadPath)) {
                throw new BadRequestException("Invalid file path");
            }

            if (!Files.exists(path)) {
                throw new BadRequestException("File not found on disk: " + filePath);
            }

            return Files.readAllBytes(path);

        } catch (IOException e) {
            log.error("Failed to read stored file: {}", filePath, e);
            throw new BadRequestException("Could not read stored file: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1) : "";
    }

    // Result of a successful store() call - what the caller needs to
    // populate ApplicationDocument.filePath / originalFileName / fileSizeBytes.
    public record StoredFile(String filePath, String originalFileName, long fileSizeBytes) {
    }
}
