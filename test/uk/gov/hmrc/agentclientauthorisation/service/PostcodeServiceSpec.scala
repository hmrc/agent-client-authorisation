/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class PostcodeServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val desConnector: DesConnector = mock[DesConnector]

  val service = new PostcodeService(desConnector)

  implicit val hc = HeaderCarrier()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(desConnector)
  }

  "clientPostcodeMatches" should {
    "return no error status if there is a business partner record with a matching UK postcode" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(
          BusinessDetails(Array(BusinessData(BusinessAddressDetails("GB", Some("AA11AA")))), Some(MtdItId("mtdItId")))))
      val maybeResult = await(service.clientPostcodeMatches(nino1.value, "AA11AA"))
      maybeResult shouldBe None
    }

    "return a 501 if there is a business partner record with a matching non-UK postcode" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(
          BusinessDetails(Array(BusinessData(BusinessAddressDetails("US", Some("AA11AA")))), Some(MtdItId("mtdItId")))))
      val result = await(service.clientPostcodeMatches(nino1.value, "AA11AA")).head
      result.header.status shouldBe 501
    }

    "return 403 if there is a business partner record with a mis-matched postcode" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(
          BusinessDetails(Array(BusinessData(BusinessAddressDetails("GB", Some("ZZ99ZZ")))), Some(MtdItId("mtdItId")))))
      val result = await(service.clientPostcodeMatches(nino1.value, "AA11AA")).head
      result.header.status shouldBe 403
    }

    "return 403 if there is a business partner record with a no postcode" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(
          BusinessDetails(Array(BusinessData(BusinessAddressDetails("GB", None))), Some(MtdItId("mtdItId")))))
      val result = await(service.clientPostcodeMatches(nino1.value, "AA11AA")).head
      result.header.status shouldBe 403
    }

    "return 403 if no business partner record is found" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(Future successful None)
      val result = await(service.clientPostcodeMatches(nino1.value, "AA11AA")).head
      result.header.status shouldBe 403
    }

    "return 403 if a business partner record is returned with no business data records" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(BusinessDetails(Array(), Some(MtdItId("mtdItId")))))
      val result = await(service.clientPostcodeMatches(nino1.value, "AA11AA")).head
      result.header.status shouldBe 403
    }

    "return 403 if a business partner record is returned with multiple business data records" in {
      when(desConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(
          BusinessDetails(
            Array(
              BusinessData(BusinessAddressDetails("GB", Some("AA11AA"))),
              BusinessData(BusinessAddressDetails("GB", Some("AA11AA")))),
            Some(MtdItId("mtdItId")))))
      val result = await(service.clientPostcodeMatches(nino1.value, "AA11AA")).head
      result.header.status shouldBe 403
    }
  }
}
