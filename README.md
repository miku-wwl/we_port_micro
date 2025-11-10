# we_ports_micro: Trading Valuation System

> 🌐 Chinese Version: [README_cn.md](README_cn.md)

> 📑 Financial background knowledge  : [README_FIN.md](README_FIN.md)

## Project Overview
`we_ports_micro` is a real-time trading valuation system built on the Java ecosystem, focusing on dynamic valuation calculation and push of investment portfolios. The system simulates market data fluctuations (stock prices), calculates the Net Asset Value (NAV) of portfolios in real time by integrating financial engineering models (such as the Black-Scholes option pricing model), and realizes efficient transmission and display of valuation results through gRPC.

Core features include:
- Real-time market data simulation (supports Geometric Brownian Motion (GBM) model and random pricing model)
- Position valuation calculation for stocks and options
- Stream-based push and reception of valuation results via gRPC
- Real-time display and update tracking of portfolio net asset value

## System Architecture
The system adopts a microservice architecture, consisting of two core modules:

### 1. Portfolio Module (Valuation Calculation & Sender)
- Market Data Generation: Simulates stock price fluctuations (for underlying assets like AAPL, TELSA)
- Valuation Calculation: Computes stock market value and theoretical option value based on real-time market data (using the Black-Scholes model)
- gRPC Client: Packages valuation results into Protobuf messages and sends them

### 2. Portfolio-Receiver Module (Valuation Reception & Display)
- gRPC Server: Listens for and receives valuation result messages
- Data Processing: Parses gRPC messages and converts them into local entities
- Result Display: Formats and prints detailed portfolio valuation information (including position details, total NAV, and price changes)

## Tech Stack
- Core Frameworks: Spring Boot, Spring WebFlux, R2DBC, Project Reactor
- Communication Protocol: gRPC (based on Protobuf)
- Financial Calculation: Black-Scholes option pricing model, GBM market data simulation
- Data Storage: H2 in-memory database (for storing static security information)
- Build Tool: Gradle

## Quick Start - docker
- Docker version 27.5.1
- Docker Compose version v2.32.4
> 📑 Docker  : [README_docker.md](README_docker.md)


## Quick Start

### Prerequisites
- JDK 8
- Gradle 8.5

### Startup Steps

1. **Launch the portfolio-receiver module (valuation receiver)**
   ```bash
   # Navigate to the module directory
   cd portfolio-receiver
   # Build and start the service
   ./gradlew bootRun
   ```
   The service will start the gRPC server (default port: `50052`) and listen for valuation messages from the sender.

2.  **Launch the portfolio module (valuation sender)**
   ```bash
   # Navigate to the module directory
   cd portfolio
   # Build and start the service
   ./gradlew bootRun
   ```
   The service will start on the default port, and the gRPC client will connect to the configured receiver address (default: `localhost:50052`).

3.  **Sample Output**
   ![alt text](output_example.png)

## Core Feature Explanation

### 1. Market Data Simulation
- **Pricing Strategies**: Two market data generation strategies are supported (switch via the configuration `portfolio.market-data.pricing-strategy`):
  - `GBM`: Geometric Brownian Motion model (default), which simulates prices based on the underlying asset's expected return (μ) and volatility (σ)
  - `Random`: Random pricing model, which generates random prices within a specified range

- **Configuration Parameters** (`application.properties`):
  ```properties
  # Initial prices of underlying assets
  portfolio.marketdata.initial-price.AAPL=110.0
  portfolio.marketdata.initial-price.TELSA=450.0
  
  # GBM model parameters (expected return μ and volatility σ)
  portfolio.marketdata.mu.AAPL=0.08
  portfolio.marketdata.sigma.AAPL=0.2
  portfolio.marketdata.mu.TELSA=0.12
  portfolio.marketdata.sigma.TELSA=0.3
  
  # Price update interval (in milliseconds)
  portfolio.market-data.min-interval=500
  portfolio.market-data.max-interval=2000
  ```

### 2. Valuation Calculation
- **Stock Valuation**: The market value is directly calculated based on the real-time price from market data (Price × Quantity)
- **Option Valuation**: The theoretical price is computed using the Black-Scholes model, with the formulas as follows:
  - Call Option: `C = S*N(d1) - K*e^(-rT)*N(d2)`
  - Put Option: `P = K*e^(-rT)*N(-d2) - S*N(-d1)`
    Where:
    `d1 = (ln(S/K) + (r+σ²/2)T) / (σ√T)`
    `d2 = d1 - σ√T`

### 3. gRPC Communication
- **Protocol Definition** (`portfolio_valuation.proto`):
  - Message Types: Include position information (`PositionProto`), market data changes (`MarketDataChangeProto`), and valuation results (`PortfolioValuationMessage`)
  - Service Interfaces: Support one-time transmission (`SendValuation`) and stream-based transmission (`StreamValuations`)

- **Data Flow**:
  1. The `portfolio` module generates valuation results and converts them into Protobuf messages
  2. Sends the messages to `portfolio-receiver` via the gRPC client
  3. The receiver parses the messages and displays them in a formatted way

## Configuration Instructions
The core configuration file is `src/main/resources/application.properties`. Key configuration items are listed below:

| Configuration Item | Description | Default Value |
|--------------------|-------------|---------------|
| `grpc.server.port` | gRPC server port | 50052 |
| `grpc.valuation.server.address` | Valuation receiver address | localhost:50052 |
| `portfolio.option.risk-free-rate` | Risk-free rate (for option pricing) | 0.02 |
| `portfolio.position.csv-path` | File path of the position data CSV file | classpath:positions.csv |
| `spring.r2dbc.url` | Database connection address (H2 in-memory database) | r2dbc:h2:mem:///webfluxdb |

## Sample Output
After successfully receiving the valuation results, the receiver will print information similar to the following:
```
# 1 Market Data Update
AAPL change to 112.50
TELSA change to 448.20

[Valuation Time]: 2023-10-01 15:30:45.123
[Total Portfolio Net Asset Value (NAV)]: 156,789.50 USD
# Portfolio
symbol                   price       qty         value
AAPL                     112.50        100    11,250.00 USD
TELSA                    448.20         50    22,410.00 USD
AAPL-MAY-2026-110-C       15.75         20     3,150.00 USD
# Total portfolio
156789.50 USD
======================================================
```

## Extension Guide
1.  **Add a New Pricing Strategy**: Implement the `PricingStrategy` interface and register it in `ReactiveMarketDataProvider`
2.  **Support New Security Types**: Extend the `SecurityType` enumeration and add corresponding processing logic in the valuation calculation
3.  **Add New Spot and Option Products**: Extend `schema.sql` to add new products, and supplement user position information in positions.csv
4.  **Add New Stock Symbols**: Extend the configuration `portfolio.market-data.stock-tickers=AAPL, TELSA`
5.  **Modify the gRPC Protocol**: Regenerate Java code after updating `portfolio_valuation.proto`
6.  **Modify H2 Storage Mode**: Refer to the commented section in application.properties

## Notes
- The system uses an H2 in-memory database. Static security data will be reset after the service restarts (initialization data is available in `schema.sql`)
- Market data simulation is for demonstration purposes only. A real market data source should be connected in a production environment
- The volatility (σ) in the option pricing model needs to be calibrated according to actual scenarios
```