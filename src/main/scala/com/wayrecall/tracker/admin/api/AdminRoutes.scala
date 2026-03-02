package com.wayrecall.tracker.admin.api

import com.wayrecall.tracker.admin.domain.*
import com.wayrecall.tracker.admin.service.*
import zio.*
import zio.http.*
import zio.json.*
import java.time.Instant
import java.util.UUID

// ============================================================
// REST API маршруты Admin Service
// Все эндпоинты — только для super_admin
// ============================================================

object AdminRoutes:

  val routes: Routes[CompanyAdminService & SystemMonitorService & ConfigService &
                     AdminAuditService & BackgroundTaskService & StatsService, Nothing] = Routes(

    // ===========================
    // Компании
    // ===========================

    // GET /api/v1/admin/companies — список компаний
    Method.GET / "api" / "v1" / "admin" / "companies" -> handler { (req: Request) =>
      val filters = CompanyFilters(
        search = req.url.queryParams.get("search"),
        isActive = req.url.queryParams.get("isActive").map(_.toBoolean),
        plan = req.url.queryParams.get("plan"),
        page = req.url.queryParams.get("page").flatMap(_.toIntOption).getOrElse(1),
        pageSize = req.url.queryParams.get("pageSize").flatMap(_.toIntOption).getOrElse(20)
      )
      ZIO.serviceWithZIO[CompanyAdminService](_.listCompanies(filters))
        .map(p => Response.json(p.toJson))
        .catchAll(handleError)
    },

    // GET /api/v1/admin/companies/:id — детали компании
    Method.GET / "api" / "v1" / "admin" / "companies" / string("companyId") -> handler { (companyId: String, _: Request) =>
      val id = UUID.fromString(companyId)
      ZIO.serviceWithZIO[CompanyAdminService](_.getCompanyDetails(id))
        .map(d => Response.json(d.toJson))
        .catchAll(handleError)
    },

    // POST /api/v1/admin/companies — создать компанию
    Method.POST / "api" / "v1" / "admin" / "companies" -> handler { (req: Request) =>
      val actorId = extractUserId(req)
      (for {
        body    <- req.body.asString
        request <- ZIO.fromEither(body.fromJson[CreateCompanyAdmin]).mapError(e => new RuntimeException(e))
        result  <- ZIO.serviceWithZIO[CompanyAdminService](_.createCompany(request, actorId))
      } yield Response.json(s"""{"companyId":"${result._1}","ownerId":"${result._2}"}""").status(Status.Created))
        .catchAll(handleError)
    },

    // POST /api/v1/admin/companies/:id/deactivate
    Method.POST / "api" / "v1" / "admin" / "companies" / string("companyId") / "deactivate" -> handler {
      (companyId: String, req: Request) =>
        val id = UUID.fromString(companyId)
        ZIO.serviceWithZIO[CompanyAdminService](_.deactivateCompany(id, "Admin action"))
          .as(Response.ok)
          .catchAll(handleError)
    },

    // POST /api/v1/admin/companies/:id/activate
    Method.POST / "api" / "v1" / "admin" / "companies" / string("companyId") / "activate" -> handler {
      (companyId: String, _: Request) =>
        val id = UUID.fromString(companyId)
        ZIO.serviceWithZIO[CompanyAdminService](_.activateCompany(id))
          .as(Response.ok)
          .catchAll(handleError)
    },

    // PUT /api/v1/admin/companies/:id/subscription
    Method.PUT / "api" / "v1" / "admin" / "companies" / string("companyId") / "subscription" -> handler {
      (companyId: String, req: Request) =>
        val id = UUID.fromString(companyId)
        (for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[UpdateSubscription]).mapError(e => new RuntimeException(e))
          _       <- ZIO.serviceWithZIO[CompanyAdminService](_.updateSubscription(id, request))
        } yield Response.ok)
          .catchAll(handleError)
    },

    // ===========================
    // Мониторинг
    // ===========================

    // GET /api/v1/admin/system/health — здоровье системы
    Method.GET / "api" / "v1" / "admin" / "system" / "health" -> handler { (_: Request) =>
      ZIO.serviceWithZIO[SystemMonitorService](_.getHealth)
        .map(h => Response.json(h.toJson))
        .catchAll(handleError)
    },

    // GET /api/v1/admin/system/services — статус сервисов
    Method.GET / "api" / "v1" / "admin" / "system" / "services" -> handler { (_: Request) =>
      ZIO.serviceWithZIO[SystemMonitorService](_.getServicesHealth)
        .map(s => Response.json(s.toJson))
        .catchAll(handleError)
    },

    // ===========================
    // Конфигурация
    // ===========================

    // GET /api/v1/admin/config — конфигурация
    Method.GET / "api" / "v1" / "admin" / "config" -> handler { (_: Request) =>
      ZIO.serviceWithZIO[ConfigService](_.getConfig)
        .map(c => Response.json(c.toJson))
        .catchAll(handleError)
    },

    // PUT /api/v1/admin/config/features/:name — обновить feature flag
    Method.PUT / "api" / "v1" / "admin" / "config" / "features" / string("featureName") -> handler {
      (featureName: String, req: Request) =>
        val actorId = extractUserId(req)
        (for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[FeatureFlagUpdate]).mapError(e => new RuntimeException(e))
          _       <- ZIO.serviceWithZIO[ConfigService](_.updateFeatureFlag(featureName, request, actorId))
        } yield Response.ok)
          .catchAll(handleError)
    },

    // POST /api/v1/admin/config/maintenance/enable
    Method.POST / "api" / "v1" / "admin" / "config" / "maintenance" / "enable" -> handler { (req: Request) =>
      val actorId = extractUserId(req)
      (for {
        body    <- req.body.asString
        request <- ZIO.fromEither(body.fromJson[EnableMaintenanceRequest]).mapError(e => new RuntimeException(e))
        _       <- ZIO.serviceWithZIO[ConfigService](_.enableMaintenanceMode(request, actorId))
      } yield Response.ok)
        .catchAll(handleError)
    },

    // POST /api/v1/admin/config/maintenance/disable
    Method.POST / "api" / "v1" / "admin" / "config" / "maintenance" / "disable" -> handler { (req: Request) =>
      ZIO.serviceWithZIO[ConfigService](_.disableMaintenanceMode(extractUserId(req)))
        .as(Response.ok)
        .catchAll(handleError)
    },

    // ===========================
    // Аудит
    // ===========================

    // GET /api/v1/admin/audit — лог аудита
    Method.GET / "api" / "v1" / "admin" / "audit" -> handler { (req: Request) =>
      val filters = AdminAuditFilters(
        companyId = req.url.queryParams.get("companyId").flatMap(s => scala.util.Try(UUID.fromString(s)).toOption),
        userId = req.url.queryParams.get("userId").flatMap(s => scala.util.Try(UUID.fromString(s)).toOption),
        action = req.url.queryParams.get("action"),
        entityType = req.url.queryParams.get("entityType"),
        fromDate = req.url.queryParams.get("fromDate").flatMap(s => scala.util.Try(Instant.parse(s)).toOption),
        toDate = req.url.queryParams.get("toDate").flatMap(s => scala.util.Try(Instant.parse(s)).toOption),
        page = req.url.queryParams.get("page").flatMap(_.toIntOption).getOrElse(1),
        pageSize = req.url.queryParams.get("pageSize").flatMap(_.toIntOption).getOrElse(20)
      )
      ZIO.serviceWithZIO[AdminAuditService](_.getAuditLog(filters))
        .map(p => Response.json(p.toJson))
        .catchAll(handleError)
    },

    // ===========================
    // Фоновые задачи
    // ===========================

    // GET /api/v1/admin/tasks — список задач
    Method.GET / "api" / "v1" / "admin" / "tasks" -> handler { (req: Request) =>
      val statusFilter = req.url.queryParams.get("status")
      val typeFilter   = req.url.queryParams.get("type")
      ZIO.serviceWithZIO[BackgroundTaskService](_.listTasks(statusFilter, typeFilter))
        .map(tasks => Response.json(tasks.toJson))
        .catchAll(handleError)
    },

    // GET /api/v1/admin/tasks/:id — статус задачи
    Method.GET / "api" / "v1" / "admin" / "tasks" / string("taskId") -> handler { (taskId: String, _: Request) =>
      val id = UUID.fromString(taskId)
      ZIO.serviceWithZIO[BackgroundTaskService](_.getTask(id))
        .map(t => Response.json(t.toJson))
        .catchAll(handleError)
    },

    // POST /api/v1/admin/tasks/:id/cancel — отменить задачу
    Method.POST / "api" / "v1" / "admin" / "tasks" / string("taskId") / "cancel" -> handler {
      (taskId: String, _: Request) =>
        val id = UUID.fromString(taskId)
        ZIO.serviceWithZIO[BackgroundTaskService](_.cancelTask(id))
          .as(Response.ok)
          .catchAll(handleError)
    },

    // POST /api/v1/admin/tasks/cleanup — очистка
    Method.POST / "api" / "v1" / "admin" / "tasks" / "cleanup" -> handler { (req: Request) =>
      val actorId = extractUserId(req)
      (for {
        body    <- req.body.asString
        request <- ZIO.fromEither(body.fromJson[CleanupRequest]).mapError(e => new RuntimeException(e))
        taskId  <- ZIO.serviceWithZIO[BackgroundTaskService](_.scheduleCleanup(request, actorId))
      } yield Response.json(TaskCreated(taskId).toJson).status(Status.Accepted))
        .catchAll(handleError)
    },

    // POST /api/v1/admin/tasks/backup — бэкап
    Method.POST / "api" / "v1" / "admin" / "tasks" / "backup" -> handler { (req: Request) =>
      val actorId = extractUserId(req)
      (for {
        body    <- req.body.asString
        request <- ZIO.fromEither(body.fromJson[BackupRequest]).mapError(e => new RuntimeException(e))
        taskId  <- ZIO.serviceWithZIO[BackgroundTaskService](_.scheduleBackup(request, actorId))
      } yield Response.json(TaskCreated(taskId).toJson).status(Status.Accepted))
        .catchAll(handleError)
    },

    // ===========================
    // Статистика
    // ===========================

    // GET /api/v1/admin/stats/overview — общая статистика
    Method.GET / "api" / "v1" / "admin" / "stats" / "overview" -> handler { (_: Request) =>
      ZIO.serviceWithZIO[StatsService](_.getOverview)
        .map(s => Response.json(s.toJson))
        .catchAll(handleError)
    }
  )

  // === Вспомогательные ===

  private def extractUserId(req: Request): UUID =
    req.headers.get("X-User-Id")
      .flatMap(v => scala.util.Try(UUID.fromString(v)).toOption)
      .getOrElse(UUID.randomUUID())

  private def handleError(error: Throwable): UIO[Response] = error match
    case e: AdminError.Forbidden =>
      ZIO.succeed(Response.json(AdminError.ErrorResponse("forbidden", e.message).toJson).status(Status.Forbidden))
    case e: AdminError.CompanyNotFound =>
      ZIO.succeed(Response.json(AdminError.ErrorResponse("not_found", e.message).toJson).status(Status.NotFound))
    case e: AdminError.TaskNotFound =>
      ZIO.succeed(Response.json(AdminError.ErrorResponse("not_found", e.message).toJson).status(Status.NotFound))
    case e: AdminError.InvalidConfiguration =>
      ZIO.succeed(Response.json(AdminError.ErrorResponse("bad_request", e.message).toJson).status(Status.BadRequest))
    case AdminError.MaintenanceModeActive =>
      ZIO.succeed(Response.json(AdminError.ErrorResponse("service_unavailable", "Maintenance mode").toJson).status(Status.ServiceUnavailable))
    case e =>
      ZIO.logError(s"Неожиданная ошибка: ${e.getMessage}") *>
        ZIO.succeed(Response.json(AdminError.ErrorResponse("internal_error", "Внутренняя ошибка").toJson).status(Status.InternalServerError))
