package com.wayrecall.tracker.admin.domain

import zio.json.*
import java.util.UUID

// ============================================================
// Ошибки Admin Service — типизированные через sealed trait
// ============================================================

sealed trait AdminError extends Throwable:
  def message: String
  override def getMessage: String = message

object AdminError:
  /** Доступ запрещён */
  final case class Forbidden(reason: String) extends AdminError:
    val message = s"Доступ запрещён: $reason"

  /** Компания не найдена */
  final case class CompanyNotFound(id: UUID) extends AdminError:
    val message = s"Компания не найдена: $id"

  /** Задача не найдена */
  final case class TaskNotFound(id: UUID) extends AdminError:
    val message = s"Задача не найдена: $id"

  /** Ошибка конфигурации */
  final case class InvalidConfiguration(errors: List[String]) extends AdminError:
    val message = s"Ошибка конфигурации: ${errors.mkString(", ")}"

  /** Режим обслуживания */
  case object MaintenanceModeActive extends AdminError:
    val message = "Система в режиме обслуживания"

  /** Ошибка БД */
  final case class DatabaseError(cause: String) extends AdminError:
    val message = s"Ошибка базы данных: $cause"

  /** Ответ с ошибкой (для HTTP) */
  final case class ErrorResponse(code: String, message: String) derives JsonCodec
