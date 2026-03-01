> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

# Admin Service — Runbook

## Запуск

```bash
cd services/admin-service
sbt run
```

## Health check

```bash
curl http://localhost:8097/health
```

## Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| POSTGRES_URL | jdbc:postgresql://localhost:5432/tracker_admin | БД |
| POSTGRES_USER | admin | Пользователь |
| POSTGRES_PASSWORD | admin_pass | Пароль |
| REDIS_HOST | localhost | Redis |
| REDIS_PORT | 6379 | Redis порт |
| ADMIN_SERVICE_PORT | 8097 | HTTP порт |

## Troubleshooting

### Сервис не проходит health check
1. Проверить доступность PostgreSQL: `psql -h localhost -U admin -d tracker_admin`
2. Проверить Redis: `redis-cli ping`
3. Проверить логи: `tail -f logs/admin-service.log`

### Фоновая задача зависла
1. Проверить статус: `GET /api/v1/admin/tasks/{id}`
2. Отменить: `POST /api/v1/admin/tasks/{id}/cancel`
