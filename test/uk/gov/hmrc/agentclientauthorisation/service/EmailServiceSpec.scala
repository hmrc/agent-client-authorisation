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

///*
// * Copyright 2019 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.agentclientauthorisation.service
//import org.joda.time.LocalDate
//import org.mockito.Mockito._
//import org.scalatest.BeforeAndAfter
//import org.slf4j
//import play.api.LoggerLike
//import play.api.i18n.{Lang, Langs, Messages, MessagesApi}
//import play.api.mvc.{RequestHeader, Result}
//import play.mvc.Http
//import reactivemongo.bson.BSONObjectID
//import uk.gov.hmrc.agentclientauthorisation.config.{AppConfig}
//import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, EmailConnector}
//import uk.gov.hmrc.agentclientauthorisation.model._
//import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
//import uk.gov.hmrc.agentclientauthorisation.support.MocksWithCache
//import uk.gov.hmrc.agentmtdidentifiers.model._
//import uk.gov.hmrc.domain.Nino
//import uk.gov.hmrc.http.HeaderCarrier
//import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
//import uk.gov.hmrc.play.test.UnitSpec
//import scala.concurrent.duration._
//
//import scala.collection.mutable.ListBuffer
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.duration.Duration
//import scala.concurrent.{ExecutionContext, Future}
//
//class EmailServiceSpec extends UnitSpec with MocksWithCache with BeforeAndAfter {
//
//  val clientNameService: ClientNameService =
//    new ClientNameService(
//      mockAgentServicesAccountConnector,
//      mockCitizenDetailsConnector,
//      mockDesConnector,
//      agentCacheProvider)
//
//  val mockEmailConnector: EmailConnector = mock[EmailConnector]
//  val mockAsaConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]
//  val mockInvitationRepository: InvitationsRepository = mock[InvitationsRepository]
//
//  val servicesConfig = mock[ServicesConfig]
//  val mockAppConfig: AppConfig = new AppConfig(servicesConfig){
//
//  }
//
//  //(mockAppConfig.invitationExpiringDuration)..returning(14 days )
//
//  implicit val langs: Langs = new Langs{
//    override def availables: Seq[Lang] = Seq(lang)
//
//    override def preferred(candidates: Seq[Lang]): Lang = lang
//  }
//
//  implicit val lang: Lang = Lang("en")
//
//
//
//  val testLogger: LoggerLikeStub.type = LoggerLikeStub
//
//  val mockMessagesApi: MessagesApi = new MessagesApi {
//    override def messages: Map[String, Map[String, String]]
//
//    = ???
//
//    override def preferred(candidates: Seq[Lang]): Messages
//
//    = ???
//
//    override def preferred(request: RequestHeader): Messages
//
//    = ???
//
//    override def preferred(request: Http.RequestHeader): Messages
//
//    = ???
//
//    override def setLang(result: Result, lang: Lang): Result
//
//    = ???
//
//    override def clearLang(result: Result): Result
//
//    = ???
//
//    override def apply(key: String, args: Any*)(implicit lang: Lang): String
//
//    = "dummy response"
//
//    override def apply(keys: Seq[String], args: Any*)(implicit lang: Lang): String
//
//    = ???
//
//    override def translate(key: String, args: Seq[Any])(implicit lang: Lang): Option[String]
//
//    = ???
//
//    override def isDefinedAt(key: String)(implicit lang: Lang): Boolean
//
//    = ???
//
//    override def langCookieName: String
//
//    = ???
//
//    override def langCookieSecure: Boolean
//
//    = ???
//
//    override def langCookieHttpOnly: Boolean
//
//    = ???
//  }
//
//  before {
//    testLogger.errorList.clear()
//    testLogger.warnList.clear()
//  }
//
//  implicit val hc = HeaderCarrier()
//
//  val arn = Arn("TARN0000001")
//
//  val emailService =
//    new EmailService(
//      mockAsaConnector,
//      clientNameService,
//      mockEmailConnector,
//      mockInvitationRepository,
//      mockAppConfig.asInstanceOf[AppConfig],
//      mockMessagesApi)(langs) {
//      override def getLogger: LoggerLike = testLogger
//    }
//
//  val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)
//  val clientIdentifierIrv = ClientIdentifier("AA000003D", NinoType.id)
//  val dfe = DetailsForEmail("abc@def.com", "Mr Agent", "Mr Client")
//  val invite: Invitation = Invitation(
//    BSONObjectID.generate(),
//    InvitationId("ATSNMKR6P6HRL"),
//    arn,
//    Some("personal"),
//    Service.MtdIt,
//    MtdItId("LCLG57411010846"),
//    MtdItId("LCLG57411010846"),
//    LocalDate.parse("2010-01-01"),
//    Some(dfe),
//    Some("continue/url"),
//    List.empty
//  )
//
//  //TODO rearrange to use forEach
//
//  "sendAcceptedEmail" should {
//    "return Unit for a successfully sent email for ITSA service" in {
//      mockMessagesApi.apply("services.HMRC-MTD-IT")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(())
//
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future(()))
//
//      await(emailService.sendAcceptedEmail(invite))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//
//    }
//    "return Unit for a successfully sent email for IRV service" in {
//      mockMessagesApi.apply("services.PERSONAL-INCOME-RECORD")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(())
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
//      await(
//        emailService.sendAcceptedEmail(Invitation(
//          BSONObjectID.generate(),
//          InvitationId("BTSNMKR6P6HRL"),
//          arn,
//          Some("personal"),
//          Service.PersonalIncomeRecord,
//          Nino("AB123456A"),
//          Nino("AB123456A"),
//          LocalDate.parse("2010-01-01"),
//          Some(dfe),
//          Some("continue/url"),
//          List.empty
//        )))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//    }
//    "return Unit for a successfully sent email for VAT service" in {
//      mockMessagesApi.apply("services.PERSONAL-INCOME-RECORD")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(())
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
//      await(
//        emailService.sendAcceptedEmail(Invitation(
//          BSONObjectID.generate(),
//          InvitationId("CTSNMKR6P6HRL"),
//          arn,
//          Some("personal"),
//          Service.Vat,
//          Vrn("555219930"),
//          Vrn("555219930"),
//          LocalDate.parse("2010-01-01"),
//          Some(dfe),
//          Some("continue/url"),
//          List.empty
//        )))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//    }
//
//    "return Unit to remove email details for Trust" in {
//      mockMessagesApi.apply("services.HMRC-TERS-ORG")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(())
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
//      await(
//        emailService.sendAcceptedEmail(Invitation(
//          BSONObjectID.generate(),
//          InvitationId("DTSNMKR6P6HRL"),
//          arn,
//          Some("business"),
//          Service.Trust,
//          Utr("555219930"),
//          Utr("555219930"),
//          LocalDate.parse("2010-01-01"),
//          Some(dfe),
//          Some("continue/url"),
//          List.empty
//        )))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//    }
//
//    "return Unit to remove email details for CGT" in {
//      mockMessagesApi.apply("services.HMRC-CGT-PD")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(())
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
//      await(
//        emailService.sendAcceptedEmail(Invitation(
//          BSONObjectID.generate(),
//          InvitationId("EWR3O9WB6E7TY"),
//          arn,
//          Some("business"),
//          Service.CapitalGains,
//          CgtRef("XMCGTP267321143"),
//          CgtRef("XMCGTP267321143"),
//          LocalDate.parse("2010-01-01"),
//          Some(dfe),
//          Some("continue/url"),
//          List.empty
//        )))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//    }
//
//    "will log an error if sending an email fails but will return successful" in {
//
//      val exception = new Exception("some kind of problem")
//      mockMessagesApi.apply("services.HMRC-MTD-IT")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(Future.failed(exception))
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
////      await(
////        emailService.sendAcceptedEmail(Invitation(
////          BSONObjectID.generate(),
////          InvitationId("ATSNMKR6P6HRL"),
////          arn,
////          Some("personal"),
////          Service.MtdIt,
////          MtdItId("LCLG57411010846"),
////          MtdItId("LCLG57411010846"),
////          LocalDate.parse("2010-01-01"),
////          Some(dfe),
////          Some("continue/url"),
////          List.empty
////        ))) shouldBe (())
////
////      testLogger.checkWarnsWithErrors("sending email failed", exception)
//    }
//  }
//  "sendRejectEmail" should {
//    "return unit for a successfully sent rejection email" in {
//      mockMessagesApi.apply("services.HMRC-MTD-IT")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, hc, *)
//        .returns(())
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
//      await(
//        emailService.sendRejectedEmail(Invitation(
//          BSONObjectID.generate(),
//          InvitationId("ATSNMKR6P6HRL"),
//          arn,
//          Some("personal"),
//          Service.MtdIt,
//          MtdItId("LCLG57411010846"),
//          MtdItId("LCLG57411010846"),
//          LocalDate.parse("2010-01-01"),
//          Some(dfe),
//          Some("continue/url"),
//          List.empty
//        )))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//    }
//  }
//
//  "sendAuthExpiredEmail" should {
//    "return unit for a successfully sent authorisation expired email" in {
//      mockMessagesApi.apply("services.HMRC-MTD-IT")
//      (mockEmailConnector
//        .sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext))
//        .expects(*, *, *)
//        .returns(())
//      (mockInvitationRepository
//        .removeEmailDetails(_: Invitation)(_: ExecutionContext))
//        .expects(*, *)
//        .returns(Future((): Unit))
//
//      await(
//        emailService.sendExpiredEmail(Invitation(
//          BSONObjectID.generate(),
//          InvitationId("ATSNMKR6P6HRL"),
//          arn,
//          Some("personal"),
//          Service.MtdIt,
//          MtdItId("LCLG57411010846"),
//          MtdItId("LCLG57411010846"),
//          LocalDate.parse("2010-01-01"),
//          Some(dfe),
//          Some("continue/url"),
//          List.empty
//        )))
//
//      verify(mockEmailConnector).sendEmail(_: EmailInformation)(_: HeaderCarrier, _: ExecutionContext)
//    }
//  }
//
//}
//
//object LoggerLikeStub extends LoggerLike with UnitSpec {
//
//  override val logger: slf4j.Logger = null
//  val errorList: ListBuffer[(String, Throwable)] = ListBuffer.empty
//  val warnWithErrorList: ListBuffer[(String, Throwable)] = ListBuffer.empty
//  val warnList: ListBuffer[String] = ListBuffer.empty
//
//  def error(message: => String, error: => Throwable): Unit = {
//    errorList += ((message, error))
//    ()
//  }
//
//  def warn(message: => String, error: => Throwable): Unit = {
//    warnWithErrorList += ((message, error))
//    ()
//  }
//
//  def warn(message: => String): Unit = {
//    warnList += message
//    ()
//  }
//
//  def checkErrors(expectMsg: String, expectError: Throwable) =
//    errorList should contain((expectMsg, expectError))
//
//  def checkWarnsWithErrors(expectMsg: String, expectError: Throwable) =
//    warnWithErrorList should contain((expectMsg, expectError))
//
//  def checkWarns(expectMsg: String) =
//    warnList should contain(expectMsg)
//
//}
