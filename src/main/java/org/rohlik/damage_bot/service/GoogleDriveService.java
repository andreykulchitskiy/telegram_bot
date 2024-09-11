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

    // Application name for Google Drive API
    private static final String APPLICATION_NAME = "Damage bot";

    // JSON factory used for parsing and creating JSON
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    // Google Drive service instance
    private static Drive driveService;

    // Returns the Google Drive service instance, creating it if necessary
    public static Drive getDriveService() throws IOException, GeneralSecurityException {
        if (driveService == null) {
            // Fetch the config path from environment variables
            String configPath = System.getenv("CONFIG_PATH");
            if (configPath == null) {
                throw new IllegalArgumentException("CONFIG_PATH environment variable is not set");
            }

            // Load Google credentials from the specified configuration file
            try (InputStream stream = Files.newInputStream(Paths.get(configPath))) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));

                // Initialize the Drive service using the loaded credentials
                driveService = new Drive.Builder(new NetHttpTransport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
        return driveService;
    }

    // Uploads an image to Google Drive and returns its public link
    public String uploadImage(byte[] imageBytes, String imageName) throws IOException, GeneralSecurityException {

        // Create file metadata with the provided image name
        File fileMetadata = new File();
        fileMetadata.setName(imageName);

        // Create a temporary file to store the image locally before uploading
        java.io.File tempFile = java.io.File.createTempFile(imageName, ".jpg");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            // Write the image bytes to the temporary file
            fos.write(imageBytes);
        }

        // Create media content for the image to be uploaded
        FileContent mediaContent = new FileContent("image/jpeg", tempFile);

        // Upload the file to Google Drive and get its file metadata (ID and web link)
        File file = getDriveService().files().create(fileMetadata, mediaContent)
                .setFields("id,webViewLink")
                .execute();

        // Set permission to allow anyone to view the uploaded file
        Permission permission = new Permission();
        permission.setType("anyone");
        permission.setRole("reader");
        getDriveService().permissions().create(file.getId(), permission).execute();

        // Delete the temporary file after uploading
        tempFile.delete();

        // Return the public link to the uploaded file
        return file.getWebViewLink();
    }
}