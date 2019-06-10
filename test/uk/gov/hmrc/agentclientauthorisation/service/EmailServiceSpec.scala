package uk.gov.hmrc.agentclientauthorisation.service
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.joda.time.LocalDate
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import play.api.i18n.MessagesApi
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Service}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailServiceSpec extends UnitSpec with MockFactory {

  val mockClientNameService: ClientNameService = mock[ClientNameService]
  val mockAsaConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  val mockMessagesApi: MessagesApi = mock[MessagesApi]
  implicit val hc = HeaderCarrier()

  val arn = Arn("TARN0000001")

  val emailService = new EmailService(mockAsaConnector, mockClientNameService, mockEmailConnector, mockMessagesApi)

  "sendAcceptedEmail" should {
    "return 204 for a successfully sent email" in {

      (mockAsaConnector.getAgencyEmailBy _).when(arn).returns(Future("abc@def.com"))
      (mockAsaConnector.getAgencyNameViaClient _).when(arn).returns(Future(Some("Mr Agent")))
      (mockClientNameService.getClientNameByService _).when("LCLG57411010846", Service.MtdIt).returns(Future(Some("Mr Client")))

      val result = await(emailService.sendAcceptedEmail(
        Invitation(
          BSONObjectID.generate(),
          InvitationId("ATSNMKR6P6HRL"),
          arn,
          Some("personal"),
          Service.MtdIt,
          MtdItId("LCLG57411010846"),
          MtdItId("LCLG57411010846"),
          LocalDate.parse("2010-01-01"),
          Some("continue/url"), List.empty
        )))

      status(result) shouldBe 202


    }
  }

}
