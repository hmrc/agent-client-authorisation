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

package uk.gov.hmrc.agentclientauthorisation.service
import com.kenshoo.play.metrics.Metrics
import org.joda.time.LocalDate
import org.mockito.Mockito._
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfter
import org.slf4j
import play.api.LoggerLike
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.mvc.{RequestHeader, Result}
import play.mvc.Http
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgencyNameNotFound, AgentServicesAccountConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{AgencyEmailNotFound, EmailInformation, Invitation, Service}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class EmailServiceSpec extends UnitSpec with MockFactory with BeforeAndAfter {

  val mockMetrics: Metrics = mock[Metrics]
  val mockClientNameService: ClientNameService = mock[ClientNameService]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  val mockAsaConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]
  val testLogger = LoggerLikeStub

  val mockMessagesApi: MessagesApi = new MessagesApi {
    override def messages: Map[String, Map[String, String]] = ???

    override def preferred(candidates: Seq[Lang]): Messages = ???

    override def preferred(request: RequestHeader): Messages = ???

    override def preferred(request: Http.RequestHeader): Messages = ???

    override def setLang(result: Result, lang: Lang): Result = ???

    override def clearLang(result: Result): Result = ???

    override def apply(key: String, args: Any*)(implicit lang: Lang): String = "dummy response"

    override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang): String = ???

    override def translate(key: String, args: Seq[Any])(implicit lang: Lang): Option[String] = ???

    override def isDefinedAt(key: String)(implicit lang: Lang): Boolean = ???

    override def langCookieName: String = ???

    override def langCookieSecure: Boolean = ???

    override def langCookieHttpOnly: Boolean = ???
  }

  before {
    testLogger.errorList.clear()
  }

  implicit val hc = HeaderCarrier()

  val arn = Arn("TARN0000001")

  val emailService =
    new EmailService(mockAsaConnector, mockClientNameService, mockEmailConnector, mockMessagesApi) {
      override def getLogger: LoggerLike = testLogger
    }

  "sendAcceptedEmail" should {
    "return Unit for a successfully sent email for ITSA service" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future(Some("Mr Agent")))
      (mockClientNameService
        .getClientNameByService(_: String, _: Service)(_: HeaderCarrier, _: ExecutionContext))
        .expects("LCLG57411010846", Service.MtdIt, hc, *)
        .returns(Future(Some("Mr Client")))
      mockMessagesApi.apply("services.HMRC-MTD-IT")
      (mockEmailConnector
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, hc, *)
        .returns(())

      await(
        emailService.sendAcceptedEmail(Invitation(
          BSONObjectID.generate(),
          InvitationId("ATSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.MtdIt,
          MtdItId("LCLG57411010846"),
          MtdItId("LCLG57411010846"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"),
          List.empty
        )))

      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)

    }
    "return 204 for a successfully sent email for IRV service" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future(Some("Mr Agent")))
      (mockClientNameService
        .getClientNameByService(_: String, _: Service)(_: HeaderCarrier, _: ExecutionContext))
        .expects("AB123456A", Service.PersonalIncomeRecord, hc, *)
        .returns(Future(Some("Mr Client")))
      mockMessagesApi.apply("services.PERSONAL-INCOME-RECORD")
      (mockEmailConnector
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, hc, *)
        .returns(())

      await(
        emailService.sendAcceptedEmail(Invitation(
          BSONObjectID.generate(),
          InvitationId("BTSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.PersonalIncomeRecord,
          Nino("AB123456A"),
          Nino("AB123456A"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"),
          List.empty
        )))

      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
    }
    "return 204 for a successfully sent email for VAT service" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future(Some("Mr Agent")))
      (mockClientNameService
        .getClientNameByService(_: String, _: Service)(_: HeaderCarrier, _: ExecutionContext))
        .expects("555219930", Service.Vat, hc, *)
        .returns(Future(Some("Mr Client")))
      mockMessagesApi.apply("services.PERSONAL-INCOME-RECORD")
      (mockEmailConnector
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, hc, *)
        .returns(())

      await(
        emailService.sendAcceptedEmail(Invitation(
          BSONObjectID.generate(),
          InvitationId("CTSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.Vat,
          Vrn("555219930"),
          Vrn("555219930"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"),
          List.empty
        )))

      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
    }
    "throw an AgencyEmailNotFound exception when there is no agency email" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .throws(new AgencyEmailNotFound)

      intercept[AgencyEmailNotFound] {
        await(
          emailService.sendAcceptedEmail(Invitation(
            BSONObjectID.generate(),
            InvitationId("ATSNMKR6P6HRL"),
            arn,
            Some("personal"),
            Service.MtdIt,
            MtdItId("LCLG57411010846"),
            MtdItId("LCLG57411010846"),
            LocalDate.parse("2010-01-01"),
            Some("continue/url"),
            List.empty
          )))
      }
    }
    "throw an AgencyNameNotFoundException when there is no agency name" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .throws(new AgencyNameNotFound)

      intercept[AgencyNameNotFound] {
        await(
          emailService.sendAcceptedEmail(Invitation(
            BSONObjectID.generate(),
            InvitationId("ATSNMKR6P6HRL"),
            arn,
            Some("personal"),
            Service.MtdIt,
            MtdItId("LCLG57411010846"),
            MtdItId("LCLG57411010846"),
            LocalDate.parse("2010-01-01"),
            Some("continue/url"),
            List.empty
          )))
      }
    }
    "will log an error if sending an email fails but will return successful" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future(Some("Mr Agent")))

      (mockClientNameService
        .getClientNameByService(_: String, _: Service)(_: HeaderCarrier, _: ExecutionContext))
        .expects("LCLG57411010846", Service.MtdIt, hc, *)
        .returns(Future(Some("Mr Client")))

      val exception = new Exception("some kind of problem")

      mockMessagesApi.apply("services.HMRC-MTD-IT")
      (mockEmailConnector
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, hc, *)
        .returns(Future.failed(exception))

      await(
        emailService.sendAcceptedEmail(Invitation(
          BSONObjectID.generate(),
          InvitationId("ATSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.MtdIt,
          MtdItId("LCLG57411010846"),
          MtdItId("LCLG57411010846"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"),
          List.empty
        ))) shouldBe ()

      testLogger.checkErrors("sending email failed", exception)
    }
  }
  "sendRejectEmail" should {
    "return unit for a successfully sent rejection email" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future(Some("Mr Agent")))
      (mockClientNameService
        .getClientNameByService(_: String, _: Service)(_: HeaderCarrier, _: ExecutionContext))
        .expects("LCLG57411010846", Service.MtdIt, hc, *)
        .returns(Future(Some("Mr Client")))
      mockMessagesApi.apply("services.HMRC-MTD-IT")
      (mockEmailConnector
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, hc, *)
        .returns(())

      await(
        emailService.sendRejectedEmail(Invitation(
          BSONObjectID.generate(),
          InvitationId("ATSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.MtdIt,
          MtdItId("LCLG57411010846"),
          MtdItId("LCLG57411010846"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"),
          List.empty
        )))

      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
    }
  }

  "sendAuthExpiredEmail" should {
    "return unit for a successfully sent authorisation expired email" in {
      (mockAsaConnector
        .getAgencyEmailBy(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future("abc@def.com"))
      (mockAsaConnector
        .getAgencyNameViaClient(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, hc, *)
        .returns(Future(Some("Mr Agent")))
      (mockClientNameService
        .getClientNameByService(_: String, _: Service)(_: HeaderCarrier, _: ExecutionContext))
        .expects("LCLG57411010846", Service.MtdIt, hc, *)
        .returns(Future(Some("Mr Client")))
      mockMessagesApi.apply("services.HMRC-MTD-IT")
      (mockEmailConnector
        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, hc, *)
        .returns(())

      await(
        emailService.sendAuthExpiredEmail(Invitation(
          BSONObjectID.generate(),
          InvitationId("ATSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.MtdIt,
          MtdItId("LCLG57411010846"),
          MtdItId("LCLG57411010846"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"),
          List.empty
        )))

      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
    }
  }

}

object LoggerLikeStub extends LoggerLike with UnitSpec {

  override val logger: slf4j.Logger = null
  val errorList: ListBuffer[(String, Throwable)] = ListBuffer.empty

  override def error(message: => String, error: => Throwable) =
    errorList += ((message, error))

  def checkErrors(expectMsg: String, expectError: Throwable) =
    errorList should contain(expectMsg, expectError)
}
