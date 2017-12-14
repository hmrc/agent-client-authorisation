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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentclientauthorisation.connectors.{BusinessAddressDetails, BusinessData, BusinessDetails, DesConnector}
import uk.gov.hmrc.agentclientauthorisation.model.AgentInvitation
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.agentclientauthorisation.support.AkkaMaterializerSpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

class AgentInvitationValidationSpec extends UnitSpec with AgentInvitationValidation with Results with MockitoSugar with AkkaMaterializerSpec {

  private val desConnector = mock[DesConnector]
  override val postcodeService: PostcodeService = new PostcodeService(desConnector)

  private val validMtdItInvite: AgentInvitation = AgentInvitation("HMRC-MTD-IT", "ni", "AA123456A", Some("AN11PA"))
  private val validPirInvite: AgentInvitation = AgentInvitation("PERSONAL-INCOME-RECORD", "ni", "AA123456A", None)
  private implicit val hc = HeaderCarrier()

  private implicit class ResultChecker(r: Result) {
    def is(r1: Result) = status(r1) shouldBe status(r)
  }

  private def responseFor(invite: AgentInvitation): Result = {
    await(checkForErrors(invite)).get
  }
  private def mockOutCallToDesWithPostcode(postcode: String = "AN11PA") =
    when(desConnector.getBusinessDetails(any[Nino])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(
      Future successful Some(BusinessDetails(Array(BusinessData(BusinessAddressDetails("GB", Some(postcode)))), None)))

  "checkForErrors" should {

    "fail with Forbidden if the postcode doesn't match" in {
      mockOutCallToDesWithPostcode()
      responseFor(validMtdItInvite.copy(clientPostcode = Some("BN29AB"))) is Forbidden
    }

    "fail with BadRequest if the postcode is not valid" in {
      mockOutCallToDesWithPostcode()
      responseFor(validMtdItInvite.copy(clientPostcode = Some("AAAAAA"))) is BadRequest
    }

    "fail with NotImplemented if the service is not HMRC-MTD-IT" in {
      mockOutCallToDesWithPostcode()
      responseFor(validMtdItInvite.copy(service = "mtd-vat")) is NotImplemented
    }

    "fail with BadRequest if the service is HMRC-MTD-IT and no postcode provided" in {
      mockOutCallToDesWithPostcode()
      responseFor(validMtdItInvite.copy(clientPostcode = None)) is BadRequest
    }

    "only perform NINO format validation after establishing that clientId is a NINO " in {
      mockOutCallToDesWithPostcode()
      val result: Result = responseFor(validMtdItInvite.copy(clientIdType = "not nino", clientId = "not a valid NINO"))
      (jsonBodyOf(result) \ "code").as[String] shouldBe "UNSUPPORTED_CLIENT_ID_TYPE"
    }

    "pass when the postcode is valid, matches and has HMRC-MTD-IT as the service" in {
      mockOutCallToDesWithPostcode()
      await(checkForErrors(validMtdItInvite)) shouldBe None
    }

    "pass when the postcodes differ by case" in {
      mockOutCallToDesWithPostcode("an11pa")
      await(checkForErrors(validMtdItInvite)) shouldBe None

      mockOutCallToDesWithPostcode()
      await(checkForErrors(validMtdItInvite.copy(clientPostcode = Some("an11pa")))) shouldBe None
    }

    "pass when the postcodes differ by spacing" in {
      mockOutCallToDesWithPostcode("AN1 1PA")
      await(checkForErrors(validMtdItInvite)) shouldBe None

      mockOutCallToDesWithPostcode()
      await(checkForErrors(validMtdItInvite.copy(clientPostcode = Some("AN1 1PA")))) shouldBe None
    }

    "pass when no postcode is present and service is of type PersonalIncomeRecord" in {
      await(checkForErrors(validPirInvite)) shouldBe None
    }

    "pass when no postcode is present and service is of type " in {
      responseFor(validPirInvite.copy(service = "HMRC-MTD-IT")) is BadRequest
    }
  }
}
