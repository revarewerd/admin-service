package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import zio.*
import zio.interop.catz.*
import java.util.UUID

// ============================================================
// CompanyAdminService — CRUD компаний для суперадмина
// ============================================================

trait CompanyAdminService:
  /** Список компаний с фильтрами */
  def listCompanies(filters: CompanyFilters): Task[Page[CompanySummary]]
  /** Детали компании */
  def getCompanyDetails(id: UUID): Task[CompanyDetails]
  /** Создать компанию с владельцем */
  def createCompany(request: CreateCompanyAdmin, actorId: UUID): Task[(UUID, UUID)]
  /** Обновить подписку компании */
  def updateSubscription(companyId: UUID, request: UpdateSubscription): Task[Unit]
  /** Активировать компанию */
  def activateCompany(companyId: UUID): Task[Unit]
  /** Деактивировать компанию */
  def deactivateCompany(companyId: UUID, reason: String): Task[Unit]

final case class CompanyAdminServiceLive(xa: Transactor[Task]) extends CompanyAdminService:

  override def listCompanies(filters: CompanyFilters): Task[Page[CompanySummary]] =
    val offset = (filters.page - 1) * filters.pageSize

    // Базовый запрос — читаем из схемы users (User Service БД)
    val baseWhere = fr"WHERE 1=1" ++
      filters.search.fold(Fragment.empty)(s => fr"AND c.name ILIKE ${"%" + s + "%"}") ++
      filters.isActive.fold(Fragment.empty)(a => fr"AND c.is_active = $a") ++
      filters.plan.fold(Fragment.empty)(p => fr"AND c.subscription_plan = $p")

    val countQ = fr"SELECT COUNT(*) FROM users.companies c" ++ baseWhere
    val dataQ = fr"""
      SELECT c.id, c.name,
             COALESCE((SELECT COUNT(*) FROM users.users u WHERE u.company_id = c.id), 0),
             0,
             c.subscription_plan, c.subscription_expires_at,
             c.is_active, c.created_at, NULL::timestamptz
      FROM users.companies c
    """ ++ baseWhere ++ fr"ORDER BY c.created_at DESC LIMIT ${filters.pageSize} OFFSET $offset"

    (for {
      total <- countQ.query[Long].unique
      items <- dataQ.query[CompanySummary].to[List]
    } yield Page(total, items, filters.page, filters.pageSize)).transact(xa)

  override def getCompanyDetails(id: UUID): Task[CompanyDetails] =
    val q = sql"""
      SELECT c.id, c.name, c.inn, c.phone, c.email, c.timezone,
             c.is_active, c.subscription_plan, c.subscription_expires_at,
             c.max_vehicles, c.max_users,
             COALESCE((SELECT COUNT(*) FROM users.users u WHERE u.company_id = c.id)::int, 0),
             COALESCE((SELECT COUNT(*) FROM users.users u WHERE u.company_id = c.id)::int, 0),
             0::bigint, 0::bigint, 0::bigint, NULL::timestamptz,
             c.created_at
      FROM users.companies c WHERE c.id = $id
    """
    q.query[(UUID, String, Option[String], Option[String], Option[String], String,
             Boolean, String, Option[java.time.Instant], Int, Int,
             Int, Int, Long, Long, Long, Option[java.time.Instant],
             java.time.Instant)]
      .unique
      .map { case (cId, name, inn, phone, email, tz, active, plan, planExp, maxV, maxU,
                   vCount, uCount, gps, storage, api, lastData, created) =>
        CompanyDetails(cId, name, inn, phone, email, tz, active, plan, planExp, maxV, maxU,
          UsageStats(vCount, uCount, gps, storage, api, lastData), created)
      }
      .transact(xa)
      .flatMap {
        case d => ZIO.succeed(d)
      }
      .catchAll(e => ZIO.fail(AdminError.CompanyNotFound(id)))

  override def createCompany(request: CreateCompanyAdmin, actorId: UUID): Task[(UUID, UUID)] =
    val companyId = UUID.randomUUID()
    val ownerId   = UUID.randomUUID()

    val insertCompany = sql"""
      INSERT INTO users.companies (id, name, inn, phone, email, timezone,
                                   subscription_plan, max_vehicles, max_users, subscription_expires_at)
      VALUES ($companyId, ${request.name}, ${request.inn}, ${request.phone}, ${request.email},
              ${request.timezone}, ${request.subscriptionPlan},
              ${request.maxVehicles}, ${request.maxUsers}, ${request.subscriptionExpires})
    """.update.run

    // Вставка владельца с временным паролем (реальная интеграция через User Service API)
    val insertOwner = sql"""
      INSERT INTO users.users (id, company_id, email, password_hash, first_name, last_name)
      VALUES ($ownerId, $companyId, ${request.ownerEmail}, 'TEMP_HASH_CHANGE_ON_LOGIN',
              ${request.ownerFirstName}, ${request.ownerLastName})
    """.update.run

    // Назначаем роль company_admin
    val assignRole = sql"""
      INSERT INTO users.user_roles (user_id, role_id, assigned_by)
      VALUES ($ownerId, '00000000-0000-0000-0000-000000000002'::uuid, $actorId)
    """.update.run

    (insertCompany *> insertOwner *> assignRole).transact(xa).as((companyId, ownerId))

  override def updateSubscription(companyId: UUID, request: UpdateSubscription): Task[Unit] =
    sql"""
      UPDATE users.companies
      SET subscription_plan = ${request.plan},
          max_vehicles = ${request.maxVehicles},
          max_users = ${request.maxUsers},
          subscription_expires_at = ${request.expiresAt},
          updated_at = NOW()
      WHERE id = $companyId
    """.update.run.transact(xa).unit

  override def activateCompany(companyId: UUID): Task[Unit] =
    sql"""UPDATE users.companies SET is_active = TRUE, updated_at = NOW() WHERE id = $companyId"""
      .update.run.transact(xa).unit

  override def deactivateCompany(companyId: UUID, reason: String): Task[Unit] =
    sql"""UPDATE users.companies SET is_active = FALSE, updated_at = NOW() WHERE id = $companyId"""
      .update.run.transact(xa).unit

object CompanyAdminService:
  val live: ZLayer[Transactor[Task], Nothing, CompanyAdminService] =
    ZLayer.fromFunction(CompanyAdminServiceLive(_))
