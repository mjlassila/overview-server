package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder


/**
 * Creates the tasks for generating `Document`s from uploaded files.
 */
trait CreateDocumentsJobShepherd extends JobShepherd {
  val documentSetId: Long
  val fileGroupId: Long
  val splitDocuments: Boolean
  val taskQueue: ActorRef
  val progressReporter: ActorRef

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val tasks = uploadedFilesInFileGroup(fileGroupId).map(CreatePagesTask(documentSetId, fileGroupId, _))

    progressReporter ! StartJob(documentSetId, tasks.size)

    taskQueue ! AddTasks(tasks)
    tasks
  }

  // TODO: we need a unified progress reporting mechanism, but for now, do this ugly thing,
  // since progress reporting only applies to these tasks.
  // Risk the MatchError because this shepherd should only get known tasks
  override def startTask(task: TaskWorkerTask): Unit = {
    super.startTask(task)
    task match {
      case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) =>
        progressReporter ! StartTask(documentSetId, uploadedFileId)
      case _ =>
    }
  }
  override def completeTask(task: TaskWorkerTask): Unit = {
    super.completeTask(task)
    task match {
      case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) => {
        progressReporter ! CompleteTask(documentSetId, uploadedFileId)
        if (allTasksComplete && !jobCancelled) {
          val createDocumentsTask = CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments)     
          taskQueue ! AddTasks(Set(createDocumentsTask))
          addTask(createDocumentsTask)
        }
      }
      case CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments) => 
    }
  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  protected val storage: Storage
  protected trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
  }
}

object CreateDocumentsJobShepherd {
  def apply(documentSetId: Long, fileGroupId: Long, splitDocuments: Boolean, taskQueue: ActorRef, progressReporter: ActorRef): CreateDocumentsJobShepherd =
    new CreateDocumentsJobShepherdImpl(documentSetId, fileGroupId, splitDocuments, taskQueue, progressReporter)

  private class CreateDocumentsJobShepherdImpl(
      val documentSetId: Long,
      val fileGroupId: Long,
      val splitDocuments: Boolean,
      val taskQueue: ActorRef,
      val progressReporter: ActorRef) extends CreateDocumentsJobShepherd {

    override protected val storage = new DatabaseStorage

    protected class DatabaseStorage extends Storage {
      override def uploadedFileIds(fileGroupId: Long): Set[Long] = Database.inTransaction {
        GroupedFileUploadFinder.byFileGroup(fileGroupId).toIds.toSet
      }

    }
  }
}
