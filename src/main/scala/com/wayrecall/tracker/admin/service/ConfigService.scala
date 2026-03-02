package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import zio.*
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

final case class ConfigServiceLive(store: Ref[Map[String, String]]) extends ConfigService:

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
    _ <- store.update(_ + (s"admin:feature:$name" -> json))
    _ <- ZIO.logInfo(s"Feature flag обновлён: $name -> enabled=${update.enabled}")
  } yield ()

  override def enableMaintenanceMode(request: EnableMaintenanceRequest, actorId: UUID): Task[Unit] =
    val config = MaintenanceConfig(
      maintenanceMode = true,
      maintenanceMessage = request.message,
      scheduledMaintenanceStart = None,
      scheduledMaintenanceEnd = None,
      allowedIps = request.allowedIps
    )
    val json = config.toJson
    for {
      _ <- store.update(_ + ("admin:maintenance" -> json))
      _ <- ZIO.logInfo(s"Режим обслуживания ВКЛЮЧЁН пользователем $actorId")
    } yield ()

  override def disableMaintenanceMode(actorId: UUID): Task[Unit] =
    val config = MaintenanceConfig(false, None, None, None, List.empty)
    val json = config.toJson
    for {
      _ <- store.update(_ + ("admin:maintenance" -> json))
      _ <- ZIO.logInfo(s"Режим обслуживания ВЫКЛЮЧЕН пользователем $actorId")
    } yield ()

  override def updateLimits(limits: Map[String, Int]): Task[Unit] =
    val json = limits.toJson
    store.update(_ + ("admin:limits" -> json))

  // === Приватные методы ===

  private def getFeatureFlags: Task[Map[String, FeatureFlag]] =
    store.get.map { data =>
      data.collect {
        case (key, value) if key.startsWith("admin:feature:") =>
          value.fromJson[FeatureFlag].toOption.map(f => f.name -> f)
      }.flatten.toMap
    }

  private def getLimits: Task[Map[String, Int]] =
    store.get.map { data =>
      data.get("admin:limits")
        .flatMap(_.fromJson[Map[String, Int]].toOption)
        .getOrElse(
          Map(
            "maxConnectionsPerDevice" -> 10,
            "maxWebSocketsPerUser" -> 5,
            "defaultRateLimit" -> 1000
          )
        )
    }

  private def getMaintenanceConfig: Task[MaintenanceConfig] =
    store.get.map { data =>
      data.get("admin:maintenance")
        .flatMap(_.fromJson[MaintenanceConfig].toOption)
        .getOrElse(MaintenanceConfig(false, None, None, None, List.empty))
    }

object ConfigService:
  val live: ZLayer[Any, Nothing, ConfigService] =
    ZLayer {
      Ref.make(Map.empty[String, String]).map(ConfigServiceLive(_))
    }
