package controllers

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import org.overviewproject.models.tables.Documents
import org.overviewproject.util.ContentDisposition
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.backend.DbBackend
import models.DocumentFileInfo
import models.archive.Archive
import models.archive.ArchiveEntry
import models.archive.ArchiveEntryCollection
import models.archive.zip.ZipArchive
import play.api.mvc.Result
import controllers.backend.DocumentFileInfoBackend

trait DocumentSetArchiveController extends Controller {

  import Authorities._

  val MaxNumberOfEntries: Int = 0xFFFF // If more than 2 bytes are needed for entries, ZIP64 should be used
  val MaxArchiveSize: Long = 0xFFFFFFFFl // If more than 4 bytes are needed for size, ZIP64 should be used

  def archive(documentSetId: Long, filename: String) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    for {
      documentViewInfos <- backend.indexDocumentViewInfos(documentSetId)
    } yield {
      val archiveEntries = documentViewInfos.map(_.archiveEntry)

      if (archiveEntries.length > MaxNumberOfEntries) flashTooManyEntriesWarning
      else if (archiveEntries.nonEmpty) streamArchive(archiveEntries, filename)
      else flashUnsupportedWarning
    }
  }

  private def streamArchive(archiveEntries: Seq[ArchiveEntry], filename: String): Result = {
    val archive = archiver.createArchive(archiveEntries)

    if (archive.size > MaxArchiveSize) {
      flashArchiveTooLargeWarning
    }
    else {
      val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition

      Ok.feed(Enumerator.fromStream(archive.stream)).
        withHeaders(
          CONTENT_TYPE -> "application/x-zip-compressed",
          CONTENT_LENGTH -> s"${archive.size}",
          CONTENT_DISPOSITION -> contentDisposition)
    }
  }

  private def flashUnsupportedWarning: Result = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")

    Redirect(routes.DocumentSetController.index()).flashing("warning" -> m("unsupported"))
  }

  private def flashTooManyEntriesWarning: Result = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")

    Redirect(routes.DocumentSetController.index()).flashing("warning" -> m("tooManyEntries"))
  }

  private def flashArchiveTooLargeWarning: Result = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")

    Redirect(routes.DocumentSetController.index()).flashing("warning" -> m("archiveTooLarge"))
  }

  protected val archiver: Archiver
  protected val backend: DocumentFileInfoBackend

  protected trait Archiver {
    def createArchive(entries: Seq[ArchiveEntry]): Archive
  }
}

object DocumentSetArchiveController extends DocumentSetArchiveController {

  override protected val archiver: Archiver = new Archiver {
    override def createArchive(entries: Seq[ArchiveEntry]): Archive = {
      val validEntries = new ArchiveEntryCollection(entries)
      new ZipArchive(validEntries)
    }
  }

  override protected val backend: DocumentFileInfoBackend = DocumentFileInfoBackend
}
