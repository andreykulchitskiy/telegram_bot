package org.rohlik.damage_bot.service;
import org.rohlik.damage_bot.models.Bag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;




@Service
public class BagService {

    // JdbcTemplate for interacting with the database
    private final JdbcTemplate jdbcTemplate;

    // GoogleSheetsService for exporting data to Google Sheets
    private final GoogleSheetsService googleSheetsService;

    // Constructor for BagService, injecting JdbcTemplate and GoogleSheetsService dependencies
    @Autowired
    public BagService(JdbcTemplate jdbcTemplate, GoogleSheetsService googleSheetsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.googleSheetsService = googleSheetsService;
    }

    // Inserts information about a bag with errors into the database and exports the data to Google Sheets
    public void reportNotOk(Bag bag) {
        jdbcTemplate.update("INSERT INTO Bags (number, status, error, image, time, date) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_DATE)",
                bag.getNumber(), bag.getStatus(), bag.getError(), bag.getImage());

        // Export the bag information to Google Sheets
        googleSheetsService.exportToGoogleSheets(bag);
    }

    // Inserts information about a successfully processed bag into the database and exports the data to Google Sheets
    public void reportOk(Bag bag) {
        jdbcTemplate.update("INSERT INTO Bags (number, status, time, date) VALUES(?, ?, CURRENT_TIMESTAMP, CURRENT_DATE)",
                bag.getNumber(), bag.getStatus());

        // Export the bag information to Google Sheets
        googleSheetsService.exportToGoogleSheets(bag);
    }

    // Checks if a bag with the given number already exists in the database
    public boolean controlOfReplicate(String number) {
        String query = "SELECT COUNT(*) FROM Bags WHERE number = ?";

        // Query the database to see if the bag number exists and return true if count is greater than 0
        int count = jdbcTemplate.queryForObject(query, Integer.class, number);
        return count > 0;
    }
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
//        String query = "SELECT image FROM Bags WHERE (error IN ('PACKING', 'DAMAGE')) AND date = CURRENT_DATE";
//        return jdbcTemplate.query(query, (rs, rowNum) -> rs.getBytes("image"));
//    }


