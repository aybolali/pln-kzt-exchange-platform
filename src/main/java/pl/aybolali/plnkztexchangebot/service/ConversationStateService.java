package pl.aybolali.plnkztexchangebot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.telegram.ConversationState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для хранения состояний разговора с пользователями
 * Используется для многошагового процесса создания заявки
 */
@Service
@Slf4j
public class ConversationStateService {

    // Хранилище состояний пользователей (userId -> state)
    private final Map<Long, ConversationState> userStates = new ConcurrentHashMap<>();

    // Хранилище временных данных пользователей (userId -> Map<key, value>)
    private final Map<Long, Map<String, String>> userData = new ConcurrentHashMap<>();


    public ConversationState getState(Long userId) {
        ConversationState state = userStates.getOrDefault(userId, ConversationState.INITIAL);
        log.debug("Getting state for user {}: {}", userId, state);
        return state;
    }


    public void setState(Long userId, ConversationState state) {
        log.debug("Setting state for user {}: {}", userId, state);
        userStates.put(userId, state);
    }

    public void clearState(Long userId) {
        log.debug("Clearing state for user {}", userId);
        userStates.remove(userId);
        userData.remove(userId);
    }


    public void setUserData(Long userId, String key, String value) {
        log.debug("Setting data for user {}: {}={}", userId, key, value);
        userData.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(key, value);
    }


    public String getUserData(Long userId, String key) {
        String value = userData.getOrDefault(userId, Map.of()).get(key);
        log.debug("Getting data for user {}: {}={}", userId, key, value);
        return value;
    }

    public boolean hasUserData(Long userId, String key) {
        return userData.containsKey(userId) && userData.get(userId).containsKey(key);
    }


    public Map<String, String> getAllUserData(Long userId) {
        return userData.getOrDefault(userId, Map.of());
    }


    public void clearUserData(Long userId) {
        log.debug("Clearing data for user {}", userId);
        userData.remove(userId);
    }


    public int getActiveStatesCount() {
        return (int) userStates.values().stream()
                .filter(state -> state != ConversationState.INITIAL)
                .count();
    }
}