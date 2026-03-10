package com.wayrecall.tracker.admin.domain

import zio.*
import zio.test.*
import zio.json.*
import java.util.UUID

// ============================================================
// Тесты ошибок Admin Service — sealed trait AdminError
// ============================================================

object ErrorsSpec extends ZIOSpecDefault:

  def spec = suite("AdminError")(
    forbiddenSpec,
    companyNotFoundSpec,
    taskNotFoundSpec,
    invalidConfigSpec,
    maintenanceModeSpec,
    databaseErrorSpec,
    errorResponseSpec
  ) @@ TestAspect.timeout(60.seconds)

  val forbiddenSpec = suite("Forbidden")(
    test("содержит причину в сообщении") {
      val err = AdminError.Forbidden("нет прав суперадмина")
      assertTrue(
        err.message.contains("Доступ запрещён"),
        err.message.contains("нет прав суперадмина"),
        err.getMessage == err.message
      )
    },

    test("является AdminError и Throwable") {
      val err: AdminError = AdminError.Forbidden("test")
      val thr: Throwable = err
      assertTrue(thr.getMessage.contains("Доступ запрещён"))
    }
  )

  val companyNotFoundSpec = suite("CompanyNotFound")(
    test("содержит UUID компании") {
      val id = UUID.randomUUID()
      val err = AdminError.CompanyNotFound(id)
      assertTrue(
        err.message.contains(id.toString),
        err.message.contains("Компания не найдена")
      )
    }
  )

  val taskNotFoundSpec = suite("TaskNotFound")(
    test("содержит UUID задачи") {
      val id = UUID.randomUUID()
      val err = AdminError.TaskNotFound(id)
      assertTrue(
        err.message.contains(id.toString),
        err.message.contains("Задача не найдена")
      )
    }
  )

  val invalidConfigSpec = suite("InvalidConfiguration")(
    test("содержит все ошибки через запятую") {
      val errors = List("поле name пустое", "port вне диапазона", "timeout < 0")
      val err = AdminError.InvalidConfiguration(errors)
      assertTrue(
        err.message.contains("поле name пустое"),
        err.message.contains("port вне диапазона"),
        err.message.contains("timeout < 0")
      )
    },

    test("одна ошибка — без запятой") {
      val err = AdminError.InvalidConfiguration(List("единственная ошибка"))
      assertTrue(err.message.contains("единственная ошибка"))
    }
  )

  val maintenanceModeSpec = suite("MaintenanceModeActive")(
    test("сообщение о режиме обслуживания") {
      val err = AdminError.MaintenanceModeActive
      assertTrue(
        err.message.contains("режиме обслуживания"),
        err.getMessage.nonEmpty
      )
    }
  )

  val databaseErrorSpec = suite("DatabaseError")(
    test("содержит причину") {
      val err = AdminError.DatabaseError("connection timeout")
      assertTrue(
        err.message.contains("Ошибка базы данных"),
        err.message.contains("connection timeout")
      )
    }
  )

  val errorResponseSpec = suite("ErrorResponse")(
    test("JSON roundtrip") {
      val resp = AdminError.ErrorResponse("NOT_FOUND", "Компания не найдена")
      val json = resp.toJson
      val decoded = json.fromJson[AdminError.ErrorResponse]
      assertTrue(
        decoded == Right(resp),
        json.contains("NOT_FOUND"),
        json.contains("Компания не найдена")
      )
    },

    test("десериализация из строки") {
      val json = """{"code":"FORBIDDEN","message":"Нет доступа"}"""
      val result = json.fromJson[AdminError.ErrorResponse]
      assertTrue(
        result.isRight,
        result.toOption.get.code == "FORBIDDEN",
        result.toOption.get.message == "Нет доступа"
      )
    }
  )
