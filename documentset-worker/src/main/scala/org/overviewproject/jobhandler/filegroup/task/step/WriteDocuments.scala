package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.models.Document
import org.overviewproject.models.TempDocumentSetFile
import org.overviewproject.util.BulkDocumentWriter
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.searchindex.TransportIndexClient

trait WriteDocuments extends TaskStep {

  protected val storage: Storage
  protected val bulkDocumentWriter: BulkDocumentWriter
  protected val searchIndex: ElasticSearchIndexClient

  protected val documents: Seq[Document]

  override def execute: Future[TaskStep] = {
    val write = writeDocuments
    val index = indexDocuments

    for {
      writeResult <- write
      indexResult <- index
      deleted <- storage.deleteTempDocumentSetFiles(documents)
    } yield FinalStep

  }

  protected trait Storage {
    def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int]
  }

  private def writeDocuments: Future[Unit] =
    for {
      docsAdded <- Future.sequence(documents.map(bulkDocumentWriter.addAndFlushIfNeeded)) // FIXME: should be done in serial
      batchFlushed <- bulkDocumentWriter.flush
    } yield {}

  private def indexDocuments: Future[Unit] = 
    for {
      docSetIndexCreated <- withDocumentSet(searchIndex.addDocumentSet)
      docsIndex <- searchIndex.addDocuments(documents)
    } yield {}

  private def withDocumentSet(f: Long => Future[Unit]): Future[Unit] =
    documents.headOption
      .map(d => f(d.documentSetId))
      .getOrElse(Future.successful(()))
}

object WriteDocuments {

  def apply(documents: Seq[Document]): WriteDocuments = new WriteDocumentsImpl(documents)

  private class WriteDocumentsImpl(
    override protected val documents: Seq[Document]) extends WriteDocuments {

    override protected val bulkDocumentWriter = BulkDocumentWriter.forDatabaseAndSearchIndex // Thread safe?
    override protected val searchIndex = TransportIndexClient.singleton

    override protected val storage: Storage = new SlickStorage

    private class SlickStorage extends Storage with SlickSessionProvider {
      import org.overviewproject.database.Slick.simple._
      import org.overviewproject.models.tables.TempDocumentSetFiles

      override def deleteTempDocumentSetFiles(documents: Seq[Document]): Future[Int] = db { implicit session =>
        val fileIds = documents.flatMap(_.fileId)
        TempDocumentSetFiles.filter(_.fileId inSet fileIds).delete
      }
    }
  }
}