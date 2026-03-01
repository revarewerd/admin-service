package com.wayrecall.tracker.admin.domain

import zio.json.*
import java.time.Instant
import java.util.UUID

// ============================================================
// Domain Models — Admin Service
// Модели данных для системного администрирования
// ============================================================

// ============================================
// Управление компаниями
// ============================================

/** Краткая информация о компании (для списка) */
final case class CompanySummary(
  id: UUID,
  name: String,
  usersCount: Int,
  vehiclesCount: Int,
  subscriptionPlan: String,
  subscriptionExpires: Option[Instant],
  isActive: Boolean,
  createdAt: Instant,
  lastActivity: Option[Instant]
) derives JsonCodec

/** Статистика использования ресурсов компанией */
final case class UsageStats(
  vehiclesCount: Int,
  usersCount: Int,
  gpsPointsThisMonth: Long,
  storageUsedMb: Long,
  apiCallsThisMonth: Long,
  lastDataReceived: Option[Instant]
) derives JsonCodec

/** Детальная информация о компании */
final case class CompanyDetails(
  id: UUID,
  name: String,
  inn: Option[String],
  phone: Option[String],
  email: Option[String],
  timezone: String,
  isActive: Boolean,
  subscriptionPlan: String,
  subscriptionExpires: Option[Instant],
  maxVehicles: Int,
  maxUsers: Int,
  usage: UsageStats,
  createdAt: Instant
) derives JsonCodec

/** Создание компании суперадмином */
final case class CreateCompanyAdmin(
  name: String,
  inn: Option[String],
  address: Option[String],
  phone: Option[String],
  email: Option[String],
  timezone: String,
  subscriptionPlan: String,
  maxVehicles: Int,
  maxUsers: Int,
  subscriptionExpires: Option[Instant],
  ownerEmail: String,
  ownerFirstName: String,
  ownerLastName: String
) derives JsonCodec

/** Обновление подписки */
final case class UpdateSubscription(
  plan: String,
  maxVehicles: Int,
  maxUsers: Int,
  expiresAt: Option[Instant]
) derives JsonCodec

/** Фильтры для списка компаний */
final case class CompanyFilters(
  search: Option[String],
  isActive: Option[Boolean],
  plan: Option[String],
  page: Int,
  pageSize: Int
) derives JsonCodec

// ============================================
// Мониторинг системы
// ============================================

/** Статус здоровья */
enum HealthStatus:
  case Healthy, Degraded, Critical

object HealthStatus:
  given JsonEncoder[HealthStatus] = JsonEncoder[String].contramap(_.toString)
  given JsonDecoder[HealthStatus] = JsonDecoder[String].map(s => HealthStatus.valueOf(s))

/** Общее здоровье системы */
final case class SystemHealth(
  status: HealthStatus,
  services: List[ServiceHealth],
  databases: List[DatabaseHealth],
  messageQueues: List[QueueHealth],
  timestamp: Instant
) derives JsonCodec

/** Здоровье отдельного сервиса */
final case class ServiceHealth(
  name: String,
  status: HealthStatus,
  instances: Int,
  healthyInstances: Int,
  latencyP50Ms: Double,
  latencyP99Ms: Double,
  errorRate: Double,
  lastCheck: Instant
) derives JsonCodec

/** Здоровье базы данных */
final case class DatabaseHealth(
  name: String,
  status: HealthStatus,
  connectionPoolUsed: Int,
  connectionPoolMax: Int,
  diskUsagePercent: Double,
  queryLatencyP99Ms: Double
) derives JsonCodec

/** Здоровье очереди сообщений */
final case class QueueHealth(
  name: String,
  status: HealthStatus,
  brokers: Int,
  healthyBrokers: Int,
  topics: List[TopicHealth]
) derives JsonCodec

/** Здоровье отдельного топика */
final case class TopicHealth(
  name: String,
  partitions: Int,
  consumerLag: Long,
  messagesPerSecond: Double
) derives JsonCodec

/** Метрики системы */
final case class SystemMetrics(
  timestamp: Instant,
  totalRequests: Long,
  requestsPerSecond: Double,
  errorRate: Double,
  avgLatencyMs: Double,
  p99LatencyMs: Double,
  activeConnections: Int,
  gpsPointsPerSecond: Double
) derives JsonCodec

