package pl.aybolali.plnkztexchangebot.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import pl.aybolali.plnkztexchangebot.dto.ExchangeRateDTO;
import pl.aybolali.plnkztexchangebot.entity.Deal;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.service.RatingService;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class TelegramMessageFormatter {

    public String formatUsernameRequired() {
        return """
                ⚠️ <b>Требуется @username</b>
                
                Для использования бота необходимо установить имя пользователя в Telegram.
                
                <b>📝 Как установить @username:</b>
                
                1️⃣ Откройте <b>Настройки</b> Telegram
                2️⃣ Нажмите на <b>своё имя</b>
                3️⃣ Выберите <b>"Имя пользователя"</b>
                4️⃣ Придумайте уникальное имя (например: <code>astana01</code>)
                5️⃣ Нажмите <b>Сохранить</b>
                6️⃣ Вернитесь в бот и нажмите /start
                
                ℹ️ <b>Зачем это нужно?</b>
                Username (@имя) необходим для связи с другими пользователями при обмене валюты.
                
                💡 <b>Примеры хороших username:</b>
                • @arystanzholbarysovich
                • @almaty2025
                • @arman2003
                
                """;
    }

    public String formatUserNotFoundError() {
        return """
                ⚠️ <b>Требуется регистрация</b>
                
                Используйте команду /start чтобы начать работу с ботом.
                """;
    }

    public String formatVerificationRequired() {
        return """
                🔒 <b>Требуется верификация телефона</b>
                
                Для использования этой функции необходимо верифицировать телефон.
                
                Используйте /start для верификации.
                """;
    }

    public String formatTechnicalError() {
        return """
                🚨 <b>Произошла ошибка</b>
                
                Попробуйте повторить операцию через минуту.
                Если проблема повторяется - напишите в поддержку: @dunnosorry
                """;
    }

    public String formatVerifiedUserWelcome(User user, BigDecimal currentRate) {
        return String.format("""
                👋 <b>Добро пожаловать, %s!</b>
                
                📊 <b>Ваша статистика:</b>
                ⭐ Рейтинг: %.1f/5.0
                💼 Обменов: %d
                
                💹 <b>Текущий курс:</b>
                1 PLN = %.2f KZT
                
                Используйте меню для списка команд
                """,
                escapeHtml(user.getFullName()),
                user.getTrustRating().doubleValue(),
                user.getSuccessfulDeals(),
                currentRate);
    }

    public String formatMandatoryPhoneVerificationRequest(User user, BigDecimal currentRate) {
        return String.format("""
                👋 <b>Добро пожаловать в PLN-KZT Exchange, %s!</b>
                
                🔒 <b>Требуется верификация номера телефона</b>
                
                Для обеспечения безопасных обменов, все пользователи должны подтвердить свой номер телефона.
                
                <b>Почему это важно:</b>
                ✅ Создание доверия между участниками обмена
                ✅ Защита от мошенничества и скама
                ✅ Поддержание честности платформы
                
                💹 <b>Текущий курс на сегодня:</b>
                🇵🇱→🇰🇿 1 PLN = %.2f KZT
                
                <b>🔐 Конфиденциальность:</b>
                Ваш номер телефона защищен. Он виден только активным встречным сторонам по сделкам во время координации обмена.
                
                👇 <b>Нажмите кнопку для верификации:</b>
                """,
                escapeHtml(user.getFirstName()),
                currentRate);
    }

    /**
     * Форматирование завершения сделки для АВТОРА заявки
     */
    public String formatDealCompletionForAuthor(
            Deal deal,
            BigDecimal authorReceived,
            ExchangeRequest.Currency requestCurrency,
            BigDecimal authorGave,
            ExchangeRequest.Currency oppositeCurrency,
            ExchangeRequest updatedRequest) {

        StringBuilder sb = new StringBuilder();
        sb.append("✅ <b>Обмен завершён!</b>\n\n");

        // Что получил
        sb.append("📥 Вы получили:\n");
        sb.append("   <b>").append(formatAmount(authorReceived))
                .append(" ").append(requestCurrency).append("</b>\n");

        // Что отдал
        sb.append("📤 Вы отдали:\n");
        sb.append("   ≈ <b>").append(formatAmount(authorGave))
                .append(" ").append(oppositeCurrency).append("</b>\n");

        // Информация о заявке
        if (updatedRequest != null) {
            sb.append("\n");

            if (updatedRequest.getAmountNeed().compareTo(BigDecimal.ZERO) > 0) {
                // Частичное выполнение
                sb.append("📋 Осталось в заявке: <b>")
                        .append(formatAmount(updatedRequest.getAmountNeed()))
                        .append(" ").append(updatedRequest.getCurrencyNeed()).append("</b>\n");
                sb.append("📊 Статус: <b>ACTIVE</b>\n");
            } else {
                // Полное выполнение
                sb.append("🎉 Ваша заявка выполнена полностью!\n");
                sb.append("📊 Статус: <b>COMPLETED</b>\n");
            }
        }

        sb.append("\n💡 <i>Вы можете оценить обмен (опционально)</i>");

        return sb.toString();
    }

    /**
     * Форматирование завершения сделки для ОТКЛИКНУВШЕГОСЯ
     */
    public String formatDealCompletionForResponder(
            Deal deal,
            BigDecimal responderReceived,
            ExchangeRequest.Currency receivedCurrency,
            BigDecimal responderGave,
            ExchangeRequest.Currency gaveCurrency,
            ExchangeRequest responderRequest) {

        StringBuilder sb = new StringBuilder();
        sb.append("✅ <b>Обмен завершён!</b>\n\n");

        // Что получил
        sb.append("📥 Вы получили:\n");
        sb.append("   ≈ <b>").append(formatAmount(responderReceived))
                .append(" ").append(receivedCurrency).append("</b>\n");

        // Что отдал
        sb.append("📤 Вы отдали:\n");
        sb.append("   <b>").append(formatAmount(responderGave))
                .append(" ").append(gaveCurrency).append("</b>\n");

        // Информация о заявке responder'а (если есть)
        if (responderRequest != null && responderRequest.getStatus() == ExchangeRequestStatus.ACTIVE) {
            sb.append("\n");

            if (responderRequest.getAmountNeed().compareTo(BigDecimal.ZERO) > 0) {
                // Частичное выполнение
                sb.append("📋 Осталось в заявке: <b>")
                        .append(formatAmount(responderRequest.getAmountNeed()))
                        .append(" ").append(responderRequest.getCurrencyNeed()).append("</b>\n");
                sb.append("📊 Статус: <b>ACTIVE</b>\n");
            } else {
                // Полное выполнение
                sb.append("🎉 Ваша заявка выполнена полностью!\n");
                sb.append("📊 Статус: <b>COMPLETED</b>\n");
            }
        }

        sb.append("\n💡 <i>Вы можете оценить обмен (опционально)</i>");

        return sb.toString();
    }
    public String formatVerificationSuccess(User user) {
        return String.format("""
                ✅ <b>Верификация успешна!</b>
                
                👤 %s
                📱 Телефон подтверждён
                
                Теперь вы можете пользоваться всеми функциями бота!
                
                Используйте /menu для списка команд.
                Для справок - /help 
                """,
                escapeHtml(user.getFullName()));
    }


    public String formatPhoneVerificationError() {
        return """
                ❌ <b>Ошибка верификации</b>
                
                Произошла ошибка при обработке вашего номера телефона.
                
                Попробуйте ещё раз или обратитесь в поддержку: @dunnosorry
                """;
    }

    // ========================================
    // МЕНЮ
    // ========================================

    public String formatMainMenu(User user) {
        return String.format("""
                🏠 <b>Главное меню</b>
                
                👤 %s
                ⭐ Рейтинг: %.1f
                💼 Обменов: %d
                
                Выберите действие:
                """,
                escapeHtml(user.getFullName()),
                user.getTrustRating().doubleValue(),
                user.getSuccessfulDeals());
    }

    // ========================================
    // СПРАВКА
    // ========================================

    public String formatHelpMessage() {
        return """
            📚 <b>Справка по PLN-KZT Exchange Bot</b>
            
            <b>🤖 Основные команды:</b>
            
            /start - Регистрация в системе
            /menu - Главное меню
            /need - Создать заявку на обмен
            /search - Поиск заявок
            /my_requests - Мои активные заявки
            /history - История обменов
            /profile - Мой профиль
            /rates - Курсы валют
            /help - Эта справка
            
            <b>💰 Как это работает:</b>
            1. Создайте заявку (/need) с нужной суммой
            2. Или найдите подходящую (/search)
            3. Свяжитесь с пользователем
            4. Обменяйтесь и подтвердите обмен
            5. Оцените пользователя
            
            <b>🛡️ Безопасность:</b>
            • Все пользователи верифицированы 📱✅
            • Проверяйте рейтинг перед обменом
            • Начинайте с небольших сумм
            • Будьте вежливы и честны
            
            <b>🔐 Конфиденциальность:</b>
            • Номер телефона защищён
            • Виден только участникам активных обменов
            • Никогда не публикуется открыто
            
            <b>💡 Советы:</b>
            • Указывайте комментарий в заявках
            • Оценивайте обменщиков после обмена
            • Следите за своим рейтингом
            • Закрывайте неактуальные заявки
            
            <b>❓ FAQ:</b>
            
            <b>Q: Почему нужна верификация телефона?</b>
            A: Для безопасных обменов и создания доверия между пользователями.
            
            <b>Q: Кто видит мой номер?</b>
            A: Только участники активных обменов, с которыми вы договорились о сделке.
            
            <b>Q: Безопасен ли мой номер?</b>
            A: Да! Мы никогда не передаём его третьим лицам и не публикуем открыто.
            
            <b>Q: Есть ли гарантии возврата средств?</b>
            A: Нет. Платформа только соединяет людей для обмена.
            Проверяйте рейтинг пользователя и будьте осторожны!
            
            <b>Q: Как повысить свой рейтинг?</b>
            A: Совершайте обмены честно и просите пользователей оценивать вас после обмена.
            
            💬 <b>Поддержка:</b> @dunnosorry
            """;
    }

    // ========================================
    // КУРСЫ ВАЛЮТ
    // ========================================

    public String formatExchangeRates(ExchangeRateDTO rateDTO) {
        return String.format("""
                        💹 <b>Курсы валют</b>
                                    
                        🇵🇱→🇰🇿 1 PLN = %.2f KZT
                        🇰🇿→🇵🇱 1 KZT = %.6f PLN
                                    
                        📅 Дата: %s
                        🏦 Источник: <a href="https://nationalbank.kz/ru/exchangerates/ezhednevnye-oficialnye-rynochnye-kursy-valyut">Нацбанк Казахстана</a>
                                                                                                                                                              
                                    
                        💡 Официальный курс, обновляется ежедневно
                        """,
                rateDTO.plnToKzt(),
                rateDTO.kztToPln(),
                rateDTO.date());
    }

    public String formatExchangeRateError() {
        return """
                ❌ <b>Ошибка получения курсов</b>
                
                Не удалось получить актуальные курсы валют.
                Попробуйте позже.
                """;
    }

    // ========================================
    // СОЗДАНИЕ ЗАЯВКИ (/need)
    // ========================================

    public String formatNeedStep1Currency() {
        return """
                💰 <b>Создание заявки на обмен</b>
                
                <b>Шаг 1/4:</b> Какую валюту вы хотите получить?
                
                ℹ️ Например:
                • Нужны злоты, есть тенге → выберите 🇵🇱 PLN
                • Нужны тенге, есть злоты → выберите 🇰🇿 KZT
                """;
    }

    public String formatNeedStep2Amount(String currency) {
        return String.format("""
                ✅ Валюта: <b>%s</b>
                
                <b>Шаг 2/4:</b> Введите сумму
                
                Например: <code>50000</code>, <code>50 000</code>или <code>1000.50</code>
                
                💡 Минимум: 10 %s
                """,
                currency,
                currency);
    }

    public String formatNeedStep3TransferMethod(BigDecimal amount, String currency) {
        return String.format("""
                ✅ Сумма: <b>%s %s</b>
                
                <b>Шаг 3/4:</b> Выберите способ перевода
                """,
                formatAmount(amount),
                currency);
    }

    public String formatNeedStep4Comment(String methodName) {
        return String.format("""
                ✅ Способ перевода: <b>%s</b>
                
                <b>Шаг 4/4:</b> Добавьте комментарий (необязательно)
                
                💡 Например:
                • "Обмен через Kaspi"
                • "Могу встретиться в центре Варшавы"
                • "Предпочитаю Revolut"
                
                Или нажмите "Пропустить"
                """,
                methodName);
    }

    public String formatExchangeRequestCreated(
            BigDecimal amount,
            String currency,
            BigDecimal equivalent,
            String otherCurrency,
            String methodName,
            String comment) {

        return String.format("""
                ✅ <b>Заявка создана!</b> 
                
                💰 Сумма: %s %s (вам нужно)
                ≈ %s %s
                
                🔄 Способ: %s
                📝 Комментарий: %s
                
                🔍 Ваша заявка теперь видна другим пользователям.
                """,
                formatAmount(amount),
                currency,
                formatAmount(equivalent),
                otherCurrency,
                methodName,
                comment != null ? comment : "—");
    }

    public String formatAmountTooSmallError() {
        return "❌ Минимальная сумма: 10\n\nВведите сумму ещё раз:";
    }

    /**
     * Форматирование списка обменов для истории (улучшенная версия)
     */
    public String formatDealsHistoryPage(
            Page<Deal> dealsPage,
            List<Deal> deals,
            User currentUser,
            int page,
            RatingService ratingService) {

        StringBuilder sb = new StringBuilder();
        sb.append("📜 <b>История обменов</b>\n");

        if (dealsPage.getTotalPages() > 1) {
            sb.append("Страница ").append(page + 1)
                    .append(" из ").append(dealsPage.getTotalPages()).append("\n");
        }

        sb.append("Всего обменов: ").append(dealsPage.getTotalElements()).append("\n\n");

        for (int i = 0; i < deals.size(); i++) {
            Deal deal = deals.get(i);
            int globalIndex = page * 10 + i + 1;

            boolean isRequester = deal.getRequester().getId().equals(currentUser.getId());
            User counterparty = isRequester ? deal.getProvider() : deal.getRequester();

            // Вычисляем обе стороны обмена
            BigDecimal receivedAmount;
            BigDecimal givenAmount;
            ExchangeRequest.Currency receivedCurrency;
            ExchangeRequest.Currency givenCurrency;

            if (isRequester) {
                // Автор заявки ПОЛУЧИЛ сумму в валюте заявки
                receivedAmount = deal.getAmount();
                receivedCurrency = deal.getCurrency();
                // И ОТДАЛ конвертированную сумму в противоположной валюте
                givenAmount = deal.getConvertedAmount();
                givenCurrency = deal.getOppositeCurrency();
            } else {
                // Откликнувшийся ОТДАЛ сумму в валюте заявки
                givenAmount = deal.getAmount();
                givenCurrency = deal.getCurrency();
                // И ПОЛУЧИЛ конвертированную сумму в противоположной валюте
                receivedAmount = deal.getConvertedAmount();
                receivedCurrency = deal.getOppositeCurrency();
            }

            sb.append("<b>").append(globalIndex).append(".</b> 🆔 Обмен #").append(deal.getId()).append("\n");
            sb.append("   📅 ").append(deal.getFinishedAt().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");

            // ✅ ПОКАЗЫВАЕМ ОБЕ СТОРОНЫ ОБМЕНА
            sb.append("  <b> 📥 Получил(а): ").append(formatAmount(receivedAmount))
                    .append(" ").append(receivedCurrency).append("</b>\n");
            sb.append("   📤 Отдал(а): <b>").append(formatAmount(givenAmount))
                    .append(" ").append(givenCurrency).append("</b>\n");

            // ✅ КУРС ВСЕГДА 1 PLN = X KZT (из БД, на момент сделки)
            BigDecimal plnToKztRate = getPLNtoKZTRate(deal);
            sb.append("   💱 1 PLN = ").append(formatRate(plnToKztRate)).append(" KZT\n");

            sb.append("   👤 С: @").append(counterparty.getTelegramUsername()).append("\n");

            boolean isRated = ratingService.existsByDealIdAndRaterId(deal.getId(), currentUser.getId());
            if (isRated) {
                sb.append("   ⭐ Оценена\n");
            } else {
                sb.append("   💬 Можно оценить\n");
            }
            sb.append("\n");
        }

        sb.append("💡 Нажмите на обмен для подробностей");

        return sb.toString();
    }

    private BigDecimal getPLNtoKZTRate(Deal deal) {
        if (deal.getCurrency() == ExchangeRequest.Currency.PLN) {
            // В сделке уже хранится курс PLN→KZT
            return deal.getExchangeRate();
        } else {
            // В сделке хранится курс KZT→PLN, нужно инвертировать
            // PLN→KZT = 1 / (KZT→PLN)
            // Используем 8 знаков для точности
            return BigDecimal.ONE.divide(deal.getExchangeRate(), 8, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Форматирует курс обмена (округление до 2 знаков)
     * @param rate курс обмена
     * @return отформатированная строка курса
     */
    private String formatRate(BigDecimal rate) {
        return rate.setScale(2, BigDecimal.ROUND_HALF_UP).toString();
    }

    /**
     * Уведомление автору заявки о новом предложении обмена
     */
    public String formatNewDealProposalNotification(
            User responder,
            BigDecimal authorWillReceive,
            ExchangeRequest.Currency requestCurrency,
            BigDecimal authorWillGive,
            ExchangeRequest.Currency oppositeCurrency) {

        return """
            🔔 <b>Новое предложение обмена!</b>
            
            👤 От: @%s
            ⭐ Рейтинг: %s | Обменов: %d
            📱 Номер телефона: %s
            
            💰 <b>Детали обмена:</b>
            📥 Вы получите: <b>%s %s</b>
            📤 Вы отдадите: ≈ <b>%s %s</b>
            💬 Подтвердите обмен после завершения сделки
            """.formatted(
                responder.getTelegramUsername(),
                formatRating(responder.getTrustRating()),
                responder.getSuccessfulDeals(),
                responder.getPhone(),
                formatAmount(authorWillReceive),
                requestCurrency,
                formatAmount(authorWillGive),
                oppositeCurrency
        );
    }

    public String formatInvalidAmountFormatError() {
        return "❌ Неверный формат суммы\n\nВведите число (например: 50000, 50 000 или 1000,50):";
    }

    public String formatRequestCancelled() {
        return "❌ Создание заявки отменено";
    }

    public String formatRequestCreationError() {
        return "❌ Ошибка при создании заявки. Попробуйте ещё раз: /need";
    }

    // ========================================
    // ПОИСК (/search)
    // ========================================

    // ⭐ НЕТ РЕЗУЛЬТАТОВ ПОИСКА
    public String formatNoSearchResults() {
        return """
                🔍 <b>Ничего не найдено</b>
                
                Нет активных предложений.
                
                💡 Проверяйте поиск периодически - новые заявки появляются постоянно!
                """;
    }

    // ⭐ СПИСОК НАЙДЕННЫХ ЗАЯВОК С КОНВЕРТАЦИЕЙ
    public String formatSearchResultsList(
            List<ExchangeRequest> requests,
            String theyWantWithFlag,
            String withFlag,
            ExchangeRequest.Currency iWant,
            BigDecimal currentRate) {

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 <b>Найдено предложений: ").append(requests.size()).append("</b>\n\n");
        sb.append("Пользователи, которым нужен <b>").append(theyWantWithFlag).append("</b>\n");
        sb.append("(у них есть <b>").append(withFlag).append("</b>):\n\n");

        for (int i = 0; i < Math.min(requests.size(), 10); i++) {  // ⭐ МАКСИМУМ 10
            ExchangeRequest req = requests.get(i);

            // ⭐ КОНВЕРТИРУЕМ В МОЮ ВАЛЮТУ
            BigDecimal theirAmount = req.getAmountNeed();
            BigDecimal convertedAmount = theirAmount.multiply(currentRate);

            sb.append("<b>").append(i + 1).append(".</b> 👤 @").append(req.getUser().getTelegramUsername()).append("\n");
            sb.append("   💰 <b>").append(formatAmount(req.getAmountNeed()))
                    .append(" ").append(req.getCurrencyNeed()).append("</b>\n");
            sb.append("   💱 Вы получите: ≈ <b>").append(formatAmount(convertedAmount))
                    .append(" ").append(iWant).append("</b>\n");
            sb.append("   🔄 ").append(getTransferMethodName(req.getTransferMethod().name())).append("\n");

            if (req.getNotes() != null && !req.getNotes().isEmpty()) {
                sb.append("   📝 ").append(req.getNotes()).append("\n");
            }

            sb.append("   ⭐ Рейтинг: ").append(formatRating(req.getUser().getTrustRating()))
                    .append(" | Обменов: ").append(req.getUser().getSuccessfulDeals()).append("\n\n");
        }

        sb.append("💡 Нажмите кнопку с номером для отклика");

        return sb.toString();
    }

    // ⭐ ПОВТОРНЫЙ ПОИСК ИЛИ ВЫБОР ДРУГОЙ ВАЛЮТЫ - Есть последний выбор - предлагаем повторить или выбрать заново
    public String formatRepeatSearchOrChooseAnother(String lastSearchCurrency) {
        return String.format("""
                🔍 <b>Поиск заявок</b>
                
                💡 Последний поиск: <b>%s</b>
                
                Повторить поиск или выбрать другую валюту?
                """,
                lastSearchCurrency);
    }

    // ⭐ ВЫБОР ВАЛЮТЫ ДЛЯ ПОИСКА
    public String formatSearchCurrencySelection() {
        return """
                🔍 <b>Какую валюту ищете?</b>
                
                Выберите валюту, которую хотят получить другие пользователи.
                
                💡 Например:
                • Нужен KZT, у вас есть PLN → выберите <b>🇰🇿 Ищу KZT</b>
                • Нужен PLN, у вас есть KZT → выберите <b>🇵🇱 Ищу PLN</b>
                """;
    }

    // ========================================
    // ОТКЛИК НА ЗАЯВКУ
    // ========================================

    // ⭐ УВЕДОМЛЕНИЕ АВТОРУ ЗАЯВКИ О НОВОМ ПРЕДЛОЖЕНИИ
    public String formatNewOfferNotificationToAuthor(
            User responder,
            BigDecimal authorWillReceive,
            ExchangeRequest.Currency requestCurrency,
            BigDecimal authorWillGive,
            ExchangeRequest.Currency oppositeCurrency) {

        return String.format("""
                🔔 <b>Новое предложение обмена!</b>
                
                 💰 <b>Детали обмена:</b>
                📥 Вы получите: <b>%s %s</b>
                📤 Вы отдадите: ≈ <b>%s %s</b>
                
                👤 От: @%s
                ⭐ Рейтинг: %s | Обменов: %d
                📱 Номер телефона: %s
                
                💬 <b>Подтвердите обмен после завершения сделки</b>
                """,

                formatAmount(authorWillReceive),
                requestCurrency.toString(),
                formatAmount(authorWillGive),
                oppositeCurrency.toString(),
                responder.getTelegramUsername(),
                formatRating(responder.getTrustRating()),
                responder.getSuccessfulDeals(),
                responder.getPhone());
    }

    // ⭐ ПОДТВЕРЖДЕНИЕ ОТКЛИКНУВШЕМУСЯ
    public String formatOfferSentConfirmationToResponder(
            User author,
            BigDecimal authorWillGive,
            ExchangeRequest.Currency oppositeCurrency,
            BigDecimal authorWillReceive,
            ExchangeRequest.Currency requestCurrency) {

        return String.format("""
                ✅ <b>Предложение отправлено!</b>
                
                💰 <b>Детали обмена:</b>
                📥 Вы получите: ≈ <b>%s %s</b>
                📤 Вы отдадите: <b>%s %s</b>
                
                👤 Получатель: @%s
                📱 Номер телефона: %s
                
                💬 Автор заявки получил уведомление.
                Ожидайте подтверждения обмена!
                """,
                formatAmount(authorWillGive),
                oppositeCurrency.toString(),
                formatAmount(authorWillReceive),
                requestCurrency.toString(),
                author.getTelegramUsername(),
                author.getPhone());
    }

    public String formatDealCompletedNotification(
            Deal deal,
            ExchangeRequest userRequest,
            Boolean isRequester) {

        StringBuilder sb = new StringBuilder();
        sb.append("✅ <b>Обмен завершён!</b>\n\n");

        // ⭐ ВСЕГДА СНАЧАЛА "ВЫ ПОЛУЧИЛИ" (главное!)
        if (isRequester) {
            // Автор заявки (requester)
            sb.append("📥 Вы получили:\n");
            sb.append("   <b>").append(formatAmount(deal.getAmount()))
                    .append(" ").append(deal.getCurrency()).append("</b>\n");

            sb.append("📤 Вы отдали:\n");
            sb.append("   ≈ <b>").append(formatAmount(deal.getConvertedAmount()))
                    .append(" ").append(deal.getOppositeCurrency()).append("</b>\n");
        } else {
            // Откликнувшийся (provider)
            sb.append("📥 Вы получили:\n");
            sb.append("   ≈ <b>").append(formatAmount(deal.getConvertedAmount()))
                    .append(" ").append(deal.getOppositeCurrency()).append("</b>\n");

            sb.append("📤 Вы отдали:\n");
            sb.append("   <b>").append(formatAmount(deal.getAmount()))
                    .append(" ").append(deal.getCurrency()).append("</b>\n");
        }

        // ⭐ ПОКАЗЫВАЕМ ОСТАТОК ЕСЛИ ЕСТЬ ЗАЯВКА
        if (userRequest != null) {
            sb.append("\n");

            if (userRequest.getAmountNeed().compareTo(BigDecimal.ZERO) > 0) {
                // Частичное выполнение
                sb.append("📋 Осталось в заявке: <b>")
                        .append(formatAmount(userRequest.getAmountNeed()))
                        .append(" ").append(userRequest.getCurrencyNeed()).append("</b>\n");
                sb.append("📊 Статус: <b>ACTIVE</b>\n");
            } else {
                // Полное выполнение
                sb.append("🎉 Ваша заявка выполнена полностью!\n");
                sb.append("📊 Статус: <b>COMPLETED</b>\n");
            }
        }

        sb.append("\n💡 <i>Вы можете оценить обмен (опционально)</i>");

        return sb.toString();
    }

    // ========================================
    // РЕЙТИНГ
    // ========================================

    // ⭐ СПАСИБО ЗА ОЦЕНКУ
    public String formatRatingThankYou(
            String ratedUsername,
            int ratingValue,
            Long dealId) {

        return String.format("""
                ⭐ <b>Спасибо за оценку!</b>
                
                Вы оценили <b>@%s</b>
                Ваша оценка: %s (%d/5)
                🆔 Обмена номер: <code>%d</code>
                
                💡 Ваши оценки помогают другим пользователям
                принять решение о сотрудничестве
                """,
                ratedUsername,
                "⭐".repeat(ratingValue),
                ratingValue,
                dealId);
    }

    // ⭐ ОЦЕНКА ПРОПУЩЕНА
    public String formatRatingSkipped(Deal deal, Boolean isRequester) {
        return """
            ℹ️ <b>Оценка пропущена</b>
            
            🆔 Обмен #%d
            
            💡 Вы можете оценить обмен позже через <b>📜 Историю обменов</b>
            """.formatted(deal.getId());
    }

    // ========================================
    // ПРОФИЛЬ
    // ========================================

    // ⭐ ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ
    public String formatUserProfile(User user, BigDecimal currentRate) {
        return String.format("""
                👤 %s
                @%s
                
                📊 <b>Статистика:</b>
                ⭐ Рейтинг: <b>%s/5.0</b>
                💼 Завершённых обменов: <b>%d</b>
                📱 Телефон: %s
                
                💹 <b>Текущий курс:</b>
                1 PLN = %s KZT
                """,
                user.getFullName(),
                user.getTelegramUsername(),
                formatRating(user.getTrustRating()),
                user.getSuccessfulDeals(),
                user.getIsPhoneVerified() ? "✅ Подтверждён" : "❌ Не подтверждён",
                formatAmount(currentRate));
    }

    // ========================================
    // УПРАВЛЕНИЕ ЗАЯВКАМИ
    // ========================================

    // ⭐ ВЫБОР ЗАЯВКИ ДЛЯ РЕДАКТИРОВАНИЯ
    public String formatSelectRequestToEdit(List<ExchangeRequest> requests) {
        StringBuilder sb = new StringBuilder();
        sb.append("✏️ <b>Выберите заявку для редактирования:</b>\n\n");

        for (int i = 0; i < requests.size(); i++) {
            ExchangeRequest req = requests.get(i);
            sb.append("<b>").append(i + 1).append(".</b> ");
            sb.append(formatAmount(req.getAmountNeed()))
                    .append(" ").append(req.getCurrencyNeed()).append(" - ");
            sb.append(getTransferMethodName(req.getTransferMethod().name())).append("\n");
        }

        return sb.toString();
    }

    // ⭐ ЗАЯВКА ОБНОВЛЕНА
    public String formatRequestUpdated(
            BigDecimal oldAmount,
            BigDecimal newAmount,
            String currency) {

        return String.format("""
                ✅ <b>Заявка обновлена</b>
                
                📋 <b>Изменения:</b>
                Было: %s %s
                Стало: <b>%s %s</b>
                
                💡 Заявка обновлена и снова активна!
                """,
                formatAmount(oldAmount),
                currency,
                formatAmount(newAmount),
                currency);
    }

    // ⭐ ВЫБОР ЗАЯВКИ ДЛЯ ОТМЕНЫ
    public String formatSelectRequestToCancel(List<ExchangeRequest> requests) {
        StringBuilder sb = new StringBuilder();
        sb.append("❌ <b>Выберите заявку для отмены:</b>\n\n");

        for (int i = 0; i < requests.size(); i++) {
            ExchangeRequest req = requests.get(i);
            sb.append("<b>").append(i + 1).append(".</b> ");
            sb.append(formatAmount(req.getAmountNeed()))
                    .append(" ").append(req.getCurrencyNeed()).append(" - ");
            sb.append(getTransferMethodName(req.getTransferMethod().name())).append("\n");
        }

        return sb.toString();
    }

    // ⭐ ЗАЯВКА ОТМЕНЕНА
    public String formatRequestCancelled(
            int index,
            String amount,
            String currency,
            String method,
            String notes) {

        StringBuilder sb = new StringBuilder();
        sb.append("✅ <b>Заявка #").append(index + 1).append(" отменена</b>\n\n");
        sb.append("📋 <b>Отменённая заявка:</b>\n");
        sb.append("💰 <b>").append(amount).append(" ").append(currency).append("</b>\n");
        sb.append("🔄 ").append(method).append("\n");
        if (notes != null && !notes.isEmpty()) {
            sb.append("📝 ").append(notes).append("\n");
        }
        sb.append("\n💡 Вы можете создать новую заявку в любое время!");

        return sb.toString();
    }

    // ========================================
    // ПОДТВЕРЖДЕНИЕ СУММЫ ОБМЕНА
    // ========================================

    // ⭐ ЗАПРОС СУММЫ ДЛЯ ПОДТВЕРЖДЕНИЯ СДЕЛКИ
    public String formatConfirmDealAmountRequest(ExchangeRequest request) {
        return String.format("""
                💰 <b>Подтверждение сделки</b>
                
                📋 Заявка: %s %s
                
                💡 <b>Введите сумму, которую вы обменяли:</b>
                (Максимум: %s %s)
                """,
                formatAmount(request.getAmountNeed()),
                request.getCurrencyNeed(),
                formatAmount(request.getAmountNeed()),
                request.getCurrencyNeed());
    }

    // ⭐ ПРЕДЛОЖЕНИЕ ОБМЕНА (БЕЗ АВТОЗАЯВКИ)
    public String formatExchangeOfferManual(
            ExchangeRequest targetRequest,
            User author) {

        return String.format("""
                💰 <b>Предложение обмена</b>
                
                📋 <b>Заявка:</b>
                💰 %s %s
                👤 Автор: @%s
                ⭐ Рейтинг: %s | Обменов: %d
                🔄 Метод: %s
                
                💡 <b>Введите сумму для обмена:</b>
                (Максимум: %s %s)
                """,
                formatAmount(targetRequest.getAmountNeed()),
                targetRequest.getCurrencyNeed(),
                author.getTelegramUsername(),
                formatRating(author.getTrustRating()),
                author.getSuccessfulDeals(),
                getTransferMethodName(targetRequest.getTransferMethod().name()),
                formatAmount(targetRequest.getAmountNeed()),
                targetRequest.getCurrencyNeed());
    }

    // ⭐ АВТОМАТИЧЕСКИЙ РАСЧЁТ ОБМЕНА (С АВТОЗАЯВКОЙ)
    public String formatExchangeOfferAutoCalculated(
            ExchangeRequest responderRequest,
            ExchangeRequest targetRequest,
            User author,
            BigDecimal calculatedAmount,
            BigDecimal maxAmount,
            BigDecimal proposedAmount) {

        StringBuilder sb = new StringBuilder();
        sb.append("💰 <b>Автоматический расчёт обмена</b>\n\n");
        sb.append("📋 <b>Ваша заявка:</b>\n");
        sb.append("   Вы хотите получить: <b>").append(formatAmount(responderRequest.getAmountNeed()))
                .append(" ").append(responderRequest.getCurrencyNeed()).append("</b>\n\n");
        sb.append("🎯 <b>Доступная заявка:</b>\n");
        sb.append("   Доступно для обмена: <b>").append(formatAmount(targetRequest.getAmountNeed()))
                .append(" ").append(targetRequest.getCurrencyNeed()).append("</b>\n");
        sb.append("   👤 Пользователь: @").append(author.getTelegramUsername()).append("\n\n");

        sb.append("💱 <b>Расчёт обмена:</b>\n");
        sb.append("   Чтобы получить <b>").append(formatAmount(responderRequest.getAmountNeed()))
                .append(" ").append(responderRequest.getCurrencyNeed()).append("</b>\n");
        sb.append("   Вам нужно отдать: <b>").append(formatAmount(calculatedAmount))
                .append(" ").append(targetRequest.getCurrencyNeed()).append("</b>\n\n");

        if (calculatedAmount.compareTo(maxAmount) > 0) {
            sb.append("   ⚠️ Доступно максимум: ").append(formatAmount(maxAmount))
                    .append(" ").append(targetRequest.getCurrencyNeed()).append("\n\n");
        }

        sb.append("   ✅ Предлагаемая сумма: <b>").append(formatAmount(proposedAmount))
                .append(" ").append(targetRequest.getCurrencyNeed()).append("</b>\n\n");

        sb.append("💡 Подтвердите, чтобы отправить предложение");

        return sb.toString();
    }

    // ========================================
    // ИСТОРИЯ ОБМЕНОВ
    // ========================================

    // ⭐ НЕТ ИСТОРИИ ОБМЕНОВ
    public String formatNoHistory() {
        return """
                📜 <b>История обменов</b>
                
                У вас пока нет завершённых обменов
                
                💡 Создайте заявку или откликнитесь на существующую!
                """;
    }

    // ⭐ СПИСОК ИСТОРИИ ОБМЕНОВ
    public String formatHistoryList(
            Page<Deal> dealsPage,
            int page,
            User currentUser) {

        StringBuilder sb = new StringBuilder();
        sb.append("📜 <b>История обменов</b>\n");

        if (dealsPage.getTotalPages() > 1) {
            sb.append("Страница ").append(page + 1).append(" из ").append(dealsPage.getTotalPages()).append("\n");
        }

        sb.append("Всего обменов: ").append(dealsPage.getTotalElements()).append("\n\n");

        List<Deal> deals = dealsPage.getContent();
        int offset = page * dealsPage.getSize();

        for (int i = 0; i < deals.size(); i++) {
            Deal deal = deals.get(i);
            int globalIndex = offset + i + 1;

            boolean isRequester = deal.getRequester().getId().equals(currentUser.getId());
            User counterparty = isRequester ? deal.getProvider() : deal.getRequester();

            sb.append("<b>").append(globalIndex).append(".</b> 🆔 Обмен #").append(deal.getId()).append("\n");
            sb.append("   📅 ").append(deal.getFinishedAt().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
            sb.append("   💰 ").append(formatAmount(deal.getAmount()))
                    .append(" ").append(deal.getCurrency()).append("\n");
            sb.append("   👤 С: @").append(counterparty.getTelegramUsername()).append("\n");

            // ⭐ УПРОСТИЛ: просто показываем "Можно оценить" для всех
            // (логику проверки рейтинга можно добавить в TelegramBotService)
            sb.append("   💬 Можно оценить\n");
            sb.append("\n");
        }

        sb.append("💡 Нажмите на обмен для подробностей");

        return sb.toString();
    }

    public String formatMatchingOffers(
            List<ExchangeRequest> matches,
            String theyWantWithFlag,
            String whatTheyHaveWithFlag,
            ExchangeRequest.Currency myNeed,
            BigDecimal rate) {

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 <b>Подходящие предложения (").append(matches.size()).append("):</b>\n\n");
        sb.append("Пользователи, которым нужен <b>").append(theyWantWithFlag).append("</b>\n");
        sb.append("(у них есть <b>").append(whatTheyHaveWithFlag).append("</b>):\n\n");

        for (int i = 0; i < matches.size(); i++) {
            ExchangeRequest match = matches.get(i);

            // ⭐ КОНВЕРТИРУЕМ ИХ СУММУ В МОЮ ВАЛЮТУ
            BigDecimal theirAmount = match.getAmountNeed();
            BigDecimal convertedAmount = theirAmount.multiply(rate);

            sb.append("<b>").append(i + 1).append(".</b> 👤 @").append(match.getUser().getTelegramUsername()).append("\n");
            sb.append("   💰 <b>").append(formatAmount(match.getAmountNeed()))
                    .append(" ").append(match.getCurrencyNeed()).append("</b>\n");
            sb.append("   💱 ≈ <b>").append(formatAmount(convertedAmount)).append(" (вы получите)")
                    .append(" ").append(myNeed).append("</b>\n");
            sb.append("   🔄 ").append(getTransferMethodName(match.getTransferMethod().name())).append("\n");
            sb.append("   📝 ").append(match.getNotes() != null ? match.getNotes() : "—").append("\n");
            sb.append("   ⭐ Рейтинг: ").append(formatRating(match.getUser().getTrustRating()))
                    .append(" | Обменов: ").append(match.getUser().getSuccessfulDeals()).append("\n\n");
        }

        sb.append("💡 Нажмите кнопку с номером для отклика");

        return sb.toString();
    }


    // ⭐ ДЕТАЛИ ОБМЕНА ИЗ ИСТОРИИ
    public String formatDealDetails(
            Deal deal,
            User currentUser,
            User counterparty,
            BigDecimal received,
            ExchangeRequest.Currency oppositeCurrency,
            boolean isRated) {

        boolean isRequester = deal.getRequester().getId().equals(currentUser.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("📋 <b>Идентификатор обмена #").append(deal.getId()).append("</b>\n\n");

        sb.append("📅 <b>Дата:</b> ").append(deal.getFinishedAt().format(
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
        sb.append("👤 <b>Встречная сторона:</b> @").append(counterparty.getTelegramUsername()).append("\n");
        sb.append("⭐ Рейтинг: ").append(formatRating(counterparty.getTrustRating()))
                .append(" | Обменов: ").append(counterparty.getSuccessfulDeals()).append("\n\n");

        sb.append("💰 <b>Обмен:</b>\n");
        if (isRequester) {
            sb.append("   📤 Вы отдали: ").append(formatAmount(received))
                    .append(" ").append(oppositeCurrency).append("\n");
            sb.append("   📥 Вы получили: ").append(formatAmount(deal.getAmount()))
                    .append(" ").append(deal.getCurrency()).append("\n");
        } else {
            sb.append("   📤 Вы отдали: ").append(formatAmount(deal.getAmount()))
                    .append(" ").append(deal.getCurrency()).append("\n");
            sb.append("   📥 Вы получили: ").append(formatAmount(received))
                    .append(" ").append(oppositeCurrency).append("\n");
        }
        BigDecimal plnToKztRate = getPLNtoKZTRate(deal);
        sb.append("   💱 1 PLN = ").append(formatRate(plnToKztRate)).append(" KZT");
        sb.append("\n🔄 <b>Метод:</b> ").append(getTransferMethodName(
                deal.getTransferMethod().name())).append("\n");
        sb.append("📊 <b>Статус:</b> ЗАВЕРШЁН\n");

        if (!isRated) {
            sb.append("\n💡 Вы можете оценить этот обмен");
        } else {
            sb.append("\n✅ Вы оценили этот обмен");
        }

        return sb.toString();
    }

    // ========================================
    // СТАТУС (/status)
    // ========================================

    /**
     * Форматирование статуса пользователя - ТОЛЬКО ЗАЯВКИ
     */
    public String formatUserStatus(User user, List<ExchangeRequest> activeRequests) {
        StringBuilder sb = new StringBuilder();

        if (activeRequests.isEmpty()) {
            sb.append("📋 <b>У вас нет активных заявок</b>\n\n");
            sb.append("💡 Создайте заявку, чтобы начать обмен валюты!");
        } else {
            sb.append("📋 <b>Ваши активные заявки (").append(activeRequests.size()).append("):</b>\n\n");

            for (int i = 0; i < activeRequests.size(); i++) {
                ExchangeRequest req = activeRequests.get(i);
                sb.append("<b>").append(i + 1).append(".</b> 💰 <b>")
                        .append(formatAmount(req.getAmountNeed()))
                        .append(" ").append(req.getCurrencyNeed()).append("</b>\n");
                sb.append("   🔄 ").append(getTransferMethodName(req.getTransferMethod().name())).append("\n");
                sb.append("   📝 ").append(req.getNotes() != null ? req.getNotes() : "—").append("\n\n");
            }

            sb.append("💡 Используйте кнопки ниже для управления заявками");
        }

        return sb.toString();
    }

    // ========================================
    // ДРУГОЕ
    // ========================================

    public String formatUnknownCommand() {
        return "❌ Неизвестная команда. Используйте /menu";
    }

    // ========================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ========================================

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Форматирование суммы с разделителем тысяч (пробел) и точкой для дробной части
     */
    public String formatAmount(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(' ');  // Пробел для тысяч
        symbols.setDecimalSeparator('.');   // Точка для дробной части

        DecimalFormat formatter = new DecimalFormat("#,##0.##", symbols);
        return formatter.format(amount);
    }

    public String getTransferMethodName(String method) {
        return switch(method) {
            case "BANK_TRANSFER" -> "Банковский перевод";
            case "CASH" -> "Наличные";
            default -> method;
        };
    }

    public String formatRating(BigDecimal rating) {
        if (rating == null) {
            return "0,0";
        }
        DecimalFormat df = new DecimalFormat("0.0");
        return df.format(rating).replace(".", ",");
    }

    public String formatStaleDataError() {
        return """
            ⚠️ <b>Данные устарели</b>
            
            Попросите пользователя отправить предложение заново.
            """;
    }
}