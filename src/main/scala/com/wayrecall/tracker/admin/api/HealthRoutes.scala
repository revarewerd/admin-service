package com.wayrecall.tracker.admin.api

import zio.*
import zio.http.*

// ============================================================
// Health Check маршруты
// ============================================================

object HealthRoutes:
  val routes: Routes[Any, Nothing] = Routes(
    Method.GET / "health" -> handler {
      Response.json("""{"status":"ok","service":"admin-service"}""")
    },
    Method.GET / "ready" -> handler {
      Response.json("""{"status":"ready","service":"admin-service"}""")
    }
  )
