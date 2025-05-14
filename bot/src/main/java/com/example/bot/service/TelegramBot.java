package com.example.bot.service;

import com.example.bot.config.*;
import com.example.bot.model.*;
import com.vdurmont.emoji.EmojiParser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    
    final BotConfig botConfig;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameTypeReposritory gameTypeReposritory;

    @Autowired
    private GameService gameService;

    public TelegramBot (BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start","Начало работы"));
        listofCommands.add(new BotCommand("/creategame","Создать новый матч"));
        listofCommands.add(new BotCommand("/mygames","Просмотреть мои матчи"));
        listofCommands.add(new BotCommand("/showallgames","Просмотреть все матчи"));
        listofCommands.add(new BotCommand("/help","Помощь по использованию данного бота"));
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        }
        catch (TelegramApiException e) {
            log.error("Error settings bot command ", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch(messageText) {
                case "/start":
                    registerUser(update.getMessage());
                    startCommandReceive(chatId, messageText);
                    break;    
                default:
                    startCommandReceive(chatId, "Такой команды нет");
                    break;
            }
        }

        else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals("game_1")) {
                String text = "Вы выбрали футбол";
                handleCallback(text, chatId, messageId);
            }
            else if (callbackData.equals("game_2")) {
                String text = "Вы выбрали баскетбол";
                handleCallback(text, chatId, messageId);
            }
        }
    }

    private void handleCallback(String text, long chatId, long messageId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Вот Ваши возможные действия");

        Iterable<GameType> gameList = gameTypeReposritory.findAll();
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>(); 
        InlineKeyboardButton buttonCreate = new InlineKeyboardButton();
        buttonCreate.setText("Создать новую комнату");
        buttonCreate.setCallbackData("CREATE_GAME");
        rowsInline.add(Collections.singletonList(buttonCreate));
        InlineKeyboardButton buttonShow = new InlineKeyboardButton();
        buttonShow.setText("Показать все комнаты");
        buttonShow.setCallbackData("SHOW_GAMES");
        rowsInline.add(Collections.singletonList(buttonShow));
        InlineKeyboardButton buttonShowMy = new InlineKeyboardButton();
        buttonShowMy.setText("Показать мои комнаты");
        buttonShowMy.setCallbackData("SHOW_MY_GAMES");
        rowsInline.add(Collections.singletonList(buttonShowMy));
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        try {
            execute(message);
            }
        catch (TelegramApiException e) {
            log.error("Error ocurred ", e.getMessage());
        } 
    }

    private void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()) {
            
            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegistredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("User saved : " + user);
        }
    }

    private void startCommandReceive(long chatId, String mes) {
        String answer = "Приветствую Вас, выберите вид спорта.";
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer);

        Iterable<GameType> gameList = gameTypeReposritory.findAll();
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        for (GameType i : gameList) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            String name = i.getGameName();
            String emoji = i.getEmoji();
            String text = EmojiParser.parseToUnicode(name + emoji);
            button.setText(text);
            button.setCallbackData("game_" + i.getTypeId());
            rowInline.add(button);
        }
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Error ocurred ", e.getMessage());
        } 
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);

        try {
            execute(sendMessage);
        }
        catch (TelegramApiException e) {
            log.error("Error occured: ", e.getMessage());
        }
    }
}