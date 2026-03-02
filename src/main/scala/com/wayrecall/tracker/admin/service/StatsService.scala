package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import zio.*
import zio.interop.catz.*

// ============================================================
// StatsService — общая статистика системы
// Агрегация данных из всех БД
// ============================================================

trait StatsService:
  /** Общая статистика */
  def getOverview: Task[SystemOverview]

final case class StatsServiceLive(xa: Transactor[Task]) extends StatsService:

  override def getOverview: Task[SystemOverview] =
    val q = sql"""
      SELECT
        (SELECT COUNT(*)::int FROM users.companies) as companies_total,
        (SELECT COUNT(*)::int FROM users.companies WHERE is_active = TRUE) as companies_active,
        (SELECT COUNT(*)::int FROM users.companies WHERE created_at >= date_trunc('month', NOW())) as companies_new,
        (SELECT COUNT(*)::int FROM users.users) as users_total,
        (SELECT COUNT(*)::int FROM users.users WHERE last_login_at >= NOW() - INTERVAL '1 day') as users_active_today,
        0::int as vehicles_total,
        0::int as vehicles_online,
        0::bigint as gps_points_today,
        0::bigint as storage_gb
    """
    q.query[SystemOverview].unique.transact(xa)
      .catchAll { e =>
        ZIO.logError(s"Ошибка получения статистики: ${e.getMessage}") *>
        ZIO.succeed(SystemOverview(0, 0, 0, 0, 0, 0, 0, 0, 0))
      }

object StatsService:
  val live: ZLayer[Transactor[Task], Nothing, StatsService] =
    ZLayer.fromFunction(StatsServiceLive(_))
