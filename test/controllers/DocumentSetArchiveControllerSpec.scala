package controllers

import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.Archive
import models.DocumentFileInfo
import models.archive.ArchiveEntry
import java.io.ByteArrayInputStream
import scala.concurrent.Future
import play.api.i18n.Messages
import controllers.backend.DocumentFileInfoBackend
import models.archive.FileViewInfo
import models.archive.PageViewInfo
import models.archive.DocumentViewInfo

class DocumentSetArchiveControllerSpec extends ControllerSpecification with Mockito {

  "DocumentSetArchiveController" should {

    "set content-type" in new DocumentSetArchiveContext {
      header(h.CONTENT_TYPE) must beSome(contentType)
    }

    "set content-length to archive size" in new DocumentSetArchiveContext {
      header(h.CONTENT_LENGTH) must beSome(s"$archiveSize")
    }

    "set content-disposition to some cool name" in new DocumentSetArchiveContext {
      header(h.CONTENT_DISPOSITION) must beSome(s"""attachment; filename="$fileName"""")
    }

    "send archive as content" in new DocumentSetArchiveContext {
      h.contentAsBytes(result) must be equalTo archiveData
    }

    "redirect if archiving is not supported" in new UnsupportedDocumentSet {
      h.status(result) must beEqualTo(h.SEE_OTHER)
    }

    "flash warning if archiving is not supported" in new UnsupportedDocumentSet {
      val unsupported = Messages("controllers.DocumentSetArchiveController.unsupported")

      h.flash(result).data must be equalTo (Map("warning" -> unsupported))
    }
  }

  trait DocumentSetArchiveContext extends Scope {
    import scala.concurrent.ExecutionContext.Implicits.global

    val documentSetId = 23
    val fileName = "documents.zip"
    val request = fakeAuthorizedRequest
    val archiveSize = 1989
    val archiveData = Array.fill(archiveSize)(0xda.toByte)

    val contentType = "application/x-zip-compressed"

    def pageViewInfos: Seq[PageViewInfo] = Seq.empty
    def fileViewInfos: Seq[FileViewInfo] = Seq.fill(5)(smartMock[FileViewInfo])

    def numberOfDocuments = 5
    def viewInfos: Seq[DocumentViewInfo] = Seq.fill(numberOfDocuments) {
      val entry = smartMock[ArchiveEntry]
      val viewInfo = smartMock[DocumentViewInfo]
      viewInfo.archiveEntry returns entry
      viewInfo
    }

    def result = {
      val controller = new TestDocumentSetArchiveController(documentSetId, archiveData, viewInfos)
      controller.archive(documentSetId, fileName)(request)
    }

    def header(key: String): Option[String] = h.header(key, result)
  }

  trait UnsupportedDocumentSet extends DocumentSetArchiveContext {
    override def numberOfDocuments = 0
  }

  class TestDocumentSetArchiveController(
      documentSetId: Long,
      archiveData: Array[Byte],
      documentViewInfos: Seq[DocumentViewInfo]) extends DocumentSetArchiveController {
    import scala.concurrent.ExecutionContext.Implicits.global

    val archiver = smartMock[Archiver]
    val archive = smartMock[Archive]

    archive.size returns archiveData.length
    archive.stream returns new ByteArrayInputStream(archiveData)

    val backend = smartMock[DocumentFileInfoBackend]
    backend.indexDocumentViewInfos(documentSetId) returns Future(documentViewInfos)

    archiver.createArchive(any) returns archive

  }
}