package controllers.backend

import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits.global

import models.pagination.{PageInfo,PageRequest}
import org.overviewproject.searchindex.{InMemoryIndexClient,IndexClient}
import org.overviewproject.models.Document

class DbDocumentBackendSpec extends DbBackendSpecification with Mockito {
  trait BaseScopeNoIndex extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentBackend {
      override val indexClient = mock[IndexClient]
    }
  }

  trait BaseScopeWithIndex extends DbScope {
    val testIndexClient: InMemoryIndexClient = new InMemoryIndexClient()
    val backend = new TestDbBackend(session) with DbDocumentBackend {
      override val indexClient: IndexClient = testIndexClient
    }

    override def after = {
      testIndexClient.close
      super.after
    }
  }

  trait CommonIndexScope extends BaseScopeWithIndex {
    val documentSet = factory.documentSet()
    val doc1 = factory.document(documentSetId=documentSet.id, title="c", text="foo bar baz")
    val doc2 = factory.document(documentSetId=documentSet.id, title="a", text="moo mar maz")
    val doc3 = factory.document(documentSetId=documentSet.id, title="b", text="noo nar naz")
    val documents = Seq(doc1, doc2, doc3)

    await(testIndexClient.addDocumentSet(documentSet.id))
    await(testIndexClient.addDocuments(documents.map(_.toDeprecatedDocument)))
    await(testIndexClient.refresh())

    val q = ""
  }

  "DbDocumentBackendSpec" should {
    "#index" should {
      trait IndexScope extends CommonIndexScope {
        val pageRequest = PageRequest(0, 1000)
        lazy val ret = await(backend.index(documentSet.id, q=q, pageRequest))
      }

      "show all documents by default" in new IndexScope {
        ret.items.length must beEqualTo(3)
        ret.pageInfo.total must beEqualTo(3)
      }

      "sort documents by title" in new IndexScope {
        ret.items.map(_.id) must beEqualTo(Seq(doc2.id, doc3.id, doc1.id))
      }

      "search by q" in new IndexScope {
        override val q = "moo"
        ret.items.map(_.id) must beEqualTo(Seq(doc2.id))
      }

      "honor offset" in new IndexScope {
        override val pageRequest = PageRequest(1, 1000)
        ret.items.map(_.id) must beEqualTo(Seq(doc3.id, doc1.id))
        ret.pageInfo must beEqualTo(PageInfo(pageRequest, 3))
      }

      "honor limit" in new IndexScope {
        override val pageRequest = PageRequest(0, 2)
        ret.items.map(_.id) must beEqualTo(Seq(doc2.id, doc3.id))
        ret.pageInfo must beEqualTo(PageInfo(pageRequest, 3))
      }
    }

    "#indexIds" should {
      trait IndexIdsScope extends CommonIndexScope {
        lazy val ret = await(backend.indexIds(documentSet.id, q=q))
      }

      "show all documents by default" in new IndexIdsScope {
        ret.length must beEqualTo(3)
      }

      "sort documents by title" in new IndexIdsScope {
        ret must beEqualTo(Seq(doc2.id, doc3.id, doc1.id))
      }

      "search by q" in new IndexIdsScope {
        override val q = "moo"
        ret must beEqualTo(Seq(doc2.id))
      }
    }

    "#show" should {
      trait ShowScope extends BaseScopeNoIndex {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id, title="title", text="text")

        val documentSetId = documentSet.id
        val documentId = document.id

        lazy val ret = await(backend.show(documentSetId, documentId))
      }

      "show a document" in new ShowScope {
        ret must beSome { d: Document =>
          d.id must beEqualTo(document.id)
          d.title must beEqualTo(document.title)
          d.text must beEqualTo(document.text)
        }
      }

      "not show a document with the wrong document set ID" in new ShowScope {
        override val documentSetId = documentSet.id + 1L
        ret must beNone
      }

      "not show a document with the wrong ID" in new ShowScope {
        override val documentId = document.id + 1L
        ret must beNone
      }
    }
  }
}
