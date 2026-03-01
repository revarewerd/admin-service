package com.wayrecall.tracker.admin

import com.wayrecall.tracker.admin.api.*
import com.wayrecall.tracker.admin.config.AppConfig
import com.wayrecall.tracker.admin.infrastructure.TransactorLayer
import com.wayrecall.tracker.admin.service.*
import zio.*
import zio.http.*
import zio.logging.backend.SLF4J

// ============================================================
// Main — точка входа Admin Service (порт 8097)
// Системное администрирование, мониторинг, конфигурация
// ============================================================

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def run: ZIO[Any, Any, Any] =
    val program = for {
      config <- ZIO.service[AppConfig]
      _      <- ZIO.logInfo(s"=== Admin Service запускается на порту ${config.server.port} ===")

      // Собираем все маршруты
      allRoutes = HealthRoutes.routes ++ AdminRoutes.routes

      // Запускаем HTTP-сервер
      _      <- Server.serve(allRoutes)
    } yield ()

    program.provide(
      // Конфигурация
      AppConfig.live,

      // БД транзактор
      ZLayer.service[AppConfig].flatMap(env => ZLayer.succeed(env.get.postgres)),
      TransactorLayer.live,

      // Redis
      zio.redis.Redis.local,
      zio.redis.RedisExecutor.local,
      zio.redis.CodecSupplier.utf8,

      // Health check config
      ZLayer.service[AppConfig].flatMap(env => ZLayer.succeed(env.get.healthCheck)),

      // Сервисы
      CompanyAdminService.live,
      SystemMonitorService.live,
      ConfigService.live,
      AdminAuditService.live,
      BackgroundTaskService.live,
      StatsService.live,

      // HTTP-сервер
      Server.defaultWithPort(8097)
    )
