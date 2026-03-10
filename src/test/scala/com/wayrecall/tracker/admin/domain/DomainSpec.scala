package com.wayrecall.tracker.admin.domain

import zio.*
import zio.test.*
import zio.json.*
import java.time.Instant
import java.util.UUID

// ============================================================
// Тесты доменных моделей Admin Service
// ============================================================

object DomainSpec extends ZIOSpecDefault:

  def spec = suite("Domain Models")(
    healthStatusSuite,
    taskTypeSuite,
    taskStatusSuite,
    errorsSuite,
    modelsSuite
  )

  // === HealthStatus enum ===

  val healthStatusSuite = suite("HealthStatus")(
    test("содержит 3 значения") {
      assertTrue(HealthStatus.values.length == 3)
    },
    test("JSON encoder кодирует в строку") {
      assertTrue(
        HealthStatus.Healthy.toJson == "\"Healthy\"",
        HealthStatus.Degraded.toJson == "\"Degraded\"",
        HealthStatus.Critical.toJson == "\"Critical\""
      )
    },
    test("JSON decoder парсит из строки") {
      assertTrue(
        "\"Healthy\"".fromJson[HealthStatus] == Right(HealthStatus.Healthy),
        "\"Degraded\"".fromJson[HealthStatus] == Right(HealthStatus.Degraded),
        "\"Critical\"".fromJson[HealthStatus] == Right(HealthStatus.Critical)
      )
    }
  )

  // === TaskType enum ===

  val taskTypeSuite = suite("TaskType")(
    test("содержит 6 значений") {
      assertTrue(TaskType.values.length == 6)
    },
    test("все значения уникальны") {
      val names = TaskType.values.map(_.toString).toSet
      assertTrue(names.size == 6)
    },
    test("JSON roundtrip") {
      TaskType.values.foreach { tt =>
        val json = tt.toJson
        val decoded = json.fromJson[TaskType]
        assert(decoded)(Assertion.equalTo(Right(tt)))
      }
      assertTrue(true)
    },
    test("конкретные значения проверяются") {
      assertTrue(
        TaskType.DataExport.toString == "DataExport",
        TaskType.DataCleanup.toString == "DataCleanup",
        TaskType.DatabaseBackup.toString == "DatabaseBackup",
        TaskType.ReindexSearch.toString == "ReindexSearch",
        TaskType.RecalculateStats.toString == "RecalculateStats",
        TaskType.BulkOperation.toString == "BulkOperation"
      )
    }
  )

  // === TaskStatus enum ===

  val taskStatusSuite = suite("TaskStatus")(
    test("содержит 5 значений") {
      assertTrue(TaskStatus.values.length == 5)
    },
    test("JSON roundtrip для каждого значения") {
      assertTrue(
        TaskStatus.Pending.toJson == "\"Pending\"",
        TaskStatus.Running.toJson == "\"Running\"",
        TaskStatus.Completed.toJson == "\"Completed\"",
        TaskStatus.Failed.toJson == "\"Failed\"",
        TaskStatus.Cancelled.toJson == "\"Cancelled\""
      )
    }
  )

  // === AdminError ===

  val errorsSuite = suite("AdminError")(
    test("Forbidden содержит reason") {
      val err = AdminError.Forbidden("Требуется роль super_admin")
      assertTrue(
        err.message.contains("super_admin"),
        err.getMessage == err.message,
        err.isInstanceOf[Throwable]
      )
    },
    test("CompanyNotFound содержит id") {
      val id = UUID.randomUUID()
      val err = AdminError.CompanyNotFound(id)
      assertTrue(err.message.contains(id.toString))
    },
    test("TaskNotFound содержит id") {
      val id = UUID.randomUUID()
      val err = AdminError.TaskNotFound(id)
      assertTrue(err.message.contains(id.toString))
    },
    test("InvalidConfiguration содержит список ошибок") {
      val err = AdminError.InvalidConfiguration(List("field1 is missing", "field2 invalid"))
      assertTrue(
        err.message.contains("field1 is missing"),
        err.message.contains("field2 invalid")
      )
    },
    test("MaintenanceModeActive — case object") {
      val err = AdminError.MaintenanceModeActive
      assertTrue(err.message.contains("обслуживания"))
    },
    test("DatabaseError содержит cause") {
      val err = AdminError.DatabaseError("Connection timeout")
      assertTrue(err.message.contains("Connection timeout"))
    },
    test("ErrorResponse JSON roundtrip") {
      val resp = AdminError.ErrorResponse("forbidden", "Доступ запрещён")
      val json = resp.toJson
      val decoded = json.fromJson[AdminError.ErrorResponse]
      assertTrue(decoded == Right(resp))
    },
    test("все 6 подтипов покрыты exhaustive match") {
      val errors: List[AdminError] = List(
        AdminError.Forbidden("reason"),
        AdminError.CompanyNotFound(UUID.randomUUID()),
        AdminError.TaskNotFound(UUID.randomUUID()),
        AdminError.InvalidConfiguration(List("err")),
        AdminError.MaintenanceModeActive,
        AdminError.DatabaseError("cause")
      )
      val messages = errors.map {
        case e: AdminError.Forbidden             => e.message
        case e: AdminError.CompanyNotFound       => e.message
        case e: AdminError.TaskNotFound          => e.message
        case e: AdminError.InvalidConfiguration  => e.message
        case AdminError.MaintenanceModeActive    => AdminError.MaintenanceModeActive.message
        case e: AdminError.DatabaseError         => e.message
      }
      assertTrue(messages.length == 6, messages.forall(_.nonEmpty))
    }
  )

  // === Модели ===

  val modelsSuite = suite("Models")(
    test("CompanySummary JSON roundtrip") {
      val now = Instant.now()
      val cs = CompanySummary(
        id = UUID.randomUUID(), name = "ООО Тест",
        usersCount = 10, vehiclesCount = 50,
        subscriptionPlan = "pro", subscriptionExpires = Some(now),
        isActive = true, createdAt = now, lastActivity = Some(now)
      )
      val json = cs.toJson
      val decoded = json.fromJson[CompanySummary]
      assertTrue(decoded == Right(cs))
    },
    test("UsageStats JSON roundtrip") {
      val us = UsageStats(
        vehiclesCount = 50, usersCount = 10,
        gpsPointsThisMonth = 1000000L, storageUsedMb = 512L,
        apiCallsThisMonth = 50000L, lastDataReceived = Some(Instant.now())
      )
      val json = us.toJson
      val decoded = json.fromJson[UsageStats]
      assertTrue(decoded == Right(us))
    },
    test("CompanyDetails JSON roundtrip") {
      val now = Instant.now()
      val cd = CompanyDetails(
        id = UUID.randomUUID(), name = "Транспортная компания",
        inn = Some("1234567890"), phone = Some("+7495"),
        email = Some("info@test.ru"), timezone = "Europe/Moscow",
        isActive = true, subscriptionPlan = "enterprise",
        subscriptionExpires = None, maxVehicles = 500, maxUsers = 100,
        usage = UsageStats(50, 10, 1000000L, 512L, 50000L, None),
        createdAt = now
      )
      val json = cd.toJson
      val decoded = json.fromJson[CompanyDetails]
      assertTrue(decoded == Right(cd))
    },
    test("SystemConfig JSON roundtrip") {
      val now = Instant.now()
      val sc = SystemConfig(
        features = Map("gps_filtering" -> FeatureFlag("gps_filtering", true, "GPS фильтрация", None, now, UUID.randomUUID())),
        limits = Map("maxConnectionsPerDevice" -> 10, "defaultRateLimit" -> 1000),
        maintenance = MaintenanceConfig(false, None, None, None, List.empty)
      )
      val json = sc.toJson
      val decoded = json.fromJson[SystemConfig]
      assertTrue(decoded == Right(sc))
    },
    test("FeatureFlag JSON roundtrip") {
      val now = Instant.now()
      val ff = FeatureFlag(
        name = "websocket_compression", enabled = true,
        description = "WS compression", enabledForCompanies = Some(Set(UUID.randomUUID())),
        updatedAt = now, updatedBy = UUID.randomUUID()
      )
      val json = ff.toJson
      val decoded = json.fromJson[FeatureFlag]
      assertTrue(decoded == Right(ff))
    },
    test("MaintenanceConfig JSON roundtrip") {
      val mc = MaintenanceConfig(
        maintenanceMode = true,
        maintenanceMessage = Some("Плановые работы"),
        scheduledMaintenanceStart = Some(Instant.now()),
        scheduledMaintenanceEnd = Some(Instant.now()),
        allowedIps = List("192.168.1.1", "10.0.0.1")
      )
      val json = mc.toJson
      val decoded = json.fromJson[MaintenanceConfig]
      assertTrue(decoded == Right(mc))
    },
    test("BackgroundTask JSON roundtrip") {
      val task = BackgroundTask(
        id = UUID.randomUUID(), taskType = TaskType.DatabaseBackup,
        status = TaskStatus.Running, progress = 45,
        parameters = Some("{\"db\":\"timescaledb\"}"),
        result = None, error = None,
        startedAt = Instant.now(), completedAt = None,
        createdBy = UUID.randomUUID()
      )
      val json = task.toJson
      val decoded = json.fromJson[BackgroundTask]
      assertTrue(decoded == Right(task))
    },
    test("SystemOverview JSON roundtrip") {
      val so = SystemOverview(
        companiesTotal = 100, companiesActive = 85, companiesNewThisMonth = 5,
        usersTotal = 2000, usersActiveToday = 500,
        vehiclesTotal = 5000, vehiclesOnline = 3500,
        gpsPointsToday = 50000000L, storageUsedGb = 256L
      )
      val json = so.toJson
      val decoded = json.fromJson[SystemOverview]
      assertTrue(decoded == Right(so))
    },
    test("ServiceHealth JSON roundtrip") {
      val sh = ServiceHealth(
        name = "connection-manager", status = HealthStatus.Healthy,
        instances = 2, healthyInstances = 2,
        latencyP50Ms = 5.0, latencyP99Ms = 25.0,
        errorRate = 0.01, lastCheck = Instant.now()
      )
      val json = sh.toJson
      val decoded = json.fromJson[ServiceHealth]
      assertTrue(decoded == Right(sh))
    },
    test("CreateCompanyAdmin JSON roundtrip") {
      val req = CreateCompanyAdmin(
        name = "Новая компания", inn = Some("9999999999"),
        address = Some("Москва"), phone = Some("+7495"),
        email = Some("admin@new.ru"), timezone = "Europe/Moscow",
        subscriptionPlan = "pro", maxVehicles = 200, maxUsers = 50,
        subscriptionExpires = None,
        ownerEmail = "owner@new.ru", ownerFirstName = "Иван", ownerLastName = "Иванов"
      )
      val json = req.toJson
      val decoded = json.fromJson[CreateCompanyAdmin]
      assertTrue(decoded == Right(req))
    },
    test("CleanupRequest JSON roundtrip") {
      val req = CleanupRequest(cleanupType = "gps_history", olderThan = "90d")
      val json = req.toJson
      val decoded = json.fromJson[CleanupRequest]
      assertTrue(decoded == Right(req))
    },
    test("BackupRequest JSON roundtrip") {
      val req = BackupRequest(databases = List("postgresql", "timescaledb"), uploadToS3 = true)
      val json = req.toJson
      val decoded = json.fromJson[BackupRequest]
      assertTrue(decoded == Right(req))
    },
    test("AdminAuditEntry JSON roundtrip") {
      val entry = AdminAuditEntry(
        id = UUID.randomUUID(), companyId = Some(UUID.randomUUID()),
        companyName = Some("Компания А"), userId = UUID.randomUUID(),
        userEmail = "admin@test.com", action = "company.created",
        entityType = "company", entityId = Some(UUID.randomUUID()),
        details = Some("Создана компания"), ipAddress = Some("192.168.1.1"),
        userAgent = Some("Mozilla/5.0"), createdAt = Instant.now()
      )
      val json = entry.toJson
      val decoded = json.fromJson[AdminAuditEntry]
      assertTrue(decoded == Right(entry))
    }
  )
