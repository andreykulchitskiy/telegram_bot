package org.rohlik.damage_bot.service;
import org.rohlik.damage_bot.models.Bag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;


@Service
public class BagService {

    private final JdbcTemplate jdbcTemplate;
    private final GoogleSheetsService googleSheetsService;
    private final GoogleDriveService googleDriveService;

    @Autowired
    public BagService(JdbcTemplate jdbcTemplate, GoogleSheetsService googleSheetsService, GoogleDriveService googleDriveService) {
        this.jdbcTemplate = jdbcTemplate;
        this.googleSheetsService = googleSheetsService;
        this.googleDriveService = googleDriveService;
    }

    public void reportNotOk(Bag bag) {
        jdbcTemplate.update("INSERT INTO Bags (number, status, error, image, time, date) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_DATE)",
                bag.getNumber(), bag.getStatus(), bag.getError(), bag.getImage());
        exportToGoogleSheets(bag);
    }

    public void reportOk(Bag bag) {
        jdbcTemplate.update("INSERT INTO Bags (number, status, time, date) VALUES(?, ?, CURRENT_TIMESTAMP, CURRENT_DATE)",
                bag.getNumber(), bag.getStatus());
        exportToGoogleSheets(bag);
    }

    private void exportToGoogleSheets(Bag bag) {
        String spreadsheetId = "1Di8JXN6zv0bVPUOjv0Jb9kv5TRhb0LlfERPip5O9Ma8";
        String range = "bags_report!A2";

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(timeFormatter);
        String date = now.format(dateFormatter);

        String error = (bag.getError() != null) ? bag.getError() : "";
        String imageUrl = "";
        if (bag.getImage() != null) {
            try {
                imageUrl = googleDriveService.uploadImage(bag.getImage(), "image_" + bag.getNumber() + ".jpg");
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
            }
        }

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

        try {
            googleSheetsService.appendToSheet(spreadsheetId, range, values);
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }



    public boolean controlOfReplicate(String number) {
        String query = "SELECT COUNT(*) FROM Bags WHERE number = ?";
        int count = jdbcTemplate.queryForObject(query, Integer.class, number);
        return count > 0;
    }

//    public int countOfBags(String status) {
//        String query = "SELECT COUNT(*) FROM Bags WHERE status = ? AND date = CURRENT_DATE";
//        return jdbcTemplate.queryForObject(query, Integer.class, status);
//    }
//
//    public int countOfBadBags(String status, String error) {
//        String query = "SELECT COUNT(*) FROM Bags WHERE status = ? AND error = ? AND date = CURRENT_DATE";
//        return jdbcTemplate.queryForObject(query, Integer.class, status, error);
//    }
//
//    public List<byte[]> getImagesOfBadBags() {
//        String query = "SELECT image FROM Bags WHERE (error IN ('taskovani', 'damage')) AND date = CURRENT_DATE";
//        return jdbcTemplate.query(query, (rs, rowNum) -> rs.getBytes("image"));
//    }
}


