package com.overviewdocs.database

import scala.concurrent.Future

import com.overviewdocs.models.tables.FileGroups

/**
 * Mark the [[FileGroup]] as deleted, and hope some background process
 * does the actual cleanup, including deleting associated [[GroupedFileUpload]]s
 */
trait FileGroupDeleter extends HasDatabase {
  import database.api._

  def delete(fileGroupId: Long): Future[Unit] = {
    database.runUnit {
      FileGroups
        .filter(_.id === fileGroupId)
        .filter(_.deleted === false)
        .map(_.deleted).update(true)
    }
  }
}

object FileGroupDeleter extends FileGroupDeleter
