package org.rohlik.damage_bot.service;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "Damage bot";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static Sheets sheetsService;

    public static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        if (sheetsService == null) {
            String configPath = System.getenv("CONFIG_PATH");
            if (configPath == null) {
                throw new IllegalArgumentException("CONFIG_PATH environment variable is not set");
            }

            try (InputStream stream = Files.newInputStream(Paths.get(configPath))) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
                sheetsService = new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
        return sheetsService;
    }

    public void appendToSheet(String spreadsheetId, String range, List<List<Object>> values) throws IOException, GeneralSecurityException {
        ValueRange body = new ValueRange().setValues(values);
        getSheetsService().spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
    }
}
