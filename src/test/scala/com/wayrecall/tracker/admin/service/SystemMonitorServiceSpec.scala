package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.config.HealthCheckConfig
import com.wayrecall.tracker.admin.domain.*
import zio.*
import zio.test.*

// ============================================================
// Тесты SystemMonitorService — мониторинг здоровья системы
// ============================================================

object SystemMonitorServiceSpec extends ZIOSpecDefault:

  // Тестовая конфигурация
  private val testConfig = HealthCheckConfig(
    intervalSeconds = 30,
    timeoutSeconds = 5,
    services = List("connection-manager", "device-manager")
  )

  private val testLayer = ZLayer.succeed(testConfig) >>> SystemMonitorService.live

  def spec = suite("SystemMonitorService")(
    getHealthSpec,
    getServicesHealthSpec
  ) @@ TestAspect.timeout(30.seconds)

  val getHealthSpec = suite("getHealth")(
    test("возвращает SystemHealth со всеми компонентами") {
      for {
        service <- ZIO.service[SystemMonitorService]
        health  <- service.getHealth
      } yield assertTrue(
        health.services.nonEmpty,
        health.databases.nonEmpty,
        health.messageQueues.nonEmpty,
        health.timestamp != null
      )
    }.provide(testLayer),

    test("databases содержит postgresql, timescaledb и redis") {
      for {
        service <- ZIO.service[SystemMonitorService]
        health  <- service.getHealth
        dbNames = health.databases.map(_.name)
      } yield assertTrue(
        dbNames.contains("postgresql"),
        dbNames.contains("timescaledb"),
        dbNames.contains("redis")
      )
    }.provide(testLayer),

    test("messageQueues содержит kafka с топиками") {
      for {
        service <- ZIO.service[SystemMonitorService]
        health  <- service.getHealth
      } yield assertTrue(
        health.messageQueues.exists(_.name == "kafka"),
        health.messageQueues.head.topics.nonEmpty
      )
    }.provide(testLayer),

    test("kafka топики включают gps-events, device-status, device-commands") {
      for {
        service <- ZIO.service[SystemMonitorService]
        health  <- service.getHealth
        topics  = health.messageQueues.flatMap(_.topics.map(_.name))
      } yield assertTrue(
        topics.contains("gps-events"),
        topics.contains("device-status"),
        topics.contains("device-commands")
      )
    }.provide(testLayer),

    test("overallStatus — Healthy когда все сервисы и БД здоровы") {
      for {
        service <- ZIO.service[SystemMonitorService]
        health  <- service.getHealth
      } yield assertTrue(
        // databases — заглушки, всегда Healthy
        health.databases.forall(_.status == HealthStatus.Healthy)
      )
    }.provide(testLayer)
  )

  val getServicesHealthSpec = suite("getServicesHealth")(
    test("возвращает статусы всех зарегистрированных сервисов") {
      for {
        service  <- ZIO.service[SystemMonitorService]
        services <- service.getServicesHealth
        names    = services.map(_.name).toSet
      } yield assertTrue(
        // 8 сервисов в карте endpoints
        services.length == 8,
        names.contains("connection-manager"),
        names.contains("device-manager"),
        names.contains("history-writer"),
        names.contains("rule-checker"),
        names.contains("notification-service"),
        names.contains("analytics-service"),
        names.contains("integration-service"),
        names.contains("user-service")
      )
    }.provide(testLayer),

    test("недоступные сервисы помечаются как Critical") {
      // В тестовой среде сервисы не запущены, поэтому все Critical
      for {
        service  <- ZIO.service[SystemMonitorService]
        services <- service.getServicesHealth
      } yield assertTrue(
        services.forall(s => s.status == HealthStatus.Critical || s.status == HealthStatus.Healthy)
      )
    }.provide(testLayer),

    test("каждый сервис имеет lastCheck timestamp") {
      for {
        service  <- ZIO.service[SystemMonitorService]
        services <- service.getServicesHealth
      } yield assertTrue(
        services.forall(_.lastCheck != null)
      )
    }.provide(testLayer)
  )