// ============================================
// Конфигурация
// ============================================

/** Системная конфигурация */
final case class SystemConfig(
  features: Map[String, FeatureFlag],
  limits: Map[String, Int],
  maintenance: MaintenanceConfig
) derives JsonCodec

/** Feature flag */
final case class FeatureFlag(
  name: String,
  enabled: Boolean,
  description: String,
  enabledForCompanies: Option[Set[UUID]],
  updatedAt: Instant,
  updatedBy: UUID
) derives JsonCodec

/** Обновление feature flag */
final case class FeatureFlagUpdate(
  enabled: Boolean,
  enabledForCompanies: Option[Set[UUID]]
) derives JsonCodec

/** Конфигурация режима обслуживания */
final case class MaintenanceConfig(
  maintenanceMode: Boolean,
  maintenanceMessage: Option[String],
  scheduledMaintenanceStart: Option[Instant],
  scheduledMaintenanceEnd: Option[Instant],
  allowedIps: List[String]
) derives JsonCodec

/** Обновление лимитов */
final case class LimitsUpdate(
  limits: Map[String, Int]
) derives JsonCodec

/** Запрос на включение maintenance */
final case class EnableMaintenanceRequest(
  message: Option[String],
  allowedIps: List[String]
) derives JsonCodec

// ============================================
// Аудит
// ============================================

/** Расширенный аудит для администратора */
final case class AdminAuditEntry(
  id: UUID,
  companyId: Option[UUID],
  companyName: Option[String],
  userId: UUID,
  userEmail: String,
  action: String,
  entityType: String,
  entityId: Option[UUID],
  details: Option[String],
  ipAddress: Option[String],
  userAgent: Option[String],
  createdAt: Instant
) derives JsonCodec

/** Фильтры для аудита */
final case class AdminAuditFilters(
  companyId: Option[UUID],
  userId: Option[UUID],
  action: Option[String],
  entityType: Option[String],
  fromDate: Option[Instant],
  toDate: Option[Instant],
  page: Int,
  pageSize: Int
)

/** Результат с пагинацией */
final case class Page[A](
  total: Long,
  items: List[A],
  page: Int,
  pageSize: Int
)

object Page:
  given [A: JsonCodec]: JsonCodec[Page[A]] = DeriveJsonCodec.gen[Page[A]]

// ============================================
// Фоновые задачи
// ============================================

/** Тип фоновой задачи */
enum TaskType:
  case DataExport, DataCleanup, DatabaseBackup, ReindexSearch, RecalculateStats, BulkOperation

object TaskType:
  given JsonEncoder[TaskType] = JsonEncoder[String].contramap(_.toString)
  given JsonDecoder[TaskType] = JsonDecoder[String].map(s => TaskType.valueOf(s))

/** Статус задачи */
enum TaskStatus:
  case Pending, Running, Completed, Failed, Cancelled

object TaskStatus:
  given JsonEncoder[TaskStatus] = JsonEncoder[String].contramap(_.toString)
  given JsonDecoder[TaskStatus] = JsonDecoder[String].map(s => TaskStatus.valueOf(s))

/** Фоновая задача */
final case class BackgroundTask(
  id: UUID,
  taskType: TaskType,
  status: TaskStatus,
  progress: Int,
  parameters: Option[String],
  result: Option[String],
  error: Option[String],
  startedAt: Instant,
  completedAt: Option[Instant],
  createdBy: UUID
) derives JsonCodec

/** Запуск очистки */
final case class CleanupRequest(
  cleanupType: String,
  olderThan: String
) derives JsonCodec

/** Запуск бэкапа */
final case class BackupRequest(
  databases: List[String],
  uploadToS3: Boolean
) derives JsonCodec

/** Ответ с taskId */
final case class TaskCreated(
  taskId: UUID
) derives JsonCodec

// ============================================
// Статистика
// ============================================

/** Общая статистика системы */
final case class SystemOverview(
  companiesTotal: Int,
  companiesActive: Int,
  companiesNewThisMonth: Int,
  usersTotal: Int,
  usersActiveToday: Int,
  vehiclesTotal: Int,
  vehiclesOnline: Int,
  gpsPointsToday: Long,
  storageUsedGb: Long
) derives JsonCodec
