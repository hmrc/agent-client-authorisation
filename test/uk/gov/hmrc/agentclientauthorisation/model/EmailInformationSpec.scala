/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.model

import uk.gov.hmrc.play.test.UnitSpec
import play.api.libs.json.Json.toJson

class EmailInformationSpec extends UnitSpec {

  val emails = Seq("someone@something.go.global")
  val templateId = "client_accepted_email"
  val parametersAccept = Map(
    "agentName"  -> "Agent 1",
    "clientName" -> "Client 2",
    "service1"   -> "Accept ITSA",
    "service2"   -> "Accept IRV",
    "service3"   -> "Accept VAT")

  "EmailInformation" should {
    "return accept email info" in {
      val emailAcceptInfo = EmailInformation(emails, templateId, parametersAccept, false, None, None)

      val json = toJson(emailAcceptInfo)

      (json \ "to").as[Seq[String]] shouldBe emails
      (json \ "templateId").as[String] shouldBe templateId
      (json \ "parameters").as[Map[String, String]] shouldBe parametersAccept
    }
  }

}
