package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import zio.*
import zio.test.*
import java.util.UUID

// ============================================================
// Тесты BackgroundTaskService — in-memory реализация
// Без реальной БД — тестируем логику через InMemory хранилище
// ============================================================

object BackgroundTaskServiceSpec extends ZIOSpecDefault:

  // In-memory реализация для тестирования
  final case class InMemoryBackgroundTaskService(
    store: Ref[Map[UUID, BackgroundTask]]
  ) extends BackgroundTaskService:

    override def listTasks(statusFilter: Option[String], typeFilter: Option[String]): Task[List[BackgroundTask]] =
      store.get.map { tasks =>
        tasks.values.toList
          .filter(t => statusFilter.fold(true)(s => t.status.toString == s))
          .filter(t => typeFilter.fold(true)(tp => t.taskType.toString == tp))
          .sortBy(_.startedAt)(using Ordering[java.time.Instant].reverse)
      }

    override def getTask(id: UUID): Task[BackgroundTask] =
      store.get.flatMap { tasks =>
        ZIO.fromOption(tasks.get(id)).orElseFail(AdminError.TaskNotFound(id))
      }

    override def cancelTask(id: UUID): Task[Unit] =
      store.update { tasks =>
        tasks.updatedWith(id) {
          case Some(t) if t.status == TaskStatus.Pending || t.status == TaskStatus.Running =>
            Some(t.copy(status = TaskStatus.Cancelled))
          case other => other
        }
      }

    override def scheduleCleanup(request: CleanupRequest, actorId: UUID): Task[UUID] =
      createTestTask(TaskType.DataCleanup, actorId)

    override def scheduleBackup(request: BackupRequest, actorId: UUID): Task[UUID] =
      createTestTask(TaskType.DatabaseBackup, actorId)

    private def createTestTask(taskType: TaskType, actorId: UUID): Task[UUID] =
      for {
        now    <- Clock.instant
        taskId = UUID.randomUUID()
        task   = BackgroundTask(taskId, taskType, TaskStatus.Pending, 0, None, None, None, now, None, actorId)
        _      <- store.update(_ + (taskId -> task))
      } yield taskId

  object InMemoryBackgroundTaskService:
    val live: ZLayer[Any, Nothing, BackgroundTaskService] =
      ZLayer(Ref.make(Map.empty[UUID, BackgroundTask]).map(InMemoryBackgroundTaskService(_)))

  private val testLayer = InMemoryBackgroundTaskService.live

  def spec = suite("BackgroundTaskService")(
    scheduleSpec,
    getTaskSpec,
    listTasksSpec,
    cancelTaskSpec
  ) @@ TestAspect.timeout(60.seconds)

  val scheduleSpec = suite("schedule")(
    test("scheduleCleanup создаёт задачу со статусом Pending") {
      val actorId = UUID.randomUUID()
      val request = CleanupRequest("old_gps_data", "30d")
      for {
        service <- ZIO.service[BackgroundTaskService]
        taskId  <- service.scheduleCleanup(request, actorId)
        task    <- service.getTask(taskId)
      } yield assertTrue(
        task.id == taskId,
        task.taskType == TaskType.DataCleanup,
        task.status == TaskStatus.Pending,
        task.progress == 0,
        task.createdBy == actorId
      )
    }.provide(testLayer),

    test("scheduleBackup создаёт задачу типа DatabaseBackup") {
      val actorId = UUID.randomUUID()
      val request = BackupRequest(List("postgresql", "timescaledb"), uploadToS3 = true)
      for {
        service <- ZIO.service[BackgroundTaskService]
        taskId  <- service.scheduleBackup(request, actorId)
        task    <- service.getTask(taskId)
      } yield assertTrue(
        task.taskType == TaskType.DatabaseBackup,
        task.status == TaskStatus.Pending
      )
    }.provide(testLayer),

    test("несколько задач создаются с уникальными ID") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[BackgroundTaskService]
        id1     <- service.scheduleCleanup(CleanupRequest("logs", "7d"), actorId)
        id2     <- service.scheduleBackup(BackupRequest(List("pg"), false), actorId)
        id3     <- service.scheduleCleanup(CleanupRequest("temp", "1d"), actorId)
      } yield assertTrue(
        id1 != id2,
        id2 != id3,
        id1 != id3
      )
    }.provide(testLayer)
  )

  val getTaskSpec = suite("getTask")(
    test("getTask — существующая задача") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[BackgroundTaskService]
        taskId  <- service.scheduleCleanup(CleanupRequest("data", "30d"), actorId)
        task    <- service.getTask(taskId)
      } yield assertTrue(task.id == taskId)
    }.provide(testLayer),

    test("getTask — несуществующая → TaskNotFound") {
      for {
        service <- ZIO.service[BackgroundTaskService]
        result  <- service.getTask(UUID.randomUUID()).either
      } yield assertTrue(
        result.isLeft,
        result.left.exists(_.isInstanceOf[AdminError.TaskNotFound])
      )
    }.provide(testLayer)
  )

  val listTasksSpec = suite("listTasks")(
    test("listTasks — все задачи без фильтров") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[BackgroundTaskService]
        _       <- service.scheduleCleanup(CleanupRequest("a", "1d"), actorId)
        _       <- service.scheduleBackup(BackupRequest(List("pg"), false), actorId)
        tasks   <- service.listTasks(None, None)
      } yield assertTrue(tasks.length == 2)
    }.provide(testLayer),

    test("listTasks — фильтр по типу") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[BackgroundTaskService]
        _       <- service.scheduleCleanup(CleanupRequest("a", "1d"), actorId)
        _       <- service.scheduleBackup(BackupRequest(List("pg"), false), actorId)
        tasks   <- service.listTasks(None, Some("DataCleanup"))
      } yield assertTrue(
        tasks.length == 1,
        tasks.head.taskType == TaskType.DataCleanup
      )
    }.provide(testLayer),

    test("listTasks — фильтр по статусу") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[BackgroundTaskService]
        _       <- service.scheduleCleanup(CleanupRequest("a", "1d"), actorId)
        tasks   <- service.listTasks(Some("Pending"), None)
      } yield assertTrue(
        tasks.nonEmpty,
        tasks.forall(_.status == TaskStatus.Pending)
      )
    }.provide(testLayer)
  )

  val cancelTaskSpec = suite("cancelTask")(
    test("отмена Pending задачи — статус Cancelled") {
      val actorId = UUID.randomUUID()
      for {
        service <- ZIO.service[BackgroundTaskService]
        taskId  <- service.scheduleCleanup(CleanupRequest("a", "1d"), actorId)
        _       <- service.cancelTask(taskId)
        task    <- service.getTask(taskId)
      } yield assertTrue(task.status == TaskStatus.Cancelled)
    }.provide(testLayer),

    test("отмена несуществующей задачи — не падает") {
      for {
        service <- ZIO.service[BackgroundTaskService]
        _       <- service.cancelTask(UUID.randomUUID())
      } yield assertTrue(true) // Не упало = успех
    }.provide(testLayer)
  )
