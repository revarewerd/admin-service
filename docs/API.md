> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

# Admin Service — REST API

| Метод | URL | Описание |
|-------|-----|----------|
| GET | /health | Health check |
| GET | /api/v1/admin/companies | Список компаний |
| GET | /api/v1/admin/companies/{id} | Детали компании |
| POST | /api/v1/admin/companies | Создать компанию |
| POST | /api/v1/admin/companies/{id}/deactivate | Деактивировать |
| POST | /api/v1/admin/companies/{id}/activate | Активировать |
| GET | /api/v1/admin/system/health | Здоровье системы |
| GET | /api/v1/admin/system/services | Статус сервисов |
| GET | /api/v1/admin/config | Конфигурация |
| PUT | /api/v1/admin/config/features/{name} | Обновить feature flag |
| POST | /api/v1/admin/config/maintenance/enable | Включить maintenance |
| POST | /api/v1/admin/config/maintenance/disable | Выключить maintenance |
| GET | /api/v1/admin/audit | Лог аудита |
| GET | /api/v1/admin/tasks | Список задач |
| GET | /api/v1/admin/tasks/{id} | Статус задачи |
| POST | /api/v1/admin/tasks/{id}/cancel | Отменить задачу |
| POST | /api/v1/admin/tasks/cleanup | Запустить очистку |
| POST | /api/v1/admin/tasks/backup | Запустить бэкап |
| GET | /api/v1/admin/stats/overview | Общая статистика |
