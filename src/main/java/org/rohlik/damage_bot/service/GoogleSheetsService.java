package org.rohlik.damage_bot.service;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.rohlik.damage_bot.models.Bag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleSheetsService {

    // Service for interacting with Google Drive to upload images
    private final GoogleDriveService googleDriveService;

    // Application name for Google Sheets API
    private static final String APPLICATION_NAME = "Damage bot";

    // JSON factory used for parsing and creating JSON
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    // Google Sheets service instance
    private static Sheets sheetsService;

    // Constructor for GoogleSheetsService, injecting GoogleDriveService dependency
    @Autowired
    public GoogleSheetsService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    // Returns the Google Sheets service instance, creating it if necessary
    public static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        if (sheetsService == null) {
            // Fetch the config path from environment variables
            String configPath = System.getenv("CONFIG_PATH");
            if (configPath == null) {
                throw new IllegalArgumentException("CONFIG_PATH environment variable is not set");
            }

            // Load Google credentials from the specified configuration file
            try (InputStream stream = Files.newInputStream(Paths.get(configPath))) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));

                // Initialize the Sheets service using the loaded credentials
                sheetsService = new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
        return sheetsService;
    }

    // Exports a Bag object to Google Sheets
    void exportToGoogleSheets(Bag bag) {
        // Google Sheets spreadsheet ID and range to append data
        String spreadsheetId = "1Di8JXN6zv0bVPUOjv0Jb9kv5TRhb0LlfERPip5O9Ma8";
        String range = "bags_report!A2";

        // Formatters for time and date
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        // Get current time and date
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(timeFormatter);
        String date = now.format(dateFormatter);

        // Check if the bag has an error, default to an empty string if not
        String error = (bag.getError() != null) ? bag.getError() : "";

        // Initialize image URL, and upload the image if one exists
        String imageUrl = "";
        if (bag.getImage() != null) {
            try {
                imageUrl = googleDriveService.uploadImage(bag.getImage(), "image_" + bag.getNumber() + ".jpg");
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
            }
        }

        // Prepare the data to be written to Google Sheets
        List<List<Object>> values = Arrays.asList(
                Arrays.asList(
                        bag.getNumber(),
                        bag.getStatus(),
                        error,
                        imageUrl,
                        timestamp,
                        date
                )
        );

        // Append data to Google Sheets
        try {
            appendToSheet(spreadsheetId, range, values);
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    // Appends values to the specified Google Sheets range
    public void appendToSheet(String spreadsheetId, String range, List<List<Object>> values) throws IOException, GeneralSecurityException {
        // Prepare the values to be added to the sheet
        ValueRange body = new ValueRange().setValues(values);

        // Append the values to the specified sheet and range
        getSheetsService().spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }
}
