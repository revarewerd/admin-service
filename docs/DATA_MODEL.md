> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

# Admin Service — DATA MODEL

## PostgreSQL (schema: admin)

### admin.background_tasks
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID PK | ID задачи |
| task_type | VARCHAR(50) | Тип задачи |
| status | VARCHAR(20) | Pending/Running/Completed/Failed/Cancelled |
| progress | INTEGER | 0-100 |
| parameters | JSONB | Параметры задачи |
| result | TEXT | Результат |
| error | TEXT | Ошибка |
| started_at | TIMESTAMPTZ | Начало |
| completed_at | TIMESTAMPTZ | Завершение |
| created_by | UUID | Кто создал |
| created_at | TIMESTAMPTZ | Когда создана |

### admin.admin_audit_log
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | UUID PK | — |
| company_id | UUID | Компания (nullable) |
| user_id | UUID | Кто выполнил |
| action | VARCHAR(100) | Действие |
| entity_type | VARCHAR(100) | Тип сущности |
| entity_id | UUID | ID сущности |
| details | JSONB | Детали |
| ip_address | VARCHAR(45) | IP |
| user_agent | TEXT | User-Agent |
| created_at | TIMESTAMPTZ | Когда |

## Redis Keys

| Ключ | Тип | TTL | Описание |
|------|-----|-----|----------|
| `admin:feature:{name}` | HASH | — | Feature flag |
| `admin:maintenance` | HASH | — | Maintenance mode config |
| `admin:limits` | HASH | — | System limits |
| `admin:config:cache` | STRING | 5 min | Cached SystemConfig |
