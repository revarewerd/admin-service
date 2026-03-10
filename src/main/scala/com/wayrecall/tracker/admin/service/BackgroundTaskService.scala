package com.wayrecall.tracker.admin.service

import com.wayrecall.tracker.admin.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import zio.*
import zio.interop.catz.*
import zio.json.*
import java.time.Instant
import java.util.UUID

// ============================================================
// BackgroundTaskService — управление фоновыми задачами
// Создание, мониторинг, отмена задач (backup, cleanup и т.д.)
// ============================================================

trait BackgroundTaskService:
  /** Список задач */
  def listTasks(statusFilter: Option[String], typeFilter: Option[String]): Task[List[BackgroundTask]]
  /** Получить задачу по ID */
  def getTask(id: UUID): Task[BackgroundTask]
  /** Отменить задачу */
  def cancelTask(id: UUID): Task[Unit]
  /** Создать задачу очистки */
  def scheduleCleanup(request: CleanupRequest, actorId: UUID): Task[UUID]
  /** Создать задачу бэкапа */
  def scheduleBackup(request: BackupRequest, actorId: UUID): Task[UUID]

final case class BackgroundTaskServiceLive(xa: Transactor[Task]) extends BackgroundTaskService:

  given Read[BackgroundTask] = Read[(UUID, String, String, Int, Option[String],
    Option[String], Option[String], Instant, Option[Instant], UUID)].map {
    case (id, tType, status, progress, params, result, error, started, completed, createdBy) =>
      BackgroundTask(id, TaskType.valueOf(tType), TaskStatus.valueOf(status),
        progress, params, result, error, started, completed, createdBy)
  }

  override def listTasks(statusFilter: Option[String], typeFilter: Option[String]): Task[List[BackgroundTask]] =
    val where = fr"WHERE 1=1" ++
      statusFilter.fold(Fragment.empty)(s => fr"AND status = $s") ++
      typeFilter.fold(Fragment.empty)(t => fr"AND task_type = $t")

    val q = fr"""
      SELECT id, task_type, status, progress, parameters::text, result, error,
             started_at, completed_at, created_by
      FROM admin.background_tasks
    """ ++ where ++ fr"ORDER BY started_at DESC LIMIT 100"

    q.query[BackgroundTask].to[List].transact(xa)

  override def getTask(id: UUID): Task[BackgroundTask] =
    sql"""
      SELECT id, task_type, status, progress, parameters::text, result, error,
             started_at, completed_at, created_by
      FROM admin.background_tasks WHERE id = $id
    """.query[BackgroundTask].unique.transact(xa)
      .catchAll(_ => ZIO.fail(AdminError.TaskNotFound(id)))

  override def cancelTask(id: UUID): Task[Unit] =
    for {
      _ <- ZIO.logWarning(s"Отмена задачи: $id")
      _ <- sql"""UPDATE admin.background_tasks SET status = 'Cancelled' WHERE id = $id AND status IN ('Pending', 'Running')"""
        .update.run.transact(xa)
      _ <- ZIO.logInfo(s"Задача отменена: $id")
    } yield ()

  override def scheduleCleanup(request: CleanupRequest, actorId: UUID): Task[UUID] =
    ZIO.logInfo(s"Запланирована очистка: type=${request.cleanupType}, olderThan=${request.olderThan}, actor=$actorId") *>
    createTask(TaskType.DataCleanup, request.toJson, actorId)

  override def scheduleBackup(request: BackupRequest, actorId: UUID): Task[UUID] =
    ZIO.logInfo(s"Запланирован бэкап: databases=${request.databases.mkString(",")}, s3=${request.uploadToS3}, actor=$actorId") *>
    createTask(TaskType.DatabaseBackup, request.toJson, actorId)

  /** Создание записи задачи в БД */
  private def createTask(taskType: TaskType, params: String, actorId: UUID): Task[UUID] =
    val taskId = UUID.randomUUID()
    (for {
      _ <- sql"""
        INSERT INTO admin.background_tasks (id, task_type, status, progress, parameters, started_at, created_by)
        VALUES ($taskId, ${taskType.toString}, 'Pending', 0, $params::jsonb, NOW(), $actorId)
      """.update.run.transact(xa)
      _ <- ZIO.logInfo(s"Задача создана: id=$taskId, type=$taskType, actor=$actorId")
    } yield taskId)


object BackgroundTaskService:
  val live: ZLayer[Transactor[Task], Nothing, BackgroundTaskService] =
    ZLayer.fromFunction(BackgroundTaskServiceLive(_))
