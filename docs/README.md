> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

# Admin Service

## Обзор

Сервис системного администрирования для суперадминов. Управление компаниями, мониторинг здоровья системы, feature flags, фоновые задачи.

## Характеристики

| Параметр | Значение |
|----------|----------|
| Порт | **8097** |
| Пакет | `com.wayrecall.tracker.admin` |
| БД | PostgreSQL (чтение из всех БД) |
| Кэш | Redis (feature flags, config) |
| Доступ | Только super_admin |

## Быстрый запуск

```bash
cd services/admin-service
sbt run
# → http://localhost:8097/health
```

## Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| POSTGRES_URL | jdbc:postgresql://localhost:5432/tracker_admin | URL БД |
| POSTGRES_USER | admin | Пользователь БД |
| POSTGRES_PASSWORD | admin_pass | Пароль БД |
| REDIS_HOST | localhost | Redis хост |
| REDIS_PORT | 6379 | Redis порт |
| ADMIN_SERVICE_PORT | 8097 | Порт HTTP |

## Связанные документы

- [ARCHITECTURE_BLOCK3.md](../../../docs/ARCHITECTURE_BLOCK3.md) — Блок 3
- [USER_SERVICE.md](../../../docs/services/USER_SERVICE.md) — Управление пользователями
- [ADMIN_SERVICE.md](../../../docs/services/ADMIN_SERVICE.md) — Дизайн-документ
