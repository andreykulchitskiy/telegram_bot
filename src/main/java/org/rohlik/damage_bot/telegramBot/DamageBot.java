package org.rohlik.damage_bot.telegramBot;

import org.rohlik.damage_bot.models.Bag;
import org.rohlik.damage_bot.service.BagService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class DamageBot extends TelegramLongPollingBot {

    // Configuration object that holds bot credentials (name, token, etc.)
    private final BotConfig botConfig;
    // Service for handling business logic related to bags
    private final BagService bagService;
    // Map to store the current state of conversations with users, based on their chat ID
    private final Map<Long, Bag> bagMap = new ConcurrentHashMap<>();

    // Map for validating message text and assigning specific keywords to internal actions
    private final Map<String, String> messageValidationMap = Map.of(
            "Dobra", "OK",
            "Spatna", "NOTOK",
            "Spatne taskovani", "PACKING",
            "Poskozeno", "DAMAGE"
    );

    @Override
    public String getBotUsername() {
        // Returns the bot's username from the configuration
        return botConfig.getDamageBotName();
    }

    @Override
    public String getBotToken() {
        // Returns the bot's token from the configuration
        return botConfig.getDamageBotToken();
    }

    // Constructor that initializes the bot's configuration and bag service
    @Autowired
    public DamageBot(BotConfig botConfig, BagService bagService) {
        this.botConfig = botConfig;
        this.bagService = bagService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Check if the update contains a message
        if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            // Retrieves or creates a new Bag object associated with the chat ID
            Bag bag = bagMap.computeIfAbsent(chatId, id -> new Bag());

            // Process text messages from the user
            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                String messageForControl = validateOfMessage(messageText);

                // Switch based on the current state of the conversation
                switch (bag.getState()) {
                    case "INIT":
                        // Check if the message is a valid bag number
                        if ("number".equals(messageForControl)) {
                            if (bagService.controlOfReplicate(messageText)) {
                                // Inform the user if a bag with the same number already exists
                                sendMessage(chatId, "Taška s tímto číslem již existuje. Zadejte jiné číslo.");
                            } else {
                                // Set the bag number and move to the next state
                                bag.setNumber(messageText);
                                bag.setState("AWAITING_STATUS");
                                sendMessageWithButtons(chatId, "ChooseStatus", "Jaký je stav tašky s číslem " + bag.getNumber() + "?");
                            }
                        } else {
                            // Inform the user if the bag number is invalid
                            sendMessage(chatId, "Neplatné číslo tašky. Zkuste to znovu, prosím.");
                        }
                        break;

                    case "AWAITING_STATUS":
                        // Handle the bag status selection or number re-entry
                        if ("number".equals(messageForControl)) {
                            if (bagService.controlOfReplicate(messageText)) {
                                sendMessage(chatId, "Taška s tímto číslem již existuje. Zadejte jiné číslo.");
                            } else {
                                bag.setStatus("dobra");
                                bag.setState("COMPLETED");
                                sendMessage(chatId, "Taška s číslem " + bag.getNumber() + " byla úspěšně zaznamenána jako " + bag.getStatus() + ".");
                                bagService.reportOk(bag);
                                bagMap.remove(chatId);

                                // Start a new process for the next bag
                                Bag newBag = new Bag();
                                newBag.setNumber(messageText);
                                newBag.setState("AWAITING_STATUS");
                                bagMap.put(chatId, newBag);
                                sendMessageWithButtons(chatId, "ChooseStatus", "Jaký je stav tašky s číslem " + newBag.getNumber() + "?");
                            }
                        } else if ("OK".equals(messageForControl)) {
                            bag.setStatus(messageText.toLowerCase());
                            bag.setState("COMPLETED");
                            sendMessage(chatId, "Taška s číslem " + bag.getNumber() + " byla úspěšně zaznamenána jako " + bag.getStatus() + ".");
                            bagService.reportOk(bag);
                            bagMap.remove(chatId);
                        } else if ("NOTOK".equals(messageForControl)) {
                            bag.setStatus(messageText.toLowerCase());
                            bag.setState("AWAITING_ERROR");
                            sendMessageWithButtons(chatId, "BadOptions", "V čem je problém s taškou?");
                        } else {
                            sendMessage(chatId, "Vyberte jednu z nabízených možností, prosím.");
                        }
                        break;

                    case "AWAITING_ERROR":
                        // Handle specific error conditions (packing or damage)
                        if ("PACKING".equals(messageForControl) || "DAMAGE".equals(messageForControl)) {
                            bag.setError(messageForControl);
                            bag.setState("AWAITING_IMAGE");
                            sendMessage(chatId, "Prosím, vyfoťte tašku a pošlete fotku.");
                        } else {
                            sendMessage(chatId, "Vyberte jednu z nabízených možností, prosím.");
                        }
                        break;

                    case "AWAITING_IMAGE":
                        sendMessage(chatId, "Prosím, vyfoťte tašku a pošlete fotku.");
                        break;

                    case "COMPLETED":
                        // Inform the user that the process is complete and they can submit another bag
                        sendMessage(chatId, "Proces byl dokončen. Můžete zadat další tašku.");
                        break;

                    default:
                        // Handle any unexpected state
                        sendMessage(chatId, "Došlo k chybě! Zkuste to znovu, prosím.");
                        bagMap.remove(chatId);
                        break;
                }
            } else if (update.getMessage().hasPhoto() && "AWAITING_IMAGE".equals(bag.getState())) {
                // Handle photo submission for bag verification
                List<PhotoSize> photos = update.getMessage().getPhoto();
                String fileId = photos.get(photos.size() - 1).getFileId();
                try {
                    // Retrieve and process the photo
                    GetFile getFile = new GetFile();
                    getFile.setFileId(fileId);
                    File file = execute(getFile);
                    java.io.File imageFile = downloadFile(file);

                    // Convert photo to byte array and save it to the bag
                    byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                    bag.setImage(imageBytes);
                    bag.setState("COMPLETED");

                    sendMessage(chatId, "Fotka byla úspěšně přijata. Pokračujte v zadávání tašek.");
                    bagService.reportNotOk(bag);
                    bagMap.remove(chatId);
                } catch (IOException | TelegramApiException e) {
                    e.printStackTrace();
                    logError(chatId, e.getMessage());
                }
            }
        }
    }

    // Validates and classifies the incoming message text
    private String validateOfMessage(String message) {
        String regex = "\\d{10}-[A-Z]-\\d{1,2}";

        if (message.startsWith("/")) {
            switch (message) {
                case "/report":
                    return "report";
                case "/images":
                    return "images";
                default:
                    return "unknown_command";
            }
        }
        if (message.matches(regex)) {
            return "number";
        }

        return messageValidationMap.getOrDefault(message, "error");
    }

    // Sends a message to the user
    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            logError(chatId, e.getMessage());
        }
    }

    // Logs any errors that occur while sending messages
    private void logError(Long chatId, String errorMessage) {
        System.err.println("Error sending message to chat " + chatId + ": " + errorMessage);
    }

    // Sends a message with a custom keyboard to the user
    private void sendMessageWithButtons(Long chatId, String mark, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        if ("ChooseStatus".equals(mark)) {
            message.setReplyMarkup(createKeyboardMarkup("Spatna", "Dobra"));
        } else if ("BadOptions".equals(mark)) {
            message.setReplyMarkup(createKeyboardMarkup("Spatne taskovani", "Poskozeno"));
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Creates a custom keyboard with specified options
    private ReplyKeyboardMarkup createKeyboardMarkup(String... options) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.addAll(List.of(options));
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}