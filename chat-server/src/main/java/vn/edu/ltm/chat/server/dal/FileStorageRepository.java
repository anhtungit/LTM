package vn.edu.ltm.chat.server.dal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Encapsulates saving and loading physical attachment files on local disk.
 */
final class FileStorageRepository {
    private final Path storageDir;

    FileStorageRepository(Path storageDir) {
        this.storageDir = storageDir;
        try {
            Files.createDirectories(this.storageDir);
        } catch (Exception ignored) {
        }
    }

    void storeFile(String messageId, String fileName, String base64Data) {
        if (base64Data == null || base64Data.isBlank()) return;
        try {
            String safeName = new File(fileName).getName();
            Path filePath = storageDir.resolve(messageId + "_" + safeName);
            byte[] bytes = Base64.getDecoder().decode(base64Data);
            Files.write(filePath, bytes);
        } catch (Exception ignored) {
        }
    }

    String readFileBase64(String messageId, String fileName) {
        try {
            String safeName = new File(fileName).getName();
            Path filePath = storageDir.resolve(messageId + "_" + safeName);
            if (Files.exists(filePath)) {
                return Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
