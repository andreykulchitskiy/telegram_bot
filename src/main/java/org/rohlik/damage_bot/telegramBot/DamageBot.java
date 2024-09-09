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

    private final BotConfig botConfig;
    private final BagService bagService;
    private final Map<Long, Bag> bagMap = new ConcurrentHashMap<>();

    private final Map<String, String> messageValidationMap = Map.of(
            "Dobra", "ok",
            "Spatna", "notok",
            "Spatne taskovani", "taskovani",
            "Poskozeno", "damage"
    );

    @Override
    public String getBotUsername() {
        return botConfig.getDamageBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getDamageBotToken();
    }

    @Autowired
    public DamageBot(BotConfig botConfig, BagService bagService) {
        this.botConfig = botConfig;
        this.bagService = bagService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            Bag bag = bagMap.computeIfAbsent(chatId, id -> new Bag());

            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                String messageForControl = validateOfMessage(messageText);
                switch (bag.getState()) {
                    case "INIT":
                        if ("number".equals(messageForControl)) {
                            if (bagService.controlOfReplicate(messageText)) {
                                sendMessage(chatId, "Taška s tímto číslem již existuje. Zadejte jiné číslo.");
                            } else {
                                bag.setNumber(messageText);
                                bag.setState("AWAITING_STATUS");
                                sendMessageWithButtons(chatId, "ChooseStatus", "Jaký je stav tašky s číslem " + bag.getNumber() + "?");
                            }
                        } else {
                            sendMessage(chatId, "Neplatné číslo tašky. Zkuste to znovu, prosím.");
                        }
                        break;
                    case "AWAITING_STATUS":
                        if ("number".equals(messageForControl)) {
                            if (bagService.controlOfReplicate(messageText)) {
                                sendMessage(chatId, "Taška s tímto číslem již existuje. Zadejte jiné číslo.");
                            } else {
                                bag.setStatus("dobra");
                                bag.setState("COMPLETED");
                                sendMessage(chatId, "Taška s číslem " + bag.getNumber() + " byla úspěšně zaznamenána jako " + bag.getStatus() + ".");
                                bagService.reportOk(bag);
                                bagMap.remove(chatId);

                                Bag newBag = new Bag();
                                newBag.setNumber(messageText);
                                newBag.setState("AWAITING_STATUS");
                                bagMap.put(chatId, newBag);
                                sendMessageWithButtons(chatId, "ChooseStatus", "Jaký je stav tašky s číslem " + newBag.getNumber() + "?");
                            }
                        } else if ("ok".equals(messageForControl)) {
                            bag.setStatus(messageText.toLowerCase());
                            bag.setState("COMPLETED");
                            sendMessage(chatId, "Taška s číslem " + bag.getNumber() + " byla úspěšně zaznamenána jako " + bag.getStatus() + ".");
                            bagService.reportOk(bag);
                            bagMap.remove(chatId);
                        } else if ("notok".equals(messageForControl)) {
                            bag.setStatus(messageText.toLowerCase());
                            bag.setState("AWAITING_ERROR");
                            sendMessageWithButtons(chatId, "SpatnaOptions", "V čem je problém s taškou?");
                        } else {
                            sendMessage(chatId, "Vyberte jednu z nabízených možností, prosím.");
                        }
                        break;
                    case "AWAITING_ERROR":
                        if ("taskovani".equals(messageForControl) || "damage".equals(messageForControl)) {
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
                        sendMessage(chatId, "Proces byl dokončen. Můžete zadat další tašku.");
                        break;
                    default:
                        sendMessage(chatId, "Došlo k chybě! Zkuste to znovu, prosím.");
                        bagMap.remove(chatId);
                        break;
                }
            } else if (update.getMessage().hasPhoto() && "AWAITING_IMAGE".equals(bag.getState())) {
                List<PhotoSize> photos = update.getMessage().getPhoto();
                String fileId = photos.get(photos.size() - 1).getFileId();
                try {
                    GetFile getFile = new GetFile();
                    getFile.setFileId(fileId);
                    File file = execute(getFile);
                    java.io.File imageFile = downloadFile(file);

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

    private void logError(Long chatId, String errorMessage) {
        System.err.println("Error sending message to chat " + chatId + ": " + errorMessage);
    }

    private void sendMessageWithButtons(Long chatId, String mark, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        if ("ChooseStatus".equals(mark)) {
            message.setReplyMarkup(createKeyboardMarkup("Spatna", "Dobra"));
        } else if ("SpatnaOptions".equals(mark)) {
            message.setReplyMarkup(createKeyboardMarkup("Spatne taskovani", "Poskozeno"));
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

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

