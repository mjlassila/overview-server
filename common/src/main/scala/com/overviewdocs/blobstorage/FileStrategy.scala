package com.overviewdocs.blobstorage

import akka.stream.scaladsl.{FileIO,Source}
import akka.util.ByteString
import java.io.{File,IOException}
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel,CompletionHandler}
import java.nio.file.{Files,Path,StandardOpenOption}
import java.util.{Base64,UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise,blocking}

trait FileStrategy extends BlobStorageStrategy {
  protected val config: BlobStorageConfig

  lazy val baseDirectory: File = new File(config.fileBaseDirectory)

  private val LocationRegex = """^file:([-\w]+):([-\w]+)$""".r
  private case class Location(bucket: String, key: String)
  private def stringToLocation(string: String): Location = string match {
    case LocationRegex(bucket, key) => Location(bucket, key)
    case _ => throw new IllegalArgumentException(s"Invalid location string: '${string}'")
  }

  private def keyFile(location: Location): File =
    new File(new File(baseDirectory, location.bucket), location.key)

  private def createNewLocationString(locationPrefix: String) = 
    s"$locationPrefix:${UUID.randomUUID}"
  
  override def get(locationString: String): Source[ByteString, akka.NotUsed] = {
    val location = stringToLocation(locationString)
    val file = keyFile(location)
    FileIO.fromPath(file.toPath)
      .mapMaterializedValue(_ => akka.NotUsed)
  }

  override def getBytes(locationString: String, maxNBytes: Int): Future[Array[Byte]] = {
    val location = stringToLocation(locationString)
    val file = keyFile(location)

    val channel = try {
      AsynchronousFileChannel.open(file.toPath, StandardOpenOption.READ)
    } catch {
      case ex: IOException => return Future.failed(ex)
    }

    val ret = Promise.apply[Array[Byte]]

    val buf = ByteBuffer.allocate(maxNBytes)
    channel.read(buf, 0, ret, new CompletionHandler[Integer,Promise[Array[Byte]]] {
      override def completed(nBytes: Integer, p: Promise[Array[Byte]]): Unit = {
        val bytes = new Array[Byte](maxNBytes)
        buf.rewind()
        buf.get(bytes, 0, nBytes)
        p.success(bytes)
      }

      override def failed(ex: Throwable, p: Promise[Array[Byte]]): Unit = {
        p.failure(ex)
      }
    })

    ret.future
  }

  override def getUrl(locationString: String, mimeType: String): Future[String] = {
    val location = stringToLocation(locationString)
    val path = keyFile(location).toPath
    Future { blocking {
      val bytes = Files.readAllBytes(path)
      "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes)
    }}
  }

  override def getUrlOpt(locationString: String, mimeType: String): Future[Option[String]] = {
    Future.successful(None)
  }

  override def delete(locationString: String): Future[Unit] = {
    val location = stringToLocation(locationString)
    val file = keyFile(location)
    Future { blocking {
      Files.deleteIfExists(file.toPath)
    } }
  }

  override def create(locationPrefix: String, dataPath: Path): Future[String] = {
    val locationString = createNewLocationString(locationPrefix)
    val location = stringToLocation(locationString)

    Future {
      val outPath = keyFile(location).toPath()
      Files.createDirectories(outPath.getParent)
      Files.copy(dataPath, outPath)

      locationString
    }
  }

}

object FileStrategy extends FileStrategy {
  override protected val config = BlobStorageConfig
}
