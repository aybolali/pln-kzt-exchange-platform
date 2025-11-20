# PLN-KZT Exchange Bot 💱

A peer-to-peer currency exchange matching platform connecting users who need to exchange Polish Złoty (PLN) and Kazakhstani Tenge (KZT). Built with Spring Boot and PostgreSQL, deployed in production.

## 🎯 Problem Statement

Kazakhstani people in Poland and Polish people in Kazakhstan face high bank fees and poor exchange rates when transferring money between countries. This platform connects them directly - someone in Poland who needs KZT exchanges with someone in Kazakhstan who needs PLN, eliminating intermediaries and getting market rates.

## ✨ Key Features

- **Smart Matching Algorithm**: Automatically pairs users with complementary exchange needs based on currency pair (PLN↔KZT), amount compatibility (±10% tolerance), user rating scores, and request freshness
- **Conversation State Management**: Multi-step interactive flow through Telegram bot with session tracking for seamless user experience
- **Rating System**: User trust scores based on completed transactions, affecting future matching priority
- **Real-Time Exchange Rates**: Dual API integration (National Bank of Kazakhstan primary + fallback) with 60-minute cache
- **Automated Cleanup**: Scheduled tasks removing stale data (3 days for active requests, 7 days for completed/cancelled)
- **Rate Limiting**: In-memory protection against spam (20 commands/min for Telegram, 6 API calls/min, 25 default actions/min)
- **Security**: Input validation, SQL injection prevention via JPA, Telegram-based authentication (no passwords)

## 🏗️ Architecture

```
├── Presentation Layer
│   ├── Telegram Bot Interface
│   │   ├── PLNKZTExchangeBot
│   │   ├── TelegramBotService
│   │   ├── TelegramKeyboardBuilder
│   │   ├── TelegramMessageFormatter
│   │   └── ConversationStateService
│   └── REST API Controllers
│       ├── UserController
│       ├── ExchangeRequestController
│       ├── DealController
│       ├── MatchingController
│       └── RatingController
│
├── Service Layer
│   ├── Core Business Services
│   │   ├── UserService
│   │   ├── ExchangeRequestService
│   │   ├── DealService
│   │   ├── MatchingService
│   │   └── RatingService
│   └── Infrastructure Services
│       ├── ExchangeRateService
│       ├── ExchangeRequestCleanupService
│       └── SimpleRateLimitService
│
├── Data Access Layer
│   ├── Repositories (Spring Data JPA)
│   │   ├── UserRepository
│   │   ├── ExchangeRequestRepository
│   │   ├── DealRepository
│   │   └── RatingRepository
│   └── Domain Entities
│       ├── User
│       ├── ExchangeRequest
│       ├── Deal
│       └── Rating
│
├── External Integrations
│   ├── Telegram Bot API
│   ├── National Bank of Kazakhstan API
│   └── Currency API Fallback
│
└── Database Layer
    └── PostgreSQL
        ├── users
        ├── exchange_requests
        ├── deals
        └── ratings
```
## 🛠️ Tech Stack

- **Backend**: Java 17, Spring Boot 3.x
- **Database**: PostgreSQL (production), H2 (testing)
- **ORM**: Hibernate, Spring Data JPA
- **Migration**: Flyway
- **Integration**: Telegram Bot API
- **External APIs**: National Bank of Kazakhstan, Currency API fallback
- **Testing**: JUnit 5, Mockito
- **Containerization**: Docker, Docker Compose
- **Build Tool**: Maven

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 12+
- Docker & Docker Compose (optional)
- Telegram Bot Token (from [@BotFather](https://t.me/botfather))

### Local Setup

1. **Clone the repository**
```bash
git clone https://github.com/aybolali/pln-kzt-exchange-bot.git
cd pln-kzt-exchange-bot
```

2. **Configure environment variables**
```bash
cp .env.example .env
# Edit .env with your actual values
```

3. **Run with Docker Compose** (Recommended)
```bash
docker-compose up -d
```

4. **Or run locally**
```bash
# Start PostgreSQL
# Configure environment variables
mvn clean install
mvn spring-boot:run
```

## 📊 How It Works

### Matching Algorithm
1. User creates exchange request via Telegram bot
2. System finds compatible requests based on:
   - Currency pair (PLN→KZT or KZT→PLN)
   - Amount tolerance (±10%)
   - User rating scores (higher ratings prioritized)
   - Request freshness (newer first)
3. Matched users connect through Telegram
4. After physical exchange, both users rate each other

### Conversation Flow
- Multi-step interactive dialogs
- Session state management per user
- Contextual keyboards for easy navigation
- Input validation at each step

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

## 📈 Performance

- Cleanup scheduled tasks run weekly (configurable)
- Active requests auto-cancelled after 3 days
- Completed/cancelled requests deleted after 7 days
- Exchange rate cache: 60 minutes TTL

## 🔧 Configuration

Key settings in `application.properties`:
- Cleanup schedules and retention periods
- Exchange rate API configuration
- Logging levels
- Cron expressions for scheduled tasks

## 🐳 Docker Deployment

The application is containerized and production-ready:
```bash
# Build and run
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop
docker-compose down
```


## 🤝 Contributing

This is a personal portfolio project, but feedback and suggestions are welcome!

## 👤 Author

**@aybolali**
