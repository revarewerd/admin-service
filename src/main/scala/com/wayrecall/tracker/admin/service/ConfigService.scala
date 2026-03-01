package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import zio.*
import zio.redis.Redis
import zio.json.*
import java.time.Instant
import java.util.UUID

// ============================================================
// ConfigService — управление feature flags и maintenance mode
// Хранит конфигурацию в Redis для мгновенного применения
// ============================================================

trait ConfigService:
  /** Получить системную конфигурацию */
  def getConfig: Task[SystemConfig]
  /** Обновить feature flag */
  def updateFeatureFlag(name: String, update: FeatureFlagUpdate, actorId: UUID): Task[Unit]
  /** Включить режим обслуживания */
  def enableMaintenanceMode(request: EnableMaintenanceRequest, actorId: UUID): Task[Unit]
  /** Выключить режим обслуживания */
  def disableMaintenanceMode(actorId: UUID): Task[Unit]
  /** Обновить лимиты */
  def updateLimits(limits: Map[String, Int]): Task[Unit]

final case class ConfigServiceLive(redis: Redis) extends ConfigService:

  override def getConfig: Task[SystemConfig] = for {
    features    <- getFeatureFlags
    limits      <- getLimits
    maintenance <- getMaintenanceConfig
  } yield SystemConfig(features, limits, maintenance)

  override def updateFeatureFlag(name: String, update: FeatureFlagUpdate, actorId: UUID): Task[Unit] = for {
    now <- Clock.instant
    flag = FeatureFlag(
      name = name,
      enabled = update.enabled,
      description = "",
      enabledForCompanies = update.enabledForCompanies,
      updatedAt = now,
      updatedBy = actorId
    )
    json = flag.toJson
    _ <- redis.set(s"admin:feature:$name", json)
    _ <- ZIO.logInfo(s"Feature flag обновлён: $name -> enabled=${update.enabled}")
  } yield ()

  override def enableMaintenanceMode(request: EnableMaintenanceRequest, actorId: UUID): Task[Unit] = for {
    config = MaintenanceConfig(
      maintenanceMode = true,
      maintenanceMessage = request.message,
      scheduledMaintenanceStart = None,
      scheduledMaintenanceEnd = None,
      allowedIps = request.allowedIps
    )
    json = config.toJson
    _ <- redis.set("admin:maintenance", json)
    _ <- ZIO.logInfo(s"Режим обслуживания ВКЛЮЧЁН пользователем $actorId")
  } yield ()

  override def disableMaintenanceMode(actorId: UUID): Task[Unit] = for {
    config = MaintenanceConfig(false, None, None, None, List.empty)
    json = config.toJson
    _ <- redis.set("admin:maintenance", json)
    _ <- ZIO.logInfo(s"Режим обслуживания ВЫКЛЮЧЕН пользователем $actorId")
  } yield ()

  override def updateLimits(limits: Map[String, Int]): Task[Unit] =
    val json = limits.toJson
    redis.set("admin:limits", json).unit

  // === Приватные методы ===

  private def getFeatureFlags: Task[Map[String, FeatureFlag]] =
    // Получаем все ключи admin:feature:*
    redis.keys("admin:feature:*").flatMap { keys =>
      ZIO.foreach(keys.toList) { key =>
        redis.get(key).returning[String].map { valueOpt =>
          for {
            value <- valueOpt
            flag  <- value.fromJson[FeatureFlag].toOption
          } yield flag.name -> flag
        }
      }.map(_.flatten.toMap)
    }.catchAll(_ => ZIO.succeed(Map.empty))

  private def getLimits: Task[Map[String, Int]] =
    redis.get("admin:limits").returning[String].map { opt =>
      opt.flatMap(_.fromJson[Map[String, Int]].toOption).getOrElse(
        Map(
          "maxConnectionsPerDevice" -> 10,
          "maxWebSocketsPerUser" -> 5,
          "defaultRateLimit" -> 1000
        )
      )
    }.catchAll(_ => ZIO.succeed(Map.empty))

  private def getMaintenanceConfig: Task[MaintenanceConfig] =
    redis.get("admin:maintenance").returning[String].map { opt =>
      opt.flatMap(_.fromJson[MaintenanceConfig].toOption).getOrElse(
        MaintenanceConfig(false, None, None, None, List.empty)
      )
    }.catchAll(_ => ZIO.succeed(MaintenanceConfig(false, None, None, None, List.empty)))

object ConfigService:
  val live: ZLayer[Redis, Nothing, ConfigService] =
    ZLayer.fromFunction(ConfigServiceLive(_))
