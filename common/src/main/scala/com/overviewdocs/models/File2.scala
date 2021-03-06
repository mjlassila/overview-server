package com.overviewdocs.models

import java.time.Instant
import play.api.libs.json.JsObject

/** A file derived (or copied) from a user upload.
  *
  * In Overview, each Document points to the leaf of a tree of File2 objects.
  * Each root is the file the user uploaded to the document set.
  *
  * File2s are shared among DocumentSets, to make cloning cheap. (File2s are
  * immutable, once INGESTED.)
  *
  * == File2 State During Ingestion ==
  *
  * During ingest, a File2 is a state machine:
  *
  * 1. CREATED means we have partial data about the File2. We know it exists,
  *    and we know what file type it should have.
  * 2. WRITTEN means the File2's contents are finalized, but they have not
  *    been converted to a format Overview understands.
  * 3. PROCESSED means the File2 has been inspected by Overview. It has produced
  *    one or more File2s, or it is a leaf. Processing may have produced a
  *    `processingError`, which is a natural part of the ingesting process.
  * 4. INGESTED means all File2Errors and Documents have been produced from this
  *    File2: there is nothing more to process.
  */
case class File2(
  /** Unique ID. */
  id: Long,

  /** File2 that the user uploaded to generate this File2. `None` if this is
    * the root.
    *
    * This makes DELETE easier: we can delete all children with one query.
    *
    * (Why not rootFile2Id == id when this is the root? Because that makes it
    * hard for SQL to enforce constraints and hard for Slick use the SEQUENCE.)
    */
  rootFile2Id: Option[Long],

  /** File2 that was used to generate this File2: None if this is the root. */
  parentFile2Id: Option[Long],

  /** Number of File2s derived from the parent File2 ahead of this one.
    *
    * This is 0 for a root File2, and it's 0 when the parent File2 generates
    * just one File2 child.
    */
  indexInParent: Int,

  /** Filename, or pseudo-filename: for instance, "archive.zip/Foo/bar.txt". */
  filename: String,

  /** Media type, as would in an HTTP GET response, specified by:
    *
    * * For _root_ File2: the user, during upload (default application/octet-stream)
    * * For interim File2: the parent processing step
    * * For _leaf_ File2: the parent processing step
    */
  contentType: String,

  /** ISO 639-1 language identifier, supplied by the user. */
  languageCode: String,

  /** Metadata supplied by user, augmented by pipeline. */
  metadata: File2.Metadata,

  /** Iff true, user has requested OCR and we have not done OCR yet. */
  wantOcr: Boolean,

  /** Iff true, user has requested to split into one document per page, and we
    * have not done so yet.
    */
  wantSplitByPage: Boolean,

  /** Reference to "main" file data.
    *
    * * For the _root_ File2, this is the uploaded data.
    * * For interim File2s, this is the interim data (e.g., "archive.pst/Foo/x.doc").
    * * For the _leaf_ File2, this is PDF data.
    */
  blob: Option[BlobStorageRef],

  /** Checksum for the file data, used to detect duplicates. */
  blobSha1: Array[Byte],

  /** Thumbnail data.
    *
    * * For a _tail_ File2, this is always None.
    * * For an interim File2, this is optional.
    * * For a _head_ File2, this points to image data.
    */
  thumbnailBlob: Option[BlobStorageRef],

  /** Thumbnail content-type.
    *
    * In 2018, we want image/jpeg or image/png. Maybe someday, image/webp.
    */
  thumbnailContentType: Option[String],

  /** Text data.
    *
    * * For a _tail_ File2, this is always None.
    * * For an interim File2, this is optional.
    * * For a _head_ File2, this is equal to document.text.
    */
  text: Option[String],

  /** When the creation of this File2 began.
    *
    * Building a File2 means copying data from a file converter to BlobStorage.
    * We need a database handle before the copy is complete, so we can resume or
    * delete from BlobStorage. It's possible for a File2 to be "created" and
    * have no data.
    */
  createdAt: Instant,

  /** When this File2's BlobStorage and text data became immutable.
    *
    * Only after a File2 becomes immutable do we begin creating documents from
    * it.
    *
    * Resuming ingestion of a WRITTEN File2 means processing it. It can't be
    * deleted until it's processed and ingested.
    */
  writtenAt: Option[Instant],

  /** When pipeline steps were done with the data in this File2.
    *
    * This means:
    *
    * * If this is a leaf, it is WRITTEN.
    * * If this is a parent, all File2 children are WRITTEN.
    *
    * Resuming ingestion of a PROCESSED File2 means processing its children and
    * converting it to Documents and/or a DocumentProcessingError. It can't be
    * deleted until it's ingested.
    */
  processedAt: Option[Instant],

  /** Number of WRITTEN, PROCESSED or INGESTED children of this File2.
    *
    * For a leaf File2, this is Some(0). For a garbage File2, this is Some(0)
    * and processingError is set. For a half-garbage File2, this is the number
    * of children emitted before error and processingError is set.
    */
  nChildren: Option[Int],

  /** Error produced during processing, if there was one.
    *
    * This serves a different purpose than File2Error. File2Error is displayed
    * during DocumentSet viewing, and the user sees it; File2Error is _derived_
    * data created when converting from PROCESSED to INGESTED.
    * File2.processError is written when converting from WRITTEN to PROCESSED:
    * it simply captures pipeline output.
    *
    * To be absolutely clear: if a child has a `processingError`, that does
    * _not_ bubble up to a `processingError` on the _parent_. Processing is
    * finished once children are WRITTEN. By definition, the children have no
    * errors at that point.
    */
  processingError: Option[String],

  /** When pipeline steps were done with the children of this File2.
    *
    * This means:
    *
    * * If this is a leaf, a Document or File2Error points to it.
    * * If this is a parent, all File2 children are INGESTED.
    *
    * Notice the recursion. If a root node is INGESTED, all Documents and
    * File2Errors that refer to it are built.
    *
    * Resuming ingestion of an INGESTED File2 is a no-op.
    */
  ingestedAt: Option[Instant]
) {
  def state: File2.State = {
    if (ingestedAt.nonEmpty) {
      File2.State.Ingested
    } else if (processedAt.nonEmpty) {
      File2.State.Processed
    } else if (writtenAt.nonEmpty) {
      File2.State.Written
    } else {
      File2.State.Created
    }
  }
}

object File2 {
  case class Metadata(jsObject: JsObject) // indirection fools Slick into treating this like a separate type

  sealed trait State
  object State {
    case object Created extends State
    case object Written extends State
    case object Processed extends State
    case object Ingested extends State
  }
}
