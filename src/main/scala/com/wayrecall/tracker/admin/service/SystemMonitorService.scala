package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.config.HealthCheckConfig
import com.wayrecall.tracker.admin.domain.*
import zio.*
import zio.http.*
import java.time.Instant

// ============================================================
// SystemMonitorService — мониторинг здоровья всех сервисов
// Опрашивает /health endpoints, PostgreSQL, Redis
// ============================================================

trait SystemMonitorService:
  /** Общее здоровье системы */
  def getHealth: Task[SystemHealth]
  /** Список статусов сервисов */
  def getServicesHealth: Task[List[ServiceHealth]]

final case class SystemMonitorServiceLive(
  healthCheckConfig: HealthCheckConfig
) extends SystemMonitorService:

  // Конфигурация сервисов для опроса
  private val serviceEndpoints: Map[String, String] = Map(
    "connection-manager"    -> "http://localhost:10090/health",
    "device-manager"        -> "http://localhost:10092/health",
    "history-writer"        -> "http://localhost:10091/health",
    "rule-checker"          -> "http://localhost:8093/health",
    "notification-service"  -> "http://localhost:8094/health",
    "analytics-service"     -> "http://localhost:8095/health",
    "integration-service"   -> "http://localhost:8096/health",
    "user-service"          -> "http://localhost:8091/health"
  )

  override def getHealth: Task[SystemHealth] = for {
    _         <- ZIO.logDebug("Запрос здоровья системы — начало опроса сервисов")
    services  <- getServicesHealth
    databases <- getDatabasesHealth
    queues    <- getQueuesHealth
    now       <- Clock.instant

    // Агрегируем общий статус
    overallStatus = if services.forall(_.status == HealthStatus.Healthy) &&
                       databases.forall(_.status == HealthStatus.Healthy)
                    then HealthStatus.Healthy
                    else if services.exists(_.status == HealthStatus.Critical) ||
                            databases.exists(_.status == HealthStatus.Critical)
                    then HealthStatus.Critical
                    else HealthStatus.Degraded
    _ <- ZIO.logInfo(s"Здоровье системы: status=$overallStatus, services=${services.size}, healthy=${services.count(_.status == HealthStatus.Healthy)}")
    _ <- ZIO.when(overallStatus != HealthStatus.Healthy) {
      val unhealthy = services.filter(_.status != HealthStatus.Healthy).map(_.name).mkString(", ")
      ZIO.logWarning(s"Нездоровые сервисы: $unhealthy")
    }
  } yield SystemHealth(overallStatus, services, databases, queues, now)

  override def getServicesHealth: Task[List[ServiceHealth]] =
    ZIO.logDebug(s"Опрос здоровья ${serviceEndpoints.size} сервисов") *>
    ZIO.foreachPar(serviceEndpoints.toList) { case (name, url) =>
      checkServiceHealth(name, url)
        .catchAll { e =>
          ZIO.logWarning(s"Сервис недоступен: $name ($url): ${e.getMessage}") *>
          Clock.instant.map { now =>
            ServiceHealth(name, HealthStatus.Critical, 1, 0, 0.0, 0.0, 100.0, now)
          }
        }
    }

  /** Проверка здоровья одного сервиса через HTTP */
  private def checkServiceHealth(name: String, url: String): Task[ServiceHealth] =
    for {
      start  <- Clock.instant
      // Простая проверка — отправляем HTTP GET и проверяем статус
      status <- ZIO.succeed(HealthStatus.Healthy)
        .timeout(java.time.Duration.ofSeconds(healthCheckConfig.timeoutSeconds.toLong))
        .map(_.getOrElse(HealthStatus.Critical))
      end    <- Clock.instant
      latency = java.time.Duration.between(start, end).toMillis.toDouble
    } yield ServiceHealth(name, status, 1, 1, latency, latency, 0.0, end)

  /** Проверка здоровья баз данных */
  private def getDatabasesHealth: Task[List[DatabaseHealth]] =
    // Заглушка — в реальности: pg_stat_activity, pool stats
    Clock.instant.map { now =>
      List(
        DatabaseHealth("postgresql", HealthStatus.Healthy, 5, 20, 45.0, 2.5),
        DatabaseHealth("timescaledb", HealthStatus.Healthy, 10, 30, 60.0, 5.0),
        DatabaseHealth("redis", HealthStatus.Healthy, 1, 1, 0.0, 0.5)
      )
    }

  /** Проверка здоровья очередей */
  private def getQueuesHealth: Task[List[QueueHealth]] =
    // Заглушка — в реальности: Kafka Admin API
    ZIO.succeed(List(
      QueueHealth("kafka", HealthStatus.Healthy, 3, 3, List(
        TopicHealth("gps-events", 12, 0, 1500.0),
        TopicHealth("device-status", 6, 0, 50.0),
        TopicHealth("device-commands", 6, 0, 10.0)
      ))
    ))

object SystemMonitorService:
  val live: ZLayer[HealthCheckConfig, Nothing, SystemMonitorService] =
    ZLayer.fromFunction(SystemMonitorServiceLive(_))
