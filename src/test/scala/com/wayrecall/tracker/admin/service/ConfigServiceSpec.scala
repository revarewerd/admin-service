package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import zio.*
import zio.test.*
import zio.json.*
import java.util.UUID

// ============================================================
// Тесты ConfigService — feature flags, maintenance mode, limits
// ConfigServiceLive использует Ref[Map[String,String]] — тестируем без БД
// ============================================================

object ConfigServiceSpec extends ZIOSpecDefault:

  val testLayer = ConfigService.live

  def spec = suite("ConfigService")(
    getConfigSuite,
    featureFlagsSuite,
    maintenanceModeSuite,
    limitsSuite
  )

  // === getConfig ===

  val getConfigSuite = suite("getConfig")(
    test("начальная конфигурация — пустые features, дефолтные limits, maintenance off") {
      for {
        service <- ZIO.service[ConfigService]
        config  <- service.getConfig
      } yield assertTrue(
        config.features.isEmpty,
        config.limits.contains("maxConnectionsPerDevice"),
        config.limits("maxConnectionsPerDevice") == 10,
        config.limits("maxWebSocketsPerUser") == 5,
        config.limits("defaultRateLimit") == 1000,
        !config.maintenance.maintenanceMode,
        config.maintenance.maintenanceMessage.isEmpty,
        config.maintenance.allowedIps.isEmpty
      )
    }.provide(testLayer)
  )

  // === Feature Flags ===

  val featureFlagsSuite = suite("Feature Flags")(
    test("updateFeatureFlag создаёт новый flag и возвращает его в getConfig") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateFeatureFlag("gps_filtering", FeatureFlagUpdate(true, None), actorId)
        config  <- service.getConfig
      } yield assertTrue(
        config.features.contains("gps_filtering"),
        config.features("gps_filtering").enabled,
        config.features("gps_filtering").name == "gps_filtering",
        config.features("gps_filtering").updatedBy == actorId
      )
    }.provide(testLayer),

    test("updateFeatureFlag может отключить existующий flag") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateFeatureFlag("ws_compression", FeatureFlagUpdate(true, None), actorId)
        _       <- service.updateFeatureFlag("ws_compression", FeatureFlagUpdate(false, None), actorId)
        config  <- service.getConfig
      } yield assertTrue(
        config.features.contains("ws_compression"),
        !config.features("ws_compression").enabled
      )
    }.provide(testLayer),

    test("updateFeatureFlag с enabledForCompanies") {
      val actorId = UUID.randomUUID()
      val companyIds = Set(UUID.randomUUID(), UUID.randomUUID())
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateFeatureFlag("beta_feature", FeatureFlagUpdate(true, Some(companyIds)), actorId)
        config  <- service.getConfig
      } yield assertTrue(
        config.features("beta_feature").enabledForCompanies == Some(companyIds)
      )
    }.provide(testLayer),

    test("несколько feature flags хранятся независимо") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateFeatureFlag("flag_a", FeatureFlagUpdate(true, None), actorId)
        _       <- service.updateFeatureFlag("flag_b", FeatureFlagUpdate(false, None), actorId)
        _       <- service.updateFeatureFlag("flag_c", FeatureFlagUpdate(true, None), actorId)
        config  <- service.getConfig
      } yield assertTrue(
        config.features.size == 3,
        config.features("flag_a").enabled,
        !config.features("flag_b").enabled,
        config.features("flag_c").enabled
      )
    }.provide(testLayer)
  )

  // === Maintenance Mode ===

  val maintenanceModeSuite = suite("Maintenance Mode")(
    test("enableMaintenanceMode включает режим обслуживания") {
      val actorId = UUID.randomUUID()
      val request = EnableMaintenanceRequest(
        message = Some("Плановые работы до 22:00"),
        allowedIps = List("192.168.1.1", "10.0.0.1")
      )
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.enableMaintenanceMode(request, actorId)
        config  <- service.getConfig
      } yield assertTrue(
        config.maintenance.maintenanceMode,
        config.maintenance.maintenanceMessage == Some("Плановые работы до 22:00"),
        config.maintenance.allowedIps == List("192.168.1.1", "10.0.0.1")
      )
    }.provide(testLayer),

    test("disableMaintenanceMode выключает режим обслуживания") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.enableMaintenanceMode(EnableMaintenanceRequest(Some("test"), Nil), actorId)
        config1 <- service.getConfig
        _       <- service.disableMaintenanceMode(actorId)
        config2 <- service.getConfig
      } yield assertTrue(
        config1.maintenance.maintenanceMode,
        !config2.maintenance.maintenanceMode,
        config2.maintenance.maintenanceMessage.isEmpty,
        config2.maintenance.allowedIps.isEmpty
      )
    }.provide(testLayer),

    test("enable + disable + enable корректно переключается") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.enableMaintenanceMode(EnableMaintenanceRequest(Some("Раунд 1"), Nil), actorId)
        _       <- service.disableMaintenanceMode(actorId)
        _       <- service.enableMaintenanceMode(EnableMaintenanceRequest(Some("Раунд 2"), List("10.0.0.1")), actorId)
        config  <- service.getConfig
      } yield assertTrue(
        config.maintenance.maintenanceMode,
        config.maintenance.maintenanceMessage == Some("Раунд 2"),
        config.maintenance.allowedIps == List("10.0.0.1")
      )
    }.provide(testLayer)
  )

  // === Limits ===

  val limitsSuite = suite("Limits")(
    test("дефолтные лимиты при отсутствии сохранённых") {
      for {
        service <- ZIO.service[ConfigService]
        config  <- service.getConfig
      } yield assertTrue(
        config.limits("maxConnectionsPerDevice") == 10,
        config.limits("maxWebSocketsPerUser") == 5,
        config.limits("defaultRateLimit") == 1000
      )
    }.provide(testLayer),

    test("updateLimits заменяет лимиты") {
      val newLimits = Map(
        "maxConnectionsPerDevice" -> 20,
        "maxWebSocketsPerUser" -> 10,
        "defaultRateLimit" -> 2000,
        "customLimit" -> 42
      )
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateLimits(newLimits)
        config  <- service.getConfig
      } yield assertTrue(
        config.limits("maxConnectionsPerDevice") == 20,
        config.limits("maxWebSocketsPerUser") == 10,
        config.limits("defaultRateLimit") == 2000,
        config.limits("customLimit") == 42
      )
    }.provide(testLayer),

    test("updateLimits полностью перезаписывает предыдущие лимиты") {
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateLimits(Map("limitA" -> 100, "limitB" -> 200))
        _       <- service.updateLimits(Map("limitC" -> 300))
        config  <- service.getConfig
      } yield assertTrue(
        config.limits == Map("limitC" -> 300),
        !config.limits.contains("limitA")
      )
    }.provide(testLayer),

    test("feature flags и limits не мешают друг другу") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[ConfigService]
        _       <- service.updateFeatureFlag("test_flag", FeatureFlagUpdate(true, None), actorId)
        _       <- service.updateLimits(Map("testLimit" -> 999))
        config  <- service.getConfig
      } yield assertTrue(
        config.features.contains("test_flag"),
        config.limits("testLimit") == 999
      )
    }.provide(testLayer)
  )
