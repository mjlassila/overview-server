package controllers.iteratees

import akka.stream.scaladsl.{Flow,Keep,Sink}
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.backend.GroupedFileUploadBackend
import com.overviewdocs.models.GroupedFileUpload

class GroupedFileUploadIteratee(
  groupedFileUploadBackend: GroupedFileUploadBackend,
  bufferSize: Int = 1024 * 1024
) {
  def apply(upload: GroupedFileUpload, initialPosition: Long): Sink[ByteString,Future[Unit]] = {
    val buffer = Flow.fromGraph(new Chunker(bufferSize).named("Chunker"))
    val write = Sink.foldAsync(initialPosition)({ (position: Long, bytes: ByteString) =>
      groupedFileUploadBackend.writeBytes(upload.id, position, bytes.toArray)
        .map(_ => position + bytes.length)
    })

    buffer.toMat(write)(Keep.right)
      .mapMaterializedValue(_.map(_ => ()))
  }
}
