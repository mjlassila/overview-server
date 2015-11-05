package com.overviewdocs.models

import com.overviewdocs.tree.orm.{DocumentSetCreationJob => DeprecatedDocumentSetCreationJob}
import com.overviewdocs.tree.{DocumentSetCreationJobType => DeprecatedDocumentSetCreationJobType}
import com.overviewdocs.tree.orm.{DocumentSetCreationJobState => DeprecatedDocumentSetCreationJobState}

object DocumentSetCreationJobType extends Enumeration {
  type DocumentSetCreationJobType = Value
  
  val DocumentCloud = Value(1)
  val CsvUpload = Value(2)
  val Clone = Value(3)
  val FileUpload = Value(4)
  val Recluster = Value(5)
}

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value
  
  // XXX nix the capitalized stuff. I'm not sure why we need it, but we test for it in app/.
  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
  val Cancelled = Value(3, "CANCELLED")
  val FilesUploaded = Value(4, "FILES_UPLOADED")
  val TextExtractionInProgress = Value(5, "TEXT_EXTRACTION_IN_PROGRESS")
}

case class DocumentSetCreationJob( 
  id: Long,
  documentSetId: Long,
  jobType: DocumentSetCreationJobType.Value,
  retryAttempts: Int,
  lang: String,
  suppliedStopWords: String,
  importantWords: String,
  splitDocuments: Boolean,
  documentcloudUsername: Option[String],
  documentcloudPassword: Option[String],
  contentsOid: Option[Long],
  sourceDocumentSetId: Option[Long],
  treeTitle: Option[String],
  treeDescription: Option[String],
  tagId: Option[Long],
  state: DocumentSetCreationJobState.Value,
  fractionComplete: Double,
  statusDescription: String,
  canBeCancelled: Boolean
) {
  def toDeprecatedDocumentSetCreationJob = DeprecatedDocumentSetCreationJob(
    id,
    documentSetId,
    DeprecatedDocumentSetCreationJobType(jobType.id),
    lang,
    suppliedStopWords,
    importantWords,
    documentcloudUsername,
    documentcloudPassword,
    splitDocuments,
    contentsOid,
    sourceDocumentSetId,
    treeTitle,
    tagId,
    DeprecatedDocumentSetCreationJobState(state.id),
    fractionComplete,
    statusDescription,
    treeDescription,
    retryAttempts,
    canBeCancelled
  )
}

object DocumentSetCreationJob {
  case class CreateAttributes(
    documentSetId: Long,
    jobType: DocumentSetCreationJobType.Value,
    retryAttempts: Int,
    lang: String,
    suppliedStopWords: String,
    importantWords: String,
    splitDocuments: Boolean,
    documentcloudUsername: Option[String],
    documentcloudPassword: Option[String],
    contentsOid: Option[Long],
    sourceDocumentSetId: Option[Long],
    treeTitle: Option[String],
    treeDescription: Option[String],
    tagId: Option[Long],
    state: DocumentSetCreationJobState.Value,
    fractionComplete: Double,
    statusDescription: String,
    canBeCancelled: Boolean
  )
}
