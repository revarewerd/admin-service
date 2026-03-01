package com.wayrecall.tracker.admin.config

import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.TypesafeConfigProvider

// ============================================================
// Конфигурация Admin Service
// ============================================================

final case class PostgresConfig(
  url: String,
  user: String,
  password: String,
  maxPoolSize: Int
)

final case class RedisConfig(
  host: String,
  port: Int
)

final case class HealthCheckConfig(
  intervalSeconds: Int,
  timeoutSeconds: Int,
  services: List[String]
)

final case class ServerConfig(
  port: Int
)

final case class AppConfig(
  postgres: PostgresConfig,
  redis: RedisConfig,
  healthCheck: HealthCheckConfig,
  server: ServerConfig
)

object AppConfig:
  val live: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO(
      ZIO.config[AppConfig](
        deriveConfig[AppConfig].mapKey(toKebabCase).nested("admin-service")
      )
    )
