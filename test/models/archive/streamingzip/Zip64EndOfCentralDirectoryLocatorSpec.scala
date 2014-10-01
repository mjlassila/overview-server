package models.archive.streamingzip

import java.io.InputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class Zip64EndOfCentralDirectoryLocatorSpec extends Specification with Mockito {

  "Zip64EndOfCentralDirectoryLocator" should {

    "write to stream" in new EndOfCentralDirectoryLocatorContext {
      val expectedValues =
        writeInt(0x07064b50) ++
          writeInt(0) ++
          writeLong(numberOfFiles * (entrySize + fileHeaderSize)) ++
          writeInt(1)

      val output = readStream(locator.stream)
      
      output must be equalTo expectedValues
    }

    trait EndOfCentralDirectoryLocatorContext extends Scope with LittleEndianWriter {
      val zip64CentralFileHeader = smartMock[Zip64CentralFileHeader]
      val zip64LocalFileEntry = smartMock[Zip64LocalFileEntry]
      
      val numberOfFiles = 10
      val entrySize = 10345
      val fileHeaderSize = 1305
    
      zip64CentralFileHeader.size returns fileHeaderSize
      zip64LocalFileEntry.size returns entrySize

      val locator = new Zip64EndOfCentralDirectoryLocator(
          Seq.fill(numberOfFiles)(zip64LocalFileEntry), Seq.fill(numberOfFiles)(zip64CentralFileHeader)
          )

      def readStream(stream: InputStream): Array[Byte] =
        Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

    }

  }
}