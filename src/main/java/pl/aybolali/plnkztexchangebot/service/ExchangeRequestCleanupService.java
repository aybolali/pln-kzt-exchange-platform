package pl.aybolali.plnkztexchangebot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Сервис для автоматической очистки старых заявок
 * Выполняет 2 задачи:
 * 1. Отменяет ACTIVE заявки старше N дней (чтобы не висели вечно)
 * 2. Удаляет старые COMPLETED и CANCELLED заявки (сделки остаются в deals)
 */
@Service
@Slf4j
@ConditionalOnProperty(
        name = "cleanup.inactive-requests.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ExchangeRequestCleanupService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final int inactiveDays;
    private final int inactiveMinutes;
    private final int completedRetentionDays;
    private final int completedRetentionMinutes;
    private final int cancelledRetentionDays;
    private final int cancelledRetentionMinutes;

    public ExchangeRequestCleanupService(
            ExchangeRequestRepository exchangeRequestRepository,
            @Value("${cleanup.inactive-requests.days}") int inactiveDays,
            @Value("${cleanup.inactive-requests.minutes}") int inactiveMinutes,
            @Value("${cleanup.completed-requests.days}") int completedRetentionDays,
            @Value("${cleanup.completed-requests.minutes}") int completedRetentionMinutes,
            @Value("${cleanup.cancelled-requests.days}") int cancelledRetentionDays,
            @Value("${cleanup.cancelled-requests.minutes}") int cancelledRetentionMinutes) {

        this.exchangeRequestRepository = exchangeRequestRepository;
        this.inactiveDays = inactiveDays;
        this.inactiveMinutes = inactiveMinutes;
        this.completedRetentionDays = completedRetentionDays;
        this.completedRetentionMinutes = completedRetentionMinutes;
        this.cancelledRetentionDays = cancelledRetentionDays;
        this.cancelledRetentionMinutes = cancelledRetentionMinutes;

        log.info("🧹 ExchangeRequestCleanupService ENABLED");
        log.info("📊 Configuration:");
        log.info("   - Cancel ACTIVE after: {} days, {} minutes", inactiveDays, inactiveMinutes);
        log.info("   - Delete COMPLETED after: {} days, {} minutes", completedRetentionDays, completedRetentionMinutes);
        log.info("   - Delete CANCELLED after: {} days, {} minutes", cancelledRetentionDays, cancelledRetentionMinutes);
    }

    /**
     * Основной метод cleanup - выполняется по расписанию
     */
    @Scheduled(cron = "${cleanup.inactive-requests.cron}")
    @Transactional
    public void cleanupInactiveRequests() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        log.info("🧹 ========================================");
        log.info("🧹 CLEANUP JOB STARTED at {}", LocalDateTime.now().format(formatter));
        log.info("🧹 ========================================");

        try {
            // ЭТАП 1: Отмена старых ACTIVE заявок
            int cancelledCount = cancelOldActiveRequests();

            // ЭТАП 2: Удаление старых COMPLETED и CANCELLED заявок
            int deletedCount = deleteOldRequests();

            log.info("🎉 ========================================");
            log.info("🎉 CLEANUP COMPLETED:");
            log.info("🎉 - Cancelled ACTIVE: {}", cancelledCount);
            log.info("🎉 - Deleted old requests: {}", deletedCount);
            log.info("🎉 ========================================");

        } catch (Exception e) {
            log.error("❌ Error during cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * ЭТАП 1: Отменить старые ACTIVE заявки
     */
    private int cancelOldActiveRequests() {
        LocalDateTime activeCutoff = calculateActiveCutoffDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        log.info("🔍 STEP 1: Cancelling old ACTIVE requests");
        log.info("   Cutoff date: {}", activeCutoff.format(formatter));

        List<ExchangeRequest> inactiveRequests = exchangeRequestRepository
                .findByStatusAndCreatedAtBefore(ExchangeRequestStatus.ACTIVE, activeCutoff);

        log.info("   Found {} ACTIVE requests older than cutoff", inactiveRequests.size());

        if (inactiveRequests.isEmpty()) {
            log.info("   ✅ No ACTIVE requests to cancel");
            return 0;
        }

        for (ExchangeRequest request : inactiveRequests) {
            log.info("   📋 Request #{} - User: @{}, Amount: {} {}, Age: {} days",
                    request.getId(),
                    request.getUser().getTelegramUsername(),
                    request.getAmountNeed(),
                    request.getCurrencyNeed(),
                    java.time.Duration.between(request.getCreatedAt(), LocalDateTime.now()).toDays());
        }

        for (ExchangeRequest request : inactiveRequests) {
            request.setStatus(ExchangeRequestStatus.CANCELLED);
            request.setUpdatedAt(LocalDateTime.now());
            exchangeRequestRepository.save(request);

            log.info("   ✅ Cancelled request #{} from @{}",
                    request.getId(),
                    request.getUser().getTelegramUsername());
        }

        return inactiveRequests.size();
    }

    private int deleteOldRequests() {
        LocalDateTime completedCutoff = calculateCompletedCutoffDate();
        LocalDateTime cancelledCutoff = calculateCancelledCutoffDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        log.info("🗑️ STEP 2: Deleting old requests");
        log.info("   COMPLETED cutoff: {}", completedCutoff.format(formatter));
        log.info("   CANCELLED cutoff: {}", cancelledCutoff.format(formatter));

        int completedDeleted = exchangeRequestRepository
                .deleteOldCompletedRequests(completedCutoff);

        int cancelledDeleted = exchangeRequestRepository
                .deleteOldCancelledRequests(cancelledCutoff);

        int totalDeleted = completedDeleted + cancelledDeleted;

        log.info("   ✅ Deleted {} requests (COMPLETED: {}, CANCELLED: {})",
                totalDeleted, completedDeleted, cancelledDeleted);


        return totalDeleted;
    }

    private LocalDateTime calculateActiveCutoffDate() {
        LocalDateTime cutoff = LocalDateTime.now();
        if (inactiveDays > 0) {
            cutoff = cutoff.minusDays(inactiveDays);
        }
        if (inactiveMinutes > 0) {
            cutoff = cutoff.minusMinutes(inactiveMinutes);
        }
        return cutoff;
    }

    private LocalDateTime calculateCompletedCutoffDate() {
        LocalDateTime cutoff = LocalDateTime.now();
        if (completedRetentionDays > 0) {
            cutoff = cutoff.minusDays(completedRetentionDays);
        }
        if (completedRetentionMinutes > 0) {
            cutoff = cutoff.minusMinutes(completedRetentionMinutes);
        }
        return cutoff;
    }

    private LocalDateTime calculateCancelledCutoffDate() {
        LocalDateTime cutoff = LocalDateTime.now();
        if (cancelledRetentionDays > 0) {
            cutoff = cutoff.minusDays(cancelledRetentionDays);
        }
        if (cancelledRetentionMinutes > 0) {
            cutoff = cutoff.minusMinutes(cancelledRetentionMinutes);
        }
        return cutoff;
    }
}