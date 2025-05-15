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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



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

    @Autowired
    private GameRepository gameRepository;

    private final String ACTIONS = "Нажмите на одну из кнопок";

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

    public enum ConversationState {
        AWAITING_PLACE,
        AWAITING_COST,
        AWAITING_COMMENT,
        AWAITING_TIME,
        AWAITING_PLAYERS,
        IDLE
    }
    
    private final Map<Long, ConversationState> chatStates = new ConcurrentHashMap<>();

    public Map<String, String> newGame = new HashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

             ConversationState state = chatStates.getOrDefault(chatId, ConversationState.IDLE);

            switch(state) {
                case IDLE:
                    if ("/start".equals(messageText)) {
                        registerUser(update.getMessage());
                        startCommandReceive(chatId, messageText);  
                        } 
                    break;     
                case AWAITING_PLACE:
                    newGame.put("place",messageText);
                    SendMessage message = new SendMessage();
                    message.setChatId(String.valueOf(chatId));
                    message.setText("Введите дату и время игры:");
                    executeMessage(message);
                    chatStates.put(chatId, ConversationState.AWAITING_TIME);
                    break;
                case AWAITING_TIME:
                    newGame.put("time",messageText);
                    SendMessage messageTime = new SendMessage();
                    messageTime.setChatId(String.valueOf(chatId));
                    messageTime.setText("Введите стоимость игры:");
                    executeMessage(messageTime);
                    chatStates.put(chatId, ConversationState.AWAITING_PLAYERS);
                    break;
                case AWAITING_PLAYERS:
                    newGame.put("players",messageText);
                    SendMessage messagePlayers = new SendMessage();
                    messagePlayers.setChatId(String.valueOf(chatId));
                    messagePlayers.setText("Введите количество свободных мест(сколько игроков требуется):");
                    executeMessage(messagePlayers);
                    chatStates.put(chatId, ConversationState.AWAITING_COST);
                    break;
                case AWAITING_COST:
                    newGame.put("cost",messageText);
                    SendMessage messageCost = new SendMessage();
                    messageCost.setChatId(String.valueOf(chatId));
                    messageCost.setText("Введите дополнительную информацию об игре(если её нет введите прочерк)");
                    executeMessage(messageCost);
                    chatStates.put(chatId, ConversationState.AWAITING_COMMENT);
                    break;
                case AWAITING_COMMENT:
                    newGame.put("comment",messageText);
                    createNewGame();
                    String textGame = "Ваша игра создана, Вы можете её посмотреть и отредактировать во вкладке Показать мои игры\n\n";
                    switch(newGame.get("gameType")) {
                        case "1":
                            textGame = textGame + EmojiParser.parseToUnicode("ФУТБОЛЬНЫЙ МАТЧ" + ":soccer:\n");
                            break;
                        case "2":
                            textGame = textGame + EmojiParser.parseToUnicode("БАСКЕТБОЛЬНЫЙ МАТЧ" + ":basketball:\n");
                            break;
                        default:
                           textGame = textGame + "";
                           break;
                    }
                    textGame = textGame + EmojiParser.parseToUnicode(":round_pushpin:" + " МЕСТО ИГРЫ: " + newGame.get("place") + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":alarm_clock:" + "ДАТА И ВРЕМЯ ИГРЫ: " + newGame.get("time") + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":dollar:" + " СТОИМОСТЬ ИГРЫ: " + newGame.get("cost") + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":heavy_check_mark:" + " КОЛИЧЕСТВО МЕСТ: " + newGame.get("players") + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":information_source:" + " ДОП ИНФА: " + newGame.get("comment") + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":iphone:" + " КОНТАКТ ДЛЯ СВЯЗИ: @" + newGame.get("contact") + "\n");
                    handleCallback(newGame.get("gameType"), chatId, textGame);
                    newGame.clear();
                    chatStates.put(chatId, ConversationState.IDLE);
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
                handleCallback("1", chatId, ACTIONS);
            }
            else if (callbackData.equals("game_2")) {
                String text = "Вы выбрали баскетбол";
                handleCallback("2", chatId, ACTIONS);
            }
            else if (callbackData.startsWith("CREATE_GAME_")) {
                String[] parts = callbackData.split("_", 3);
                String gameType = parts[2];
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                newGame.put("gameType",gameType);
                newGame.put("contact", update.getCallbackQuery().getFrom().getUserName());
                message.setText("Давайте введем данные вашей игры. \nНачнём с места, где вы хотите её проводить");
                try {
                    execute(message);
                    chatStates.put(chatId, ConversationState.AWAITING_PLACE);
                }
                catch (TelegramApiException e) {
                    log.error("Error ocurred ", e.getMessage());
                } 
            }
            else if (callbackData.startsWith("SHOW_MY_GAMES")) {
                String userName = update.getCallbackQuery().getFrom().getUserName();
                List<Game> gamesOfUser = gameRepository.findByUsername(userName);
                String text = "";
                for (Game game : gamesOfUser) {
                    text = text + TemplateForGame(text,game);
                }
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setText(text);
                executeMessage(sendMessage);
            }
        }
    }

    private void createNewGame() {
        Game game = new Game();
        game.setComment(newGame.get("comment"));
        game.setTimeOfGame(newGame.get("time"));
        game.setContact(newGame.get("contact"));
        game.setCost(newGame.get("cost"));
        game.setPlace(newGame.get("place"));
        game.setTypeOfGame(Long.valueOf(newGame.get("gameType")));
        gameRepository.save(game);
        log.info("Game created " + game);
    }

    private String TemplateForGame(String textGame, Game game) {
                    switch(game.getTypeOfGame().toString()) {
                        case "1":
                            textGame = textGame + EmojiParser.parseToUnicode("ФУТБОЛЬНЫЙ МАТЧ" + ":soccer:\n");
                            break;
                        case "2":
                            textGame = textGame + EmojiParser.parseToUnicode("БАСКЕТБОЛЬНЫЙ МАТЧ" + ":basketball:\n");
                            break;
                        default:
                           textGame = textGame + "";
                           break;
                    }
                    textGame = textGame + EmojiParser.parseToUnicode(":round_pushpin:" + " МЕСТО ИГРЫ: " + game.getPlace() + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":alarm_clock:" + "ДАТА И ВРЕМЯ ИГРЫ: " + game.getTimeOfGame() + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":dollar:" + " СТОИМОСТЬ ИГРЫ: " + game.getCost() + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":heavy_check_mark:" + " КОЛИЧЕСТВО МЕСТ: " + game.getPlayers() + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":information_source:" + " ДОП ИНФА: " + game.getComment() + "\n");
                    textGame = textGame + EmojiParser.parseToUnicode(":iphone:" + " КОНТАКТ ДЛЯ СВЯЗИ: @" + game.getContact() + "\n\n\n");
                    return textGame;
    }

    private void handleCallback(String type, long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        Iterable<GameType> gameList = gameTypeReposritory.findAll();
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>(); 
        InlineKeyboardButton buttonCreate = new InlineKeyboardButton();
        buttonCreate.setText("Создать новую игру");
        buttonCreate.setCallbackData("CREATE_GAME_" + type);
        rowsInline.add(Collections.singletonList(buttonCreate));
        InlineKeyboardButton buttonShow = new InlineKeyboardButton();
        buttonShow.setText("Показать все игры");
        buttonShow.setCallbackData("SHOW_GAMES");
        rowsInline.add(Collections.singletonList(buttonShow));
        InlineKeyboardButton buttonShowMy = new InlineKeyboardButton();
        buttonShowMy.setText("Показать мои игры");
        buttonShowMy.setCallbackData("SHOW_MY_GAMES");
        rowsInline.add(Collections.singletonList(buttonShowMy));
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {
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
        executeMessage(message);
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