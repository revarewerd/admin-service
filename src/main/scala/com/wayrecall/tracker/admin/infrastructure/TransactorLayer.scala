package com.wayrecall.tracker.admin.infrastructure

import cats.effect.IO
import com.wayrecall.tracker.admin.config.PostgresConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import zio.*
import zio.interop.catz.*

// ============================================================
// TransactorLayer — ZIO Layer для PostgreSQL через Doobie
// ============================================================

object TransactorLayer:
  val live: ZLayer[PostgresConfig, Throwable, Transactor[Task]] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[PostgresConfig]
        xa <- HikariTransactor.newHikariTransactor[Task](
          driverClassName = "org.postgresql.Driver",
          url             = config.url,
          user            = config.user,
          pass            = config.password,
          connectEC       = scala.concurrent.ExecutionContext.global
        ).toScopedZIO
      } yield xa
    }
