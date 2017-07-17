package views.json.DocumentList

import java.util.UUID
import play.api.libs.json.{JsString, JsValue, Json}
import play.twirl.api.{Html,HtmlFormat}
import scala.collection.immutable

import models.pagination.Page
import models.{Selection,SelectionWarning}
import views.json.api.selectionWarnings
import com.overviewdocs.models.DocumentHeader
import com.overviewdocs.searchindex.{Highlight,Snippet}

object show {
  private def documentToJson(maybeSortKey: Option[String])(document: DocumentHeader, thumbnailUrl: Option[String], nodeIds: Seq[Long], tagIds: Seq[Long], snippets: Seq[Snippet]) : JsValue = {
    Json.obj(
      "id" -> document.id,
      "documentSetId" -> document.documentSetId.toString,
      "description" -> document.keywords,
      "title" -> document.title,
      "page_number" -> document.pageNumber,
      "url" -> document.viewUrl,
      "sortKey" -> maybeSortKey.flatMap(sortKey => document.metadataJson.value.get(sortKey) match {
        case Some(JsString(s)) => Some(Json.obj("name" -> sortKey, "value" -> s))
        case _ => None
      }),
      "nodeids" -> nodeIds,
      "tagids" -> tagIds,
      "snippet" -> snippetsToHtml(snippets, document.text),
      "thumbnailUrl" -> thumbnailUrl
    )
  }

  def snippetsToHtml(snippets: Seq[Snippet], documentText: String): String = {
    val tokens: Seq[Snippet.Token] = Snippet.concatTokenCollections(snippets.map(_.tokenize(documentText)))
    val htmls: Seq[Html] = tokens.map(_ match {
      case Snippet.TextToken(text) => HtmlFormat.escape(text)
      case Snippet.HighlightToken(text) => HtmlFormat.fill(immutable.Seq(
        HtmlFormat.raw("<em>"),
        HtmlFormat.escape(text),
        HtmlFormat.raw("</em>")
      ))
      case Snippet.ElisionToken => HtmlFormat.raw("…")
    })
    HtmlFormat.fill(htmls.to[immutable.Seq]).body
  }

  def apply(selection: Selection, maybeSortKey: Option[String], documents: Page[(DocumentHeader,Option[String],Seq[Long],Seq[Long],Seq[Snippet])]) = {
    Json.obj(
      "selection_id" -> selection.id.toString,
      "warnings" -> selectionWarnings(selection.warnings),
      "total_items" -> documents.pageInfo.total,
      "documents" -> documents.items.map(Function.tupled(documentToJson(maybeSortKey))).toSeq
    )
  }
}
