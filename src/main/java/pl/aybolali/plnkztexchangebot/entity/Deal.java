package pl.aybolali.plnkztexchangebot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"requester", "provider", "ratings"})
public class Deal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0.01")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private ExchangeRequest.Currency currency;

    // 🔥 ИСПРАВЛЕНО: увеличена precision и уменьшен минимум
    @Column(name = "exchange_rate", precision = 12, scale = 8, nullable = false)
    @NotNull(message = "Exchange rate is required")
    @DecimalMin(value = "0.00000001", message = "Exchange rate must be positive")  // Обновить минимум
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_method", nullable = false)
    @NotNull(message = "Transfer method is required")
    private TransferMethod transferMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status") // 🔥 ИСПРАВЛЕНО: убрали nullable = false
    private DealStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        // ✅ Устанавливаем только если еще не установлен
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Business methods - ВСЕ ОСТАЕТСЯ КАК БЫЛО
    public void finishProcess(DealStatus dealStatus){
        this.status = dealStatus;
        this.finishedAt = LocalDateTime.now();
    }

    public void complete() { finishProcess(DealStatus.COMPLETED); }

    public void cancel() { finishProcess(DealStatus.CANCELLED); }

    public boolean isCompleted() {
        return this.status == DealStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return this.status == DealStatus.CANCELLED;
    }
    private BigDecimal roundToCurrency(BigDecimal value) {
        return BigDecimal.valueOf(Math.round(value.doubleValue() * 100.0) / 100.0);
    }

    // 🔥 ЗАМЕНИТЬ весь метод getConvertedAmount():
    public BigDecimal getConvertedAmount() {
        if (this.currency == ExchangeRequest.Currency.KZT) {
            return roundToCurrency(this.amount.multiply(this.exchangeRate));
        } else if (this.currency == ExchangeRequest.Currency.PLN) {
            return roundToCurrency(this.amount.multiply(this.exchangeRate));
        }
        return BigDecimal.ZERO;
    }

    public ExchangeRequest.Currency getOppositeCurrency() {
        if (this.currency == ExchangeRequest.Currency.KZT) {
            return ExchangeRequest.Currency.PLN;
        } else if (this.currency == ExchangeRequest.Currency.PLN) {
            return ExchangeRequest.Currency.KZT;
        }
        // Если валюта неизвестна, можно вернуть null или выбросить исключение
        throw new IllegalStateException("Unknown currency: " + this.currency);
    }


    public boolean isUserParticipant(Long userId) {
        return (requester != null && requester.getId().equals(userId)) || (provider != null && provider.getId().equals(userId));
    }
}