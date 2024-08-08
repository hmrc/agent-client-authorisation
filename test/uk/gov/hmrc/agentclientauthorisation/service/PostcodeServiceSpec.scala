/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.model.{BusinessAddressDetails, BusinessData, BusinessDetails}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentclientauthorisation.util.FailedResultException
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class PostcodeServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val ifConnector: IfConnector = mock[IfConnector]

  val service = new PostcodeService(ifConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(ifConnector)
  }

  "clientPostcodeMatches" should {
    "return no error status if there is a business partner record with a matching UK postcode" in {
      when(ifConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(BusinessDetails(Seq(BusinessData(Some(BusinessAddressDetails("GB", Some("AA11AA"))))), Some(MtdItId("mtdItId"))))
      )

      val maybeResult = await(service.postCodeMatches(nino1.value, "AA11AA"))
      maybeResult shouldBe (())
    }

    "return no error status if there are multiple business partner record with a matching UK postcode" in {
      when(ifConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(
          BusinessDetails(
            Seq(
              BusinessData(Some(BusinessAddressDetails("GB", Some("AA11AA")))),
              BusinessData(Some(BusinessAddressDetails("GB", Some("AA11AB"))))
            ),
            Some(MtdItId("mtdItId"))
          )
        )
      )

      val maybeResult = await(service.postCodeMatches(nino1.value, "AA11AA"))
      maybeResult shouldBe (())
    }

    "fail with a 501 if there is a business partner record with a matching non-UK postcode" in {
      when(ifConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(BusinessDetails(Seq(BusinessData(Some(BusinessAddressDetails("US", Some("AA11AA"))))), Some(MtdItId("mtdItId"))))
      )

      await(service.postCodeMatches(nino1.value, "AA11AA").failed) match {
        case FailedResultException(r) => r.header.status shouldBe 501
      }
    }

    "fail with a 403 if there is a business partner record with a mis-matched postcode" in {
      when(ifConnector.getBusinessDetails(nino1)).thenReturn(
        Future successful Some(BusinessDetails(Seq(BusinessData(Some(BusinessAddressDetails("GB", Some("ZZ99ZZ"))))), Some(MtdItId("mtdItId"))))
      )
      await(service.postCodeMatches(nino1.value, "AA11AA").failed) match {
        case FailedResultException(r) => r.header.status shouldBe 403
      }
    }

    "return 403 if there is a business partner record with a no postcode" in {
      when(ifConnector.getBusinessDetails(nino1))
        .thenReturn(Future successful Some(BusinessDetails(Seq(BusinessData(Some(BusinessAddressDetails("GB", None)))), Some(MtdItId("mtdItId")))))
      await(service.postCodeMatches(nino1.value, "AA11AA").failed) match {
        case FailedResultException(r) => r.header.status shouldBe 403
      }
    }

    "return 403 if no business partner record is found" in {
      when(ifConnector.getBusinessDetails(nino1)).thenReturn(Future successful None)
      await(service.postCodeMatches(nino1.value, "AA11AA").failed) match {
        case FailedResultException(r) => r.header.status shouldBe 403
      }
    }

    "return 403 if a business partner record is returned with no business data records" in {
      when(ifConnector.getBusinessDetails(nino1))
        .thenReturn(Future successful Some(BusinessDetails(Seq(), Some(MtdItId("mtdItId")))))
      await(service.postCodeMatches(nino1.value, "AA11AA").failed) match {
        case FailedResultException(r) => r.header.status shouldBe 403
      }
    }

  }
}
