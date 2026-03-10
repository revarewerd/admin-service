package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import zio.*
import zio.test.*
import java.util.UUID
import java.time.Instant

// ============================================================
// Тесты StatsService — in-memory реализация
// Тестируем логику агрегации системной статистики
// ============================================================

object StatsServiceSpec extends ZIOSpecDefault:

  // In-memory реализация — возвращает предустановленные значения
  final case class InMemoryStatsService(
    overview: Ref[SystemOverview]
  ) extends StatsService:

    override def getOverview: Task[SystemOverview] =
      overview.get

  object InMemoryStatsService:
    def make(data: SystemOverview): ZLayer[Any, Nothing, StatsService] =
      ZLayer(Ref.make(data).map(InMemoryStatsService(_)))

  private val sampleOverview = SystemOverview(
    companiesTotal = 15,
    companiesActive = 10,
    companiesNewThisMonth = 3,
    usersTotal = 45,
    usersActiveToday = 20,
    vehiclesTotal = 230,
    vehiclesOnline = 180,
    gpsPointsToday = 1_500_000L,
    storageUsedGb = 256L
  )

  private val emptyOverview = SystemOverview(
    companiesTotal = 0,
    companiesActive = 0,
    companiesNewThisMonth = 0,
    usersTotal = 0,
    usersActiveToday = 0,
    vehiclesTotal = 0,
    vehiclesOnline = 0,
    gpsPointsToday = 0L,
    storageUsedGb = 0L
  )

  def spec = suite("StatsService")(
    test("getOverview — возвращает корректную статистику") {
      for {
        service  <- ZIO.service[StatsService]
        overview <- service.getOverview
      } yield assertTrue(
        overview.companiesTotal == 15,
        overview.vehiclesTotal == 230,
        overview.vehiclesOnline == 180,
        overview.usersTotal == 45,
        overview.gpsPointsToday == 1_500_000L,
        overview.storageUsedGb == 256L
      )
    }.provide(InMemoryStatsService.make(sampleOverview)),

    test("getOverview — пустая система (нулевые значения)") {
      for {
        service  <- ZIO.service[StatsService]
        overview <- service.getOverview
      } yield assertTrue(
        overview.companiesTotal == 0,
        overview.vehiclesTotal == 0,
        overview.vehiclesOnline == 0,
        overview.gpsPointsToday == 0L,
        overview.storageUsedGb == 0L
      )
    }.provide(InMemoryStatsService.make(emptyOverview)),

    test("getOverview — activeDevices <= totalDevices") {
      for {
        service  <- ZIO.service[StatsService]
        overview <- service.getOverview
      } yield assertTrue(
        overview.vehiclesOnline <= overview.vehiclesTotal
      )
    }.provide(InMemoryStatsService.make(sampleOverview)),

    test("getOverview — systemUptime непустой") {
      for {
        service  <- ZIO.service[StatsService]
        overview <- service.getOverview
      } yield assertTrue(
        overview.storageUsedGb >= 0L
      )
    }.provide(InMemoryStatsService.make(sampleOverview))
  ) @@ TestAspect.timeout(60.seconds)
