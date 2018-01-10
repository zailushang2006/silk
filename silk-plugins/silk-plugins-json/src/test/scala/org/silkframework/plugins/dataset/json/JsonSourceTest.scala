package org.silkframework.plugins.dataset.json

import org.scalatest.{FlatSpec, MustMatchers}
import org.silkframework.entity.{EntitySchema, Path}
import org.silkframework.runtime.resource.{ClasspathResourceLoader, InMemoryResourceManager}
import org.silkframework.util.Uri

import scala.io.Codec

class JsonSourceTest extends FlatSpec with MustMatchers {
  behavior of "Json Source"

  private def jsonSource: JsonSource = {
    val resources = ClasspathResourceLoader("org/silkframework/plugins/dataset/json/")
    val source = new JsonSource(resources.get("example.json"), "", "#id", Codec.UTF8)
    source
  }

  it should "return all inner node types" in {
    val types = jsonSource.retrieveTypes().map(_._1).toSet
    types mustBe Set(
      "",
      "/persons",
      "/persons/phoneNumbers",
      "/organizations"
    )
  }

  it should "not return an entity for an empty JSON array" in {
    val resourceManager = InMemoryResourceManager()
    val resource = resourceManager.get("test.json")
    resource.writeString(
      """
        |{"data":[]}
      """.stripMargin)
    val source = new JsonSource(resource, "data", "http://blah", Codec.UTF8)
    val entities = source.retrieve(EntitySchema.empty)
    entities mustBe empty
  }

  it should "not return entities for valid paths" in {
    val resourceManager = InMemoryResourceManager()
    val resource = resourceManager.get("test.json")
    resource.writeString(
      """
        |{"data":{"entities":[{"id":"ID"}]}}
      """.stripMargin)
    val source = new JsonSource(resource, "data/entities", "http://blah/{id}", Codec.UTF8)
    val entities = source.retrieve(EntitySchema.empty)
    entities.size mustBe 1
    entities.head.uri mustBe "http://blah/ID"
  }

  it should "return peak results" in {
    val result = jsonSource.peak(EntitySchema(Uri(""), typedPaths = IndexedSeq(Path.parse("/persons/phoneNumbers/number").asStringTypedPath)), 3).toSeq
    result.size mustBe 1
    result.head.values mustBe IndexedSeq(Seq("123", "456", "789"))
  }

  it should "return peak results with sub path set" in {
    val result = jsonSource.peak(EntitySchema(Uri(""), typedPaths = IndexedSeq(Path.parse("/number").asStringTypedPath),
      subPath = Path.parse("/persons/phoneNumbers")), 3).toSeq
    result.size mustBe 3
    result.map(_.values) mustBe Seq(IndexedSeq(Seq("123")), IndexedSeq(Seq("456")), IndexedSeq(Seq("789")))
  }

  it should "return all paths including intermediate paths for retrieve paths" in {
    val paths = jsonSource.retrievePaths(Uri(""), depth = Int.MaxValue)
    paths.size mustBe 8
    paths must contain allOf(Path.parse("/persons"), Path.parse("/persons/phoneNumbers"))
  }

  it should "return all paths of depth 1" in {
    val paths = jsonSource.retrievePaths(Uri(""), depth = 1)
    paths.map(_.serializeSimplified) mustBe Seq("persons", "organizations")
  }

  it should "return all paths of depth 2" in {
    val paths = jsonSource.retrievePaths(Uri(""), depth = 2)
    paths.map(_.serializeSimplified) mustBe Seq("persons", "persons/id", "persons/name", "persons/phoneNumbers", "organizations", "organizations/name")
  }

  it should "return all paths of depth 1 of sub path" in {
    val paths = jsonSource.retrievePaths(Uri("/persons"), depth = 1)
    paths.map(_.serializeSimplified) mustBe Seq("id", "name", "phoneNumbers")
  }

  it should "return all paths of depth 2 of sub path" in {
    val paths = jsonSource.retrievePaths(Uri("/persons"), depth = 2)
    paths.map(_.serializeSimplified) mustBe Seq("id", "name", "phoneNumbers", "phoneNumbers/type", "phoneNumbers/number")
  }

  it should "return all paths of depth 1 of sub path of length 2" in {
    val paths = jsonSource.retrievePaths(Uri("/persons/phoneNumbers"), depth = 1)
    paths.map(_.serializeSimplified) mustBe Seq("type", "number")
  }

  it should "list all leaf paths of the root" in {
    val paths = jsonSource.retrieveJsonPaths(Uri(""), depth = Int.MaxValue, limit = None, leafPathsOnly = true, innerPathsOnly = false)
    paths.map(_.serializeSimplified) mustBe Seq("persons/id", "persons/name", "persons/phoneNumbers/type", "persons/phoneNumbers/number", "organizations/name")
  }

  it should "list all leaf paths of a sub path" in {
    val paths = jsonSource.retrieveJsonPaths(Uri("persons"), depth = Int.MaxValue, limit = None, leafPathsOnly = true, innerPathsOnly = false)
    paths.map(_.serializeSimplified) mustBe Seq("id", "name", "phoneNumbers/type", "phoneNumbers/number")
  }

  it should "list all leaf paths of depth 1 of a sub path" in {
    val paths = jsonSource.retrieveJsonPaths(Uri("persons"), depth = 1, limit = None, leafPathsOnly = true, innerPathsOnly = false)
    paths.map(_.serializeSimplified) mustBe Seq("id", "name")
  }

  it should "return valid URIs for resource paths" in {
    val result = jsonSource.retrieve(EntitySchema(Uri(""), typedPaths = IndexedSeq(Path.parse("/persons").asStringTypedPath)))
    val uris = result.flatMap(_.values.flatten).toSeq
    for(uri <- uris) {
      assert(Uri(uri).isValidUri, s"URI $uri was not valid!")
    }
    uris.distinct.size mustBe uris.size
  }
}
