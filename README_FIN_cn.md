# we_ports_micro 金融逻辑说明

本文档聚焦项目核心金融模型与计算逻辑，详细说明股票价格模拟、期权定价及组合估值的数学原理与实现细节。

## 一、核心金融模型概述

项目采用两大核心金融模型实现实时估值：
- **几何布朗运动（GBM）**：模拟股票价格的随机波动
- **Black-Scholes模型**：计算期权的理论价格

通过上述模型，系统定期（默认500ms）生成市场数据快照，计算持仓市值并汇总组合总净资产（NAV）。


## 二、股票价格模拟：几何布朗运动（GBM）

股票价格波动采用几何布朗运动模型模拟，其核心思想是：股票价格的收益率服从正态分布，价格变化与当前价格成正比。

## 数学公式
### 价格变动率公式
股票价格变动率的计算公式如下：
$$\frac{\Delta S}{S} = \mu \cdot \frac{\Delta t}{T_{year}} + \sigma \cdot \epsilon \cdot \sqrt{\frac{\Delta t}{T_{year}}}$$

### t时刻股票价格的推导公式
由上述价格变动率公式可推导出t时刻的股票价格，其表达式为：
$$S_t = S_{t-1} \cdot \left(1 + \mu \cdot \frac{\Delta t}{T_{year}} + \sigma \cdot \epsilon \cdot \sqrt{\frac{\Delta t}{T_{year}}}\right)$$

### 参数说明
| 参数 | 含义 | 取值/来源 |
|------|------|----------|
| $S$ | 股票价格 | 初始价格可配置（如AAPL为110.0，TELSA为450.0） |
| $\mu$ | 预期年化收益率 | 按股票配置（AAPL=0.08，TELSA=0.12，默认0.05） |
| $\sigma$ | 年化波动率 | 按股票配置（AAPL=0.2，TELSA=0.3，默认0.2） |
| $\Delta t$ | 时间间隔（秒） | 基于两次价格更新的时间差计算 |
| $T_{year}$ | 年秒数 | 固定为7257600（按365天×24小时×3600秒计算） |
| $\epsilon$ | 标准正态分布随机变量 | 由随机数生成器产生（$N(0,1)$） |

### 实现细节
- 首次生成价格时使用配置的初始价格（`portfolio.marketdata.initial-price.{TICKER}`）
- 非首次生成时，基于上一次价格和时间戳计算$\Delta t$，代入GBM公式
- 价格精度固定为2位小数，且确保非负（最低0.01）
- 代码实现：`GBMPricingStrategy.generatePrice()`


## 三、期权定价：Black-Scholes模型

期权价格通过Black-Scholes模型计算，该模型基于无套利原理，假设标的资产价格服从几何布朗运动。

### 核心公式

#### 看涨期权价格
$$
C = S \cdot N(d_1) - K \cdot e^{-rT} \cdot N(d_2)
$$

#### 看跌期权价格
$$
P = K \cdot e^{-rT} \cdot N(-d_2) - S \cdot N(-d_1)
$$

#### 辅助计算
$$
d_1 = \frac{\ln(S/K) + (r + 0.5\sigma^2)T}{\sigma\sqrt{T}}
$$
$$
d_2 = d_1 - \sigma\sqrt{T}
$$
$$
N(x) = \frac{1}{\sqrt{2\pi}} \int_{-\infty}^{x} e^{-t^2/2} dt \quad \text{（标准正态分布累积分布函数）}
$$

### 参数说明
| 参数 | 含义 | 取值/来源 |
|------|------|----------|
| $S$ | 标的股票价格 | 由GBM模型实时生成 |
| $K$ | 期权行权价 | 期权合约属性（预设） |
| $r$ | 无风险利率 | 配置值（默认0.02） |
| $T$ | 到期时间（年） | 基于期权到期日与当前时间计算 |
| $\sigma$ | 标的股票波动率 | 与GBM模型中该股票的$\sigma$一致 |
| $N(\cdot)$ | 标准正态分布CDF | 代码中通过近似算法实现 |

### 实现细节
- 参数校验：若$S \leq 0$、$K \leq 0$或$T \leq 0$，返回0
- 价格精度固定为2位小数，且确保非负（若计算结果为负则返回0）
- 代码实现：`BlackScholesPricingService.calculate()`


## 四、组合估值逻辑

组合总净资产（NAV）为所有持仓市值之和，持仓市值计算规则如下：

1. **股票持仓**：  
   市值 = 标的股票实时价格 × 持仓数量  
   （价格来源：GBM模型生成的`MarketData`）

2. **期权持仓**：  
   市值 = 期权理论价格（Black-Scholes计算） × 持仓数量 × 合约乘数  
   （合约乘数配置：`portfolio.option.contract-multiplier`，默认1）

3. **实时更新机制**：  
   - 每500ms生成一次市场数据快照  
   - 对比上一快照，记录价格变动的股票  
   - 基于最新价格重新计算所有持仓市值并汇总NAV  
   - 代码实现：`ReactivePortfolioValuator.calculateRealTimeValuation()`


## 五、关键金融参数配置

所有金融参数可通过`application.properties`配置，核心参数如下：

```properties
# 无风险利率（Black-Scholes模型用）
portfolio.option.risk-free-rate=0.02

# 股票预期收益率μ（GBM模型用）
portfolio.marketdata.mu.AAPL=0.08
portfolio.marketdata.mu.TELSA=0.12

# 股票波动率σ（GBM和Black-Scholes模型共用）
portfolio.marketdata.sigma.AAPL=0.2
portfolio.marketdata.sigma.TELSA=0.3

# 股票初始价格
portfolio.marketdata.initial-price.AAPL=110.0
portfolio.marketdata.initial-price.TELSA=450.0

# 期权合约乘数
portfolio.option.contract-multiplier=1
```


## 六、模型验证

项目通过单元测试验证金融模型的正确性：

- **Black-Scholes模型测试**：  
  对平值、实值、虚值期权进行定价验证，例如：  
  - 当$S=100$，$K=100$，$T=1$，$r=0.02$，$\sigma=0.2$时，看涨期权理论价格约为8.90，看跌期权约为7.02（`BlackScholesPricingServiceTest`）

- **GBM模型测试**：  
  验证价格非负性、精度（2位小数）及公式正确性，确保相同参数下价格变化符合GBM理论（`GBMPricingStrategyTest`）
