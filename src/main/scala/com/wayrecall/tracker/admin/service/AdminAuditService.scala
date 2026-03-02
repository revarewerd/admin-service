package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import zio.*
import zio.interop.catz.*
import java.time.Instant
import java.util.UUID

// ============================================================
// AdminAuditService — глобальный аудит для суперадмина
// Чтение из admin.admin_audit_log
// ============================================================

trait AdminAuditService:
  /** Лог аудита с фильтрами */
  def getAuditLog(filters: AdminAuditFilters): Task[Page[AdminAuditEntry]]
  /** Записать событие аудита */
  def log(actorId: UUID, action: String, entityType: String, entityId: Option[UUID],
          details: Option[String], companyId: Option[UUID]): Task[Unit]

final case class AdminAuditServiceLive(xa: Transactor[Task]) extends AdminAuditService:

  override def getAuditLog(filters: AdminAuditFilters): Task[Page[AdminAuditEntry]] =
    val offset = (filters.page - 1) * filters.pageSize

    val where = fr"WHERE 1=1" ++
      filters.companyId.fold(Fragment.empty)(id => fr"AND a.company_id = $id") ++
      filters.userId.fold(Fragment.empty)(id => fr"AND a.user_id = $id") ++
      filters.action.fold(Fragment.empty)(act => fr"AND a.action = $act") ++
      filters.entityType.fold(Fragment.empty)(et => fr"AND a.entity_type = $et") ++
      filters.fromDate.fold(Fragment.empty)(d => fr"AND a.created_at >= $d") ++
      filters.toDate.fold(Fragment.empty)(d => fr"AND a.created_at <= $d")

    val countQ = fr"SELECT COUNT(*) FROM admin.admin_audit_log a" ++ where
    val dataQ = fr"""
      SELECT a.id, a.company_id, NULL::text, a.user_id, '',
             a.action, a.entity_type, a.entity_id,
             a.details::text, a.ip_address, a.user_agent, a.created_at
      FROM admin.admin_audit_log a
    """ ++ where ++ fr"ORDER BY a.created_at DESC LIMIT ${filters.pageSize} OFFSET $offset"

    given Read[AdminAuditEntry] = Read[(UUID, Option[UUID], Option[String], UUID, String,
      String, String, Option[UUID], Option[String], Option[String], Option[String], Instant)].map {
      case (id, cId, cName, uId, uEmail, act, eType, eId, det, ip, ua, created) =>
        AdminAuditEntry(id, cId, cName, uId, uEmail, act, eType, eId, det, ip, ua, created)
    }

    (for {
      total <- countQ.query[Long].unique
      items <- dataQ.query[AdminAuditEntry].to[List]
    } yield Page(total, items, filters.page, filters.pageSize)).transact(xa)

  override def log(actorId: UUID, action: String, entityType: String, entityId: Option[UUID],
                   details: Option[String], companyId: Option[UUID]): Task[Unit] =
    sql"""
      INSERT INTO admin.admin_audit_log (id, company_id, user_id, action, entity_type, entity_id, details)
      VALUES (${UUID.randomUUID()}, $companyId, $actorId, $action, $entityType, $entityId, $details::jsonb)
    """.update.run.transact(xa).unit

object AdminAuditService:
  val live: ZLayer[Transactor[Task], Nothing, AdminAuditService] =
    ZLayer.fromFunction(AdminAuditServiceLive(_))
