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

package uk.gov.hmrc.agentclientauthorisation.service

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Langs
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{await, defaultAwaitTimeout, stubControllerComponents}
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Service.Vat
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Service, Utr, Vrn}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EmailServiceSpec extends UnitSpec with MockitoSugar {

  val cc: ControllerComponents = stubControllerComponents()
  implicit val langs: Langs = cc.langs
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockClientNameService: ClientNameService = mock[ClientNameService]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]

  val service = new EmailService(mockDesConnector, mockClientNameService, mockEmailConnector, cc.messagesApi)

  val exampleArn: Arn = Arn("XARN1234567")
  val agencyDetails: AgencyDetails =
    AgencyDetails(Some("Agency Name"), Some("agency@email.com"), Some("01612342345"), None)
  val agencyDetailsResult: AgentDetailsDesResponse =
    AgentDetailsDesResponse(Some(Utr("999888")), Some(agencyDetails), None)

  ".createDetailsForEmail" should {

    "return a DetailsForEmail model" when {

      "all connector calls are successful" in {
        when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(agencyDetailsResult)))

        when(mockClientNameService.getClientNameByService(any[String], any[Service])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some("Erling Haal")))

        val expectedModel = DetailsForEmail("agency@email.com", "Agency Name", "Erling Haal")
        await(service.createDetailsForEmail(exampleArn, Vrn("123456789"), Vat)) shouldBe expectedModel
      }
    }

    "throw an AgencyEmailNotFound exception" when {

      "an agency email cannot be retrieved from the agency details call" in {
        val noEmailResult = agencyDetailsResult.copy(agencyDetails = Some(agencyDetails.copy(agencyEmail = None)))

        when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(noEmailResult)))

        when(mockClientNameService.getClientNameByService(any[String], any[Service])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some("Erling Haal")))

        intercept[AgencyEmailNotFound](await(service.createDetailsForEmail(exampleArn, Vrn("123456789"), Vat)))
      }

      "the call to get agency details returns a None" in {
        when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        when(mockClientNameService.getClientNameByService(any[String], any[Service])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some("Erling Haal")))

        intercept[AgencyEmailNotFound](await(service.createDetailsForEmail(exampleArn, Vrn("123456789"), Vat)))
      }
    }

    "throw an AgencyNameNotFound exception" when {

      "an agency name cannot be retrieved from the agency details call" in {
        val noNameResult = agencyDetailsResult.copy(agencyDetails = Some(agencyDetails.copy(agencyName = None)))

        when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(noNameResult)))

        when(mockClientNameService.getClientNameByService(any[String], any[Service])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some("Erling Haal")))

        intercept[AgencyNameNotFound](await(service.createDetailsForEmail(exampleArn, Vrn("123456789"), Vat)))
      }
    }

    "throw a ClientNameNotFound exception" when {

      "the call to get client name returns a None" in {
        when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(agencyDetailsResult)))

        when(mockClientNameService.getClientNameByService(any[String], any[Service])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        intercept[ClientNameNotFound](await(service.createDetailsForEmail(exampleArn, Vrn("123456789"), Vat)))
      }
    }
  }
}
