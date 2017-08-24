/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers.actions

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentclientauthorisation.connectors.{BusinessAddressDetails, BusinessData, BusinessDetails, DesConnector}
import uk.gov.hmrc.agentclientauthorisation.model.AgentInvitation
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.agentclientauthorisation.support.AkkaMaterializerSpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AgentInvitationValidationSpec extends UnitSpec with AgentInvitationValidation with Results with MockitoSugar with AkkaMaterializerSpec {

  private val desConnector = mock[DesConnector]
  override val postcodeService: PostcodeService = new PostcodeService(desConnector)

  private val validInvite: AgentInvitation = AgentInvitation("HMRC-MTD-IT", "ni", "AA123456A", "AN11PA")
  private implicit val hc = HeaderCarrier()

  private implicit class ResultChecker(r: Result) {
    def is(r1: Result) = status(r1) shouldBe status(r)
  }

  private def responseFor(invite: AgentInvitation): Result = {
    await(checkForErrors(invite)).get
  }
  private def postcodeCheck(postcode: String = "AN11PA") =
    when(desConnector.getBusinessDetails(any[Nino])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(
      Future successful Some(BusinessDetails(Array(BusinessData(BusinessAddressDetails("GB", Some(postcode)))), None)))

  "checkForErrors" should {

    "fail with Forbidden if the postcode doesn't match" in {
      postcodeCheck()
      responseFor(validInvite.copy(clientPostcode = "BN29AB")) is Forbidden
    }

    "fail with BadRequest if the postcode is not valid" in {
      postcodeCheck()
      responseFor(validInvite.copy(clientPostcode = "AAAAAA")) is BadRequest
    }

    "fail with NotImplemented if the service is not HMRC-MTD-IT" in {
      postcodeCheck()
      responseFor(validInvite.copy(service = "mtd-vat")) is NotImplemented
    }

    "only perform NINO format validation after establishing that clientId is a NINO " in {
      postcodeCheck()
      val result: Result = responseFor(validInvite.copy(clientIdType = "not nino", clientId = "not a valid NINO"))
      (jsonBodyOf(result) \ "code").as[String] shouldBe "UNSUPPORTED_CLIENT_ID_TYPE"
    }

    "pass when the postcode is valid, matches and has HMRC-MTD-IT as the service" in {
      postcodeCheck()
      await(checkForErrors(validInvite)) shouldBe None
    }

    "pass when the postcodes differ by case" in {
      postcodeCheck("an11pa")
      await(checkForErrors(validInvite)) shouldBe None

      postcodeCheck()
      await(checkForErrors(validInvite.copy(clientPostcode = "an11pa"))) shouldBe None
    }

    "pass when the postcodes differ by spacing" in {
      postcodeCheck("AN1 1PA")
      await(checkForErrors(validInvite)) shouldBe None

      postcodeCheck()
      await(checkForErrors(validInvite.copy(clientPostcode = "AN1 1PA"))) shouldBe None
    }
  }
}
