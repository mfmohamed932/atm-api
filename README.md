# ATM Machine REST API

A Spring Boot REST API application that simulates ATM (Automated Teller Machine) operations including customer authentication, balance inquiry, cash withdrawal, and cash deposit with daily withdrawal limits.

## 📋 Table of Contents
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Sample Test Data](#sample-test-data)
- [Testing](#testing)
- [Design Decisions](#design-decisions)
- [Future Enhancements](#future-enhancements)

## ✨ Features

### Part I - Core ATM Operations
- ✅ **Customer Authentication**: Secure PIN-based authentication
- ✅ **Balance Inquiry**: View current account balance
- ✅ **Cash Withdrawal**: Simulate cash withdrawal with validation
- ✅ **Cash Deposit**: Simulate cash deposit
- ✅ **Daily Withdrawal Limit**: Track and enforce daily withdrawal limits

### Part II - REST API & Documentation
- ✅ **REST API**: RESTful endpoints for all operations
- ✅ **Swagger/OpenAPI**: Complete API specification and interactive documentation
- ✅ **Persistent Storage**: H2 in-memory database with JPA/Hibernate
- ✅ **Exception Handling**: Comprehensive error handling with meaningful responses
- ✅ **Transaction History**: Track all account transactions
- ✅ **Automated Tests**: Unit and integration tests

## 🛠 Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.0
- **Spring Data JPA**: Database access and ORM
- **H2 Database**: In-memory database for development
- **Lombok**: Reduce boilerplate code
- **SpringDoc OpenAPI**: API documentation (Swagger UI)
- **JUnit 5 & Mockito**: Testing framework
- **Gradle**: Build tool

## 📦 Prerequisites

- Java 21 or higher
- Gradle (included via wrapper)
- IDE (IntelliJ IDEA, Eclipse, or VS Code)

## 🚀 Getting Started

### 1. Clone and Navigate to Project

### 2. Build the Project
```bash
./gradlew clean build
```

### 3. Run the Application

The application will start on `http://localhost:8080`

### 4. Access the Application

#### Swagger UI (Interactive API Documentation)
Open your browser and navigate to:
```
http://localhost:8080/swagger-ui.html
```

#### H2 Database Console
```
http://localhost:8080/h2-console
```
- **JDBC URL**: `jdbc:h2:mem:atmdb`
- **Username**: `sa`
- **Password**: (leave empty)

#### API Endpoints Base URL
```
http://localhost:8080/api/atm
```

## 📚 API Documentation

### Authentication
**POST** `/api/atm/authenticate`
```json
{
  "accountNumber": "1001",
  "pin": "1234"
}
```

### Get Balance
**GET** `/api/atm/balance/{accountId}`

### Withdraw Cash
**POST** `/api/atm/withdraw`
```json
{
  "accountId": 1,
  "amount": 100.00,
  "pin": "1234"
}
```

### Deposit Cash
**POST** `/api/atm/deposit`
```json
{
  "accountId": 1,
  "amount": 500.00,
  "pin": "1234"
}
```

### Get Transaction History
**GET** `/api/atm/transactions/{accountId}`

### Health Check
**GET** `/api/atm/health`

## 🔐 Sample Test Data

The application initializes with the following test accounts:

| Account Number | Customer Name    | PIN  | Balance    | Daily Limit |
|---------------|------------------|------|------------|-------------|
| 1001          | John Doe         | 1234 | $5,000.00  | $1,000.00   |
| 1002          | Jane Smith       | 5678 | $10,000.00 | $2,000.00   |
| 1003          | Bob Johnson      | 9012 | $2,500.00  | $500.00     |
| 1004          | Alice Williams   | 3456 | $15,000.00 | $3,000.00   |

## 🧪 Testing

### Run All Tests
```bash
./gradlew test
```

### View Test Reports
After running tests, open:
```
build/reports/tests/test/index.html
```

### Test Coverage
- **Unit Tests**: Service layer business logic
- **Integration Tests**: Controller endpoints with database
- **Test Scenarios**:
  - Successful authentication
  - Invalid PIN handling
  - Insufficient funds
  - Daily limit enforcement
  - Successful withdrawals and deposits
  - Transaction history

## 🏗 Design Decisions

### Architecture
- **Layered Architecture**: Separation of concerns (Controller → Service → Repository)
- **RESTful Design**: Standard HTTP methods and status codes
- **DTO Pattern**: Separate request/response objects from entities
- **Exception Handling**: Centralized error handling with custom exceptions

### Security Considerations
- PIN validation on every transaction
- Account status checking (active/inactive)
- Input validation using Jakarta Bean Validation

### Business Logic
- **Daily Limit Reset**: Automatically resets at midnight
- **Transaction Tracking**: All operations are logged
- **Atomic Operations**: Transactional consistency for withdrawals/deposits
- **Decimal Precision**: BigDecimal for accurate monetary calculations

### Database Design
- **Entities**: Account, Transaction
- **Relationships**: One Account to Many Transactions
- **Constraints**: Unique account numbers, non-null validations

## 🚀 Future Enhancements

### Short-term Improvements
1. **Security**
   - JWT token-based authentication
   - Password encryption (BCrypt)
   - Rate limiting for failed authentication attempts
   - Account lockout after multiple failed attempts

2. **Features**
   - Transfer between accounts
   - Mini statement (last N transactions)
   - PIN change functionality
   - Multiple account support per customer
   - Check balance without full authentication

3. **Validation**
   - Amount denomination validation (multiples of 20)
   - Maximum single transaction limit
   - Minimum balance requirement
   - Business hours validation

### Long-term Enhancements
1. **Production Readiness**
   - PostgreSQL/MySQL database integration
   - Redis caching for improved performance
   - Actuator endpoints for monitoring
   - Logging with ELK stack
   - Containerization with Docker
   - CI/CD pipeline setup

2. **Advanced Features**
   - Card/Account verification system
   - Receipt generation (PDF)
   - Email/SMS notifications
   - Multi-currency support
   - Fraud detection algorithms
   - Real-time analytics dashboard

3. **Scalability**
   - Microservices architecture
   - Event-driven architecture (Kafka)
   - Load balancing
   - Database sharding
   - API Gateway

4. **Compliance & Audit**
   - Audit logs for all operations
   - Regulatory compliance (PCI DSS)
   - Data encryption at rest and in transit
   - GDPR compliance

## 📝 Testing Scenarios for Real-World Deployment

### Functional Testing
- ✅ Valid and invalid authentication
- ✅ Boundary value testing for amounts
- ✅ Concurrent transaction handling
- ✅ Daily limit across midnight boundary
- ✅ Multiple simultaneous withdrawals

### Non-Functional Testing
- **Performance**: Response time under load (target: <200ms)
- **Load Testing**: 1000+ concurrent users
- **Stress Testing**: System behavior under extreme conditions
- **Security**: Penetration testing, SQL injection, XSS
- **Reliability**: 99.9% uptime SLA
- **Data Integrity**: Transaction rollback scenarios

### Edge Cases
- Exact balance withdrawal
- Withdrawal exactly at daily limit
- Multiple rapid transactions
- System recovery after failure
- Network timeout handling

## 📄 License
This project is for educational/interview purposes.

## 👤 Author
HEB Interview Project - November 2025

