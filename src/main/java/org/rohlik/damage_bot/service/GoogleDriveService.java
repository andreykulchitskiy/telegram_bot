package org.rohlik.damage_bot.service;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "Damage bot";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static Drive driveService;

    public static Drive getDriveService() throws IOException, GeneralSecurityException {
        if (driveService == null) {
            String configPath = System.getenv("CONFIG_PATH");
            if (configPath == null) {
                throw new IllegalArgumentException("CONFIG_PATH environment variable is not set");
            }

            try (InputStream stream = Files.newInputStream(Paths.get(configPath))) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));
                driveService = new Drive.Builder(new NetHttpTransport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
        return driveService;
    }

    public String uploadImage(byte[] imageBytes, String imageName) throws IOException, GeneralSecurityException {

        File fileMetadata = new File();
        fileMetadata.setName(imageName);

        java.io.File tempFile = java.io.File.createTempFile(imageName, ".jpg");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(imageBytes);
        }

        FileContent mediaContent = new FileContent("image/jpeg", tempFile);
        File file = getDriveService().files().create(fileMetadata, mediaContent)
                .setFields("id,webViewLink")
                .execute();

        Permission permission = new Permission();
        permission.setType("anyone");
        permission.setRole("reader");
        getDriveService().permissions().create(file.getId(), permission).execute();

        tempFile.delete();

        return file.getWebViewLink();
    }
}
