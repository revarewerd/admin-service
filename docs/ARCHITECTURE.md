> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

# Admin Service — Архитектура

## Компоненты

```mermaid
graph TB
    subgraph "API Layer"
        HEALTH[HealthRoutes]
        ADMIN[AdminRoutes]
    end

    subgraph "Services"
        COMP[CompanyAdminService]
        SYS[SystemMonitorService]
        CONF[ConfigService]
        AUDIT[AuditService]
        TASK[BackgroundTaskService]
    end

    subgraph "Infrastructure"
        HC[HealthChecker]
        TE[TaskExecutor]
    end

    subgraph "Storage"
        PG[(PostgreSQL)]
        REDIS[(Redis)]
    end

    ADMIN --> COMP
    ADMIN --> SYS
    ADMIN --> CONF
    ADMIN --> AUDIT
    ADMIN --> TASK

    COMP --> PG
    SYS --> HC
    CONF --> REDIS
    AUDIT --> PG
    TASK --> TE
    TE --> PG
    HC --> PG
```

## ZIO Layer Graph

```
Main
├── AppConfig.live
├── TransactorLayer.live
├── Redis.local
├── CompanyAdminService.live
│   └── Transactor
├── SystemMonitorService.live
│   └── HealthChecker.live
├── ConfigService.live
│   └── Redis
├── AuditService.live
│   └── Transactor
├── BackgroundTaskService.live
│   └── TaskExecutor.live
└── Server.defaultWithPort(8097)
```
