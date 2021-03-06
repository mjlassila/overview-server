package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import play.api.libs.json.{Json,JsObject,JsNull}
import play.api.mvc.AnyContent
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{ApiTokenBackend,StoreBackend,ViewBackend}
import com.overviewdocs.models.{ApiToken,Tree,View,ViewDocumentDetailLink,ViewFilter}
import com.overviewdocs.test.factories.PodoFactory

class ViewControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val factory = PodoFactory

    val mockStorage = mock[ViewController.Storage]
    val mockApiTokenBackend = mock[ApiTokenBackend]
    val mockStoreBackend = mock[StoreBackend]
    val mockViewBackend = mock[ViewBackend]

    val controller = new ViewController(
      mockStorage,
      mockApiTokenBackend,
      mockStoreBackend,
      mockViewBackend,
      fakeControllerComponents
    )

    def fakeTree(id: Long, jobId: Long) = factory.tree(
      id=id,
      documentSetId=1L,
      rootNodeId=Some(3L),
      title=s"title${id}",
      documentCount=Some(10),
      lang="en",
      description=s"description${id}",
      suppliedStopWords=s"suppliedStopWords${id}",
      importantWords=s"importantWords${id}"
    )
  }

  "ViewController" should {
    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSetId = 1L
        val formBody: Vector[(String,String)] = Vector()
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
        lazy val result = controller.create(documentSetId)(request)

        val validFormBody = Vector(
          "title" -> "title",
          "url" -> "http://localhost:9001",
          "server_url_from_plugin" -> ""
        )
      }

      "return 400 Bad Request on invalid form body" in new CreateScope {
        override val formBody = Vector("title" -> "", "url" -> "http://localhost:9001")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "create an ApiToken" in new CreateScope {
        override val formBody = validFormBody
        mockApiTokenBackend.create(any[Option[Long]], any[ApiToken.CreateAttributes]) returns Future.failed(new Throwable("goto end"))
        h.status(result)
        there was one(mockApiTokenBackend).create(Some(documentSetId), ApiToken.CreateAttributes(
          email="user@example.org",
          description="title"
        ))
      }

      "create a View" in new CreateScope {
        override val formBody = validFormBody
        mockApiTokenBackend.create(any[Option[Long]], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken(token="api-token"))
        mockViewBackend.create(any[Long], any[View.CreateAttributes]) returns Future.failed(new Throwable("goto end"))
        h.status(result)
        there was one(mockViewBackend).create(
          beLike[Long] { case x => x must beEqualTo(documentSetId) },
          beLike[View.CreateAttributes] { case attributes =>
            attributes.url must beEqualTo("http://localhost:9001")
            attributes.serverUrlFromPlugin must beNone
            attributes.apiToken must beEqualTo("api-token")
            attributes.title must beEqualTo("title")
          }
        )
      }

      "create a View with a serverUrlFromPlugin" in new CreateScope {
        override val formBody = Vector(
          "title" -> "title",
          "url" -> "http://localhost:9001",
          "serverUrlFromPlugin" -> "http://overview-web:9000"
        )
        mockApiTokenBackend.create(any[Option[Long]], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken(token="api-token"))
        mockViewBackend.create(any[Long], any[View.CreateAttributes]) returns Future.failed(new Throwable("goto end"))
        h.status(result)
        there was one(mockViewBackend).create(
          beLike[Long] { case x => x must beEqualTo(documentSetId) },
          beLike[View.CreateAttributes] { case attributes =>
            attributes.url must beEqualTo("http://localhost:9001")
            attributes.serverUrlFromPlugin must beSome("http://overview-web:9000")
            attributes.apiToken must beEqualTo("api-token")
            attributes.title must beEqualTo("title")
          }
        )
      }

      "return the View" in new CreateScope {
        override val formBody = validFormBody
        mockApiTokenBackend.create(any[Option[Long]], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken())
        mockViewBackend.create(any[Long], any[View.CreateAttributes]) returns Future.successful(factory.view(
          id=123L,
          url="http://localhost:9001",
          serverUrlFromPlugin=None,
          apiToken="api-token",
          title="title",
          createdAt=new java.sql.Timestamp(1234L)
        ))
        h.status(result) must beEqualTo(h.CREATED)
        val json = h.contentAsString(result)
        json must /("id" -> 123)
        json must /("url" -> "http://localhost:9001")
        json must beMatching(""".*"serverUrlFromPlugin":null.*""".r)
        json must /("apiToken" -> "api-token")
        json must /("title" -> "title")
        json must /("createdAt" -> "1970-01-01T00:00:01.234Z")
      }
    }

    "#indexJson" should {
      trait IndexJsonScope extends BaseScope {
        val documentSetId = 1L
        def request = fakeAuthorizedRequest
        def result = controller.indexJson(documentSetId)(request)
        lazy val trees : Iterable[Tree] = Vector()
        mockViewBackend.index(documentSetId) returns Future.successful(Vector[View]())
        mockStorage.findTrees(documentSetId) returns trees
      }

      "return 200 OK" in new IndexJsonScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "show nothing by default" in new IndexJsonScope {
        h.contentAsString(result) must beEqualTo("[]")
      }

      "show a tree" in new IndexJsonScope {
        override lazy val trees = Vector(fakeTree(1L, 3L))
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "tree")
        json must /#(0) /("id" -> 1)
        json must /#(0) /("title" -> "title1")
        json must /#(0) /("createdAt" -> "\\d\\d\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ".r)
        json must /#(0) /("creationData") /#(2) /("lang")
        json must /#(0) /("creationData") /#(2) /("en")
        json must /#(0) /("nDocuments" -> 10)
      }

      "show a view" in new IndexJsonScope {
        val view = factory.view(id=2L)
        mockViewBackend.index(documentSetId) returns Future.successful(Vector(view))

        val json = h.contentAsString(result)
        json must /#(0) /("type" -> "view")
        json must /#(0) /("id" -> 2)
        json must /#(0) /("title" -> view.title)
        json must /#(0) /("url" -> view.url)
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val documentSetId = 1L
        val viewId = 2L
        mockViewBackend.update(any, any) returns Future.successful(Some(PodoFactory.view(title="updated title")))
        val request: AuthorizedRequest[_ <: AnyContent] = fakeAuthorizedRequest.withFormUrlEncodedBody("title" -> "submitted title")
        lazy val result = controller.update(documentSetId, viewId)(request)
      }

      "call ViewBackend#update" in new UpdateScope {
        h.status(result)
        there was one(mockViewBackend).update(viewId, View.UpdateAttributes(title="submitted title"))
      }

      "call ViewBackend#update when input is JSON" in new UpdateScope {
        override val request = fakeAuthorizedRequest.withJsonBody(Json.obj("title" -> "submitted title"))
        h.status(result) must beEqualTo(h.OK)
        there was one(mockViewBackend).update(viewId, View.UpdateAttributes(title="submitted title"))
      }

      "return the updated View" in new UpdateScope {
        h.status(result) must beEqualTo(h.OK)
        val json = h.contentAsString(result)

        json must /("title" -> "updated title")
      }

      "return NotFound when the View is not found" in new UpdateScope {
        mockViewBackend.update(any, any) returns Future.successful(None)
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest when the input is bad" in new UpdateScope {
        override val request = fakeAuthorizedRequest.withFormUrlEncodedBody("titleblah" -> "submittedblah")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }
    }

    "#update() with JSON ViewFilter" should {
      trait UpdateViewFilterScope extends BaseScope {
        val documentSetId = 1L
        val viewId = 2L
        def expectedResult: Option[View] = Some(PodoFactory.view(viewFilter=Some(ViewFilter("http://written", Json.obj("url" -> "http://written")))))
        mockViewBackend.updateViewFilter(any, any) returns Future.successful { expectedResult }
        val requestBody: JsObject = Json.obj("filter" -> Json.obj("url" -> "http://foo"))
        lazy val request = fakeAuthorizedRequest.withJsonBody(requestBody)
        lazy val result = controller.update(documentSetId, viewId)(request)
      }

      "call ViewBackend#updateViewFilter" in new UpdateViewFilterScope {
        h.status(result)
        there was one(mockViewBackend).updateViewFilter(viewId, Some(ViewFilter("http://foo", Json.obj("url" -> "http://foo"))))
      }

      "return the updated View" in new UpdateViewFilterScope {
        h.status(result)
        val json = h.contentAsString(result)

        json must /("filter") /("url" -> "http://written")
      }

      "return NotFound when the View is not found" in new UpdateViewFilterScope {
        override def expectedResult = None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest when the input has no URL" in new UpdateViewFilterScope {
        override val requestBody = Json.obj("filter" -> Json.obj("foo" -> "bar"))
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "set viewFilter to None when given filter=null" in new UpdateViewFilterScope {
        override val requestBody = JsObject(Map("filter" -> JsNull))
        h.status(result) must beEqualTo(h.OK)
        there was one(mockViewBackend).updateViewFilter(viewId, None)
      }
    }

    "#update() with JSON documentDetailLink" should {
      trait UpdateDocumentDetailLink extends BaseScope {
        val documentSetId = 1L
        val viewId = 2L
        def documentDetailLink: Option[ViewDocumentDetailLink] = Some(ViewDocumentDetailLink("https://url/:documentId", "Title", "Text", "icon-class"))
        def expectedResult: Option[View] = Some(PodoFactory.view(documentDetailLink=documentDetailLink))
        mockViewBackend.updateDocumentDetailLink(any, any) returns Future.successful { expectedResult }
        val documentDetailLinkUrl = "https://url/:documentId"
        val documentDetailLinkTitle = "Title"
        val documentDetailLinkText = "Text"
        val documentDetailLinkIconClass = "icon-class"
        lazy val documentDetailLinkJs = Json.obj(
          "url" -> documentDetailLinkUrl,
          "title" -> documentDetailLinkTitle,
          "text" -> documentDetailLinkText,
          "iconClass" -> documentDetailLinkIconClass
        )
        lazy val requestBody: JsObject = Json.obj("documentDetailLink" -> documentDetailLinkJs)
        lazy val request = fakeAuthorizedRequest.withJsonBody(requestBody)
        lazy val result = controller.update(documentSetId, viewId)(request)
      }

      "call ViewBackend#updateDocumentDetailLink" in new UpdateDocumentDetailLink {
        h.status(result)
        there was one(mockViewBackend).updateDocumentDetailLink(viewId, documentDetailLink)
      }

      "return the updated View" in new UpdateDocumentDetailLink {
        h.status(result)
        val json = h.contentAsString(result)

        json must /("documentDetailLink") /("url" -> "https://url/:documentId")
        json must /("documentDetailLink") /("title" -> "Title")
        json must /("documentDetailLink") /("text" -> "Text")
        json must /("documentDetailLink") /("iconClass" -> "icon-class")
      }

      "return NotFound when the View is not found" in new UpdateDocumentDetailLink {
        override def expectedResult = None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest when the input has no URL" in new UpdateDocumentDetailLink {
        override lazy val documentDetailLinkJs = Json.obj(
          "title" -> documentDetailLinkTitle,
          "text" -> documentDetailLinkText,
          "iconClass" -> documentDetailLinkIconClass
        )
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "return BadRequest when the input url has no ':documentId'" in new UpdateDocumentDetailLink {
        override val documentDetailLinkUrl = "https://url"
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "set documentDetailLink to None when given documentDetailLink=null" in new UpdateDocumentDetailLink {
        override lazy val requestBody = JsObject(Map("documentDetailLink" -> JsNull))
        h.status(result) must beEqualTo(h.OK)
        there was one(mockViewBackend).updateDocumentDetailLink(viewId, None)
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSetId = 1L
        val viewId = 2L
        val apiToken = "some-token"
        lazy val request = fakeAuthorizedRequest
        def result = controller.destroy(documentSetId, viewId)(request)
        mockViewBackend.show(any) returns Future.successful(Some(factory.view(id=viewId, apiToken=apiToken)))
        mockViewBackend.destroy(any) returns Future.unit
        mockStoreBackend.destroy(any) returns Future.unit
        mockApiTokenBackend.destroy(any) returns Future.unit
      }

      "return NoContent" in new DestroyScope {
        h.status(result) must beEqualTo(h.NO_CONTENT)
      }

      "destroy the View" in new DestroyScope {
        h.status(result)
        there was one(mockViewBackend).destroy(viewId)
      }

      "destroy the Store" in new DestroyScope {
        h.status(result)
        there was one(mockStoreBackend).destroy(apiToken)
      }

      "destroy the ApiToken" in new DestroyScope {
        h.status(result)
        there was one(mockApiTokenBackend).destroy(apiToken)
      }
    }
  }
}
