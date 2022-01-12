/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.hal.{HalLink, HalLinks, HalResource}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

class HalWriterSpec extends UnitSpec {

  val teddyBear = Toy("Theodore Bear", 0)
  val ball = Toy("Ball", 3)
  val duplo = Toy("Lego Duplo", 1)

  implicit val toyFormat = Json.format[Toy]

  "HalWriter" should {
    "write embedded resources to an array" in {
      val res = HalResource(HalLinks.empty, teddyBear, Vector("likes" -> Vector(ball, duplo)))

      val json = Json.toJson(res)(HalWriter.halWrites)

      json shouldBe Json.parse("""
                                 |{
                                 |  "name": "Theodore Bear",
                                 |  "minAge": 0,
                                 |  "_embedded": {
                                 |    "likes": [
                                 |      {
                                 |        "name": "Ball",
                                 |        "minAge": 3
                                 |      }, {
                                 |        "name": "Lego Duplo",
                                 |        "minAge": 1
                                 |      }
                                 |    ]
                                 |  }
                                 |}
        """.stripMargin)
    }

    "write embedded resources to an array, even if there is only one resource embedded" in {
      val res = HalResource(HalLinks.empty, teddyBear, Vector("likes" -> Vector(ball)))

      val json = Json.toJson(res)(HalWriter.halWrites)

      json shouldBe Json.parse("""
                                 |{
                                 |  "name": "Theodore Bear",
                                 |  "minAge": 0,
                                 |  "_embedded": {
                                 |    "likes": [
                                 |      {
                                 |        "name": "Ball",
                                 |        "minAge": 3
                                 |      }
                                 |    ]
                                 |  }
                                 |}
        """.stripMargin)
    }

    "write multiple heterogeneous elements to several arrays of size 1" in {
      val res = HalResource(HalLinks.empty, teddyBear, Vector("likes" -> Vector(duplo), "hates" -> Vector(ball)))

      val json = Json.toJson(res)(HalWriter.halWrites)

      json shouldBe Json.parse("""
                                 |{
                                 |  "name": "Theodore Bear",
                                 |  "minAge": 0,
                                 |  "_embedded": {
                                 |    "likes": [
                                 |      {
                                 |        "name": "Lego Duplo",
                                 |        "minAge": 1
                                 |      }
                                 |    ],
                                 |    "hates": [
                                 |      {
                                 |        "name": "Ball",
                                 |        "minAge": 3
                                 |      }
                                 |    ]
                                 |  }
                                 |}
        """.stripMargin)
    }

    "do not litter the JSON if there's not any resources" in {
      val res = toSimpleHalResource(teddyBear)

      val json = Json.toJson(res)(HalWriter.halWrites)

      json shouldBe Json.parse("""
                                 |{
                                 |  "name": "Theodore Bear",
                                 |  "minAge": 0
                                 |}
        """.stripMargin)
    }

    "add _links section if there are any links" in {
      val res = toHalResourceWithLink(teddyBear, "bear")

      val json = Json.toJson(res)(HalWriter.halWrites)

      json shouldBe Json.parse("""
                                 |{
                                 |  "name": "Theodore Bear",
                                 |  "minAge": 0,
                                 |  "_links": {
                                 |    "self": { "href": "/toys/bear" }
                                 |  }
                                 |}
        """.stripMargin)
    }

    "add _links even if there are embedded resources as well" in {
      val res = HalResource(
        HalLinks(Vector(HalLink("self", "/toys/bear"))),
        teddyBear,
        Vector("has" -> Vector(toHalResourceWithLink(ball, "ball"), toHalResourceWithLink(duplo, "duplo")))
      )

      val json = Json.toJson(res)(HalWriter.halWrites)

      json shouldBe Json.parse("""
                                 |{
                                 |  "name": "Theodore Bear",
                                 |  "minAge": 0,
                                 |  "_links": {
                                 |    "self": { "href": "/toys/bear" }
                                 |  },
                                 |  "_embedded": {
                                 |    "has": [
                                 |      {
                                 |        "name": "Ball",
                                 |        "minAge": 3,
                                 |        "_links": {
                                 |          "self": { "href": "/toys/ball" }
                                 |        }
                                 |      },
                                 |      {
                                 |        "name": "Lego Duplo",
                                 |        "minAge": 1,
                                 |        "_links": {
                                 |          "self": { "href": "/toys/duplo" }
                                 |        }
                                 |      }
                                 |    ]
                                 |  }
                                 |}
        """.stripMargin)

    }
  }

  implicit def toyToJson(toy: Toy): JsObject = Json.toJson(toy).asInstanceOf[JsObject]

  implicit def toSimpleHalResource(toy: Toy): HalResource = HalResource(HalLinks.empty, toyToJson(toy))

  def toHalResourceWithLink(toy: Toy, id: String): HalResource =
    HalResource(HalLinks(Vector(HalLink("self", s"/toys/$id"))), toyToJson(toy))

}

case class Toy(name: String, minAge: Int)
