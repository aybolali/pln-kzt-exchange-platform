# PLN-KZT Exchange Bot 💱

A peer-to-peer currency exchange platform connecting users who need to exchange Polish Złoty (PLN) and Kazakhstani Tenge (KZT). Built with Spring Boot and PostgreSQL, deployed in production serving real users.

## 🎯 Problem Statement

Polish students in Kazakhstan and Kazakhstani residents in Poland face high fees and poor exchange rates when transferring money between countries. This platform connects people with complementary currency needs, enabling direct peer-to-peer exchanges at better rates.

## ✨ Key Features

- **Smart Matching Algorithm**: Automatically pairs users with complementary exchange needs
- **Two-Step Confirmation**: Secure deal confirmation process to ensure trust
- **Rating System**: User trust scores based on completed transactions
- **Real-Time Exchange Rates**: Integration with National Bank of Kazakhstan API
- **Automated Cleanup**: Scheduled cleanup of inactive and completed requests
- **Rate Limiting**: Protection against spam and abuse
- **Telegram Integration**: Full bot interface for seamless user experience

## 🏗️ Architecture

```
├── Domain Layer (Entities)
│   ├── User
│   ├── ExchangeRequest
│   └── Deal
├── Service Layer
│   ├── TelegramBotService
│   ├── MatchingService
│   ├── DealService
│   └── ExchangeRateService
├── Repository Layer (Spring Data JPA)
├── Telegram Bot Interface
└── Database (PostgreSQL)
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

### Environment Variables

See `.env.example` for required configuration:
- `DATABASE_URL`: PostgreSQL connection string
- `DATABASE_USERNAME`: Database user
- `DATABASE_PASSWORD`: Database password
- `TELEGRAM_BOT_TOKEN`: Your Telegram bot token
- `TELEGRAM_BOT_USERNAME`: Your bot username

## 📊 Features in Detail

### Matching Algorithm
The system implements intelligent matching based on:
- Currency pair (PLN→KZT or KZT→PLN)
- Amount compatibility (±10% tolerance)
- Geographic location (city-based)
- Request freshness (newer requests prioritized)

### Security & Rate Limiting
- In-memory rate limiting (100 requests/minute per user)
- Input validation and sanitization
- SQL injection prevention via JPA
- Scheduled cleanup of stale data

### Deal Processing
1. User creates exchange request
2. System finds matching requests
3. Users confirm deal (two-step process)
4. After physical exchange, users rate each other
5. Ratings affect future matching priority

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

## 📝 Database Schema

- **users**: User profiles and ratings
- **exchange_requests**: Active and historical requests
- **deals**: Completed transaction records
- **flyway_schema_history**: Migration tracking


## 🤝 Contributing

This is a personal portfolio project, but feedback and suggestions are welcome!

## 👤 Author

**Aibolali**
- Building full-stack applications with Spring Boot
- Interested in fintech and p2p solutions
- Open to opportunities in software engineering

## 🙏 Acknowledgments

- Telegram Bot API for seamless integration
- National Bank of Kazakhstan for exchange rate data
- Spring Boot community for excellent documentation

---

*Built with ❤️ to solve a real problem for Polish-Kazakh community*