package views.json.Tree

import scala.collection.JavaConversions._

import scala.collection.mutable.HashSet

import models.{Node,PartiallyLoadedNode,Document}

import play.api.libs.json._

object JsonHelpers {
    val maxElementsInList = 10

    def subNodeToJsValue(node: Node) : JsValue = {
        val partialNode = new PartiallyLoadedNode(node.getId)
        val partialDocumentList = partialNode.getDocuments(0, maxElementsInList - 1)
        // TODO: move the above into app.models

        JsObject(Seq(
            "id" -> JsNumber(node.id.longValue),
            "description" -> JsString(node.getDescription),
            "children" -> JsArray(node.getChildren.toSeq.map(n => JsNumber(n.id.longValue))),
            "doclist" -> JsObject(Seq(
                "docids" -> JsArray(partialDocumentList.
                    map(d => JsNumber(d.getId.longValue))),
                "n" -> JsNumber(node.getDocuments.size.intValue())
            )),
            "taglist" -> JsObject(Seq(
                "full" -> JsArray(Seq()),
                "partial" -> JsArray(Seq())
            ))
        ))
    }

    def rootNodeToJsValue(rootNode: Node) : JsValue = {
        val includedNodes = rootNode.getNodesBreadthFirst(12).toSeq
        
        val partiallyLoadedNodes = includedNodes.map(n => new PartiallyLoadedNode(n.getId))
        val documentsInNodes = partiallyLoadedNodes.flatMap(_.getDocuments(0, maxElementsInList))
        val includedDocumentIds = documentsInNodes.toSet
        

        JsObject(Seq(
            "nodes" -> JsArray(includedNodes.map(JsonHelpers.subNodeToJsValue)),
            "documents" -> JsArray(
                includedDocumentIds.toSeq.map(d =>
                    JsObject(
                        Seq(
                            "id" -> JsNumber(d.id.longValue),
                            "description" -> JsString(d.getTitle)
                        )
                    )
            )),
            "tags" -> JsArray(Seq())
        ))
    }
}
