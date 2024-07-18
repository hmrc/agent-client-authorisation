/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, EmailStub}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class EmailConnectorISpec extends UnitSpec with AppAndStubs with EmailStub {

  val connector = app.injector.instanceOf[EmailConnector]

  val arn = Arn("TARN0000001")
  val nino = Nino("AB123456A")
  val mtdItId = MtdItId("LC762757D")
  val vrn = Vrn("101747641")

  "sendEmail" should {
    val emailInfo = EmailInformation(Seq("abc@xyz.com"), "template-id", Map("param1" -> "foo", "param2" -> "bar"))

    "return Unit when the email service responds" in {
      givenEmailSent(emailInfo)

      val result = await(connector.sendEmail(emailInfo))

      result shouldBe (())
    }
    "not throw an Exception when the email service throws an Exception" in {
      givenEmailReturns500

      val result = await(connector.sendEmail(emailInfo))

      result shouldBe (())
    }
  }
}
