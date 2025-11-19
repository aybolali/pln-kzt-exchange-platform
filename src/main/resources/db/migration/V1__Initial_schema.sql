-- =====================================================
-- PLN-KZT Exchange Bot - Initial Database Schema
-- =====================================================

-- Создание таблицы пользователей
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       telegram_user_id BIGINT UNIQUE NOT NULL,           -- ✅ ДОБАВЛЕНО: ID пользователя в Telegram
                       telegram_username VARCHAR(32) UNIQUE NOT NULL,     -- Username в Telegram (@username)
                       first_name VARCHAR(64),                            -- Имя пользователя
                       last_name VARCHAR(64),                             -- Фамилия пользователя
                       phone VARCHAR(20),                                 -- Номер телефона
                       trust_rating DECIMAL(3,2) DEFAULT 0.00,           -- Рейтинг доверия (0.00 - 5.00)
                       successful_deals INTEGER DEFAULT 0,                -- Количество успешных сделок
                       is_phone_verified BOOLEAN DEFAULT FALSE,           -- Верифицирован ли телефон
                       is_enabled BOOLEAN DEFAULT TRUE,                   -- Активен ли аккаунт
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для быстрого поиска пользователей
CREATE INDEX idx_users_telegram_user_id ON users(telegram_user_id);
CREATE INDEX idx_users_telegram_username ON users(telegram_username);
CREATE INDEX idx_users_trust_rating ON users(trust_rating DESC);

-- =====================================================

-- Создание таблицы запросов обмена
CREATE TABLE exchange_requests (
                                   id BIGSERIAL PRIMARY KEY,
                                   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                   currency_need VARCHAR(3) NOT NULL CHECK (currency_need IN ('PLN', 'KZT')),
                                   amount_need DECIMAL(12,2) NOT NULL CHECK (amount_need > 0),
                                   status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
                                   notes VARCHAR(500),
                                   transfer_method VARCHAR(20) NOT NULL CHECK (transfer_method IN ('CASH', 'BANK_TRANSFER')),
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   finished_at TIMESTAMP
);

-- Индексы для запросов обмена
CREATE INDEX idx_exchange_requests_user_id ON exchange_requests(user_id);
CREATE INDEX idx_exchange_requests_status ON exchange_requests(status);
CREATE INDEX idx_exchange_requests_currency_status ON exchange_requests(currency_need, status);
CREATE INDEX idx_exchange_requests_created_at ON exchange_requests(created_at DESC);

-- =====================================================

-- Создание таблицы сделок
CREATE TABLE deals (
                       id BIGSERIAL PRIMARY KEY,
                       request_id BIGINT,                                           -- Связь с запросом (nullable для независимости)
                       requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- Кто создал запрос
                       provider_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- Кто откликнулся (встречная сторона)
                       amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),           -- ✅ УВЕЛИЧЕНО: было 12,2 → стало 15,2
                       currency VARCHAR(3) NOT NULL CHECK (currency IN ('PLN', 'KZT')),
                       exchange_rate DECIMAL(12,8) NOT NULL CHECK (exchange_rate > 0),
                       transfer_method VARCHAR(20) NOT NULL CHECK (transfer_method IN ('CASH', 'BANK_TRANSFER')),
                       status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN (      -- ✅ УВЕЛИЧЕНО: было 20 → 50
                                                                                       'PENDING',                  -- Ожидает подтверждения
                                                                                       'REQUESTER_CONFIRMED',      -- Инициатор подтвердил
                                                                                       'PROVIDER_CONFIRMED',       -- Встречная сторона подтвердила
                                                                                       'COMPLETED',                -- Завершена успешно
                                                                                       'CANCELLED'                 -- Отменена
                           )),
                       requester_confirmed BOOLEAN DEFAULT FALSE,                   -- ✅ ДОБАВЛЕНО: подтверждение инициатора
                       provider_confirmed BOOLEAN DEFAULT FALSE,                    -- ✅ ДОБАВЛЕНО: подтверждение встречной стороны
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       finished_at TIMESTAMP
);

-- Индексы для сделок
CREATE INDEX idx_deals_request_id ON deals(request_id);
CREATE INDEX idx_deals_requester_id ON deals(requester_id);
CREATE INDEX idx_deals_provider_id ON deals(provider_id);
CREATE INDEX idx_deals_status ON deals(status);
CREATE INDEX idx_deals_created_at ON deals(created_at DESC);

-- =====================================================

-- Создание таблицы рейтингов
CREATE TABLE ratings (
                         id BIGSERIAL PRIMARY KEY,
                         deal_id BIGINT NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
                         rater_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,        -- Кто ставит оценку
                         rated_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- Кому ставят оценку
                         rating DECIMAL(3,2) NOT NULL CHECK (rating >= 1.0 AND rating <= 5.0),  -- ✅ ИСПРАВЛЕНО: 3,2 для точности
                         comment VARCHAR(500),                                                   -- ✅ ДОБАВЛЕНО: комментарий к оценке
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Один пользователь может оценить одну сделку только один раз
                         CONSTRAINT unique_rating_per_deal UNIQUE(deal_id, rater_id)
);

-- Индексы для рейтингов
CREATE INDEX idx_ratings_deal_id ON ratings(deal_id);
CREATE INDEX idx_ratings_rated_user_id ON ratings(rated_user_id);
CREATE INDEX idx_ratings_rater_id ON ratings(rater_id);

-- =====================================================
-- КОММЕНТАРИИ К ТАБЛИЦАМ
-- =====================================================

COMMENT ON TABLE users IS 'Пользователи платформы обмена валют';
COMMENT ON TABLE exchange_requests IS 'Запросы на обмен валюты';
COMMENT ON TABLE deals IS 'Сделки между пользователями';
COMMENT ON TABLE ratings IS 'Рейтинги пользователей после завершения сделок';

COMMENT ON COLUMN users.telegram_user_id IS 'Уникальный ID пользователя в Telegram';
COMMENT ON COLUMN users.trust_rating IS 'Средний рейтинг доверия (от 0.00 до 5.00)';
COMMENT ON COLUMN deals.requester_confirmed IS 'Подтвердил ли инициатор получение средств';
COMMENT ON COLUMN deals.provider_confirmed IS 'Подтвердила ли встречная сторона получение средств';