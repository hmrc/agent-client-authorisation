package uk.gov.hmrc.agentclientauthorisation.connectors
import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.BaseISpec
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.Future

class AuthActionsISpec extends BaseISpec {

  val authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
  val metrics: Metrics = app.injector.instanceOf[Metrics]

  object TestController extends AuthActions(metrics, authConnector) {

    implicit val hc = HeaderCarrier()

    import scala.concurrent.ExecutionContext.Implicits.global

    def testWithAuthorisedAsClientOrStride(service: String, identifier: String, strideRoles: Seq[String]): Action[AnyContent] =
      super.AuthorisedClientOrStrideUser(service, identifier, strideRoles) {
        _ => _ => Future.successful(Ok)
      }
  }

  class LoggedinUser(forStride: Boolean, forClient: Boolean) {
    if(forStride)
      givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
    else if(forClient)
      givenClientAll(mtdItId, vrn, nino, utr, cgtRef)
    else isNotLoggedIn
  }

  case class TestClient(
                         clientType: Option[String],
                         service: Service,
                         urlIdentifier: String,
                         clientId: TaxIdentifier,
                         suppliedClientId: TaxIdentifier)

  val itsaClient = TestClient(personal, Service.MtdIt, "MTDITID", mtdItId, nino)
  val irvClient = TestClient(personal, Service.PersonalIncomeRecord, "NI", nino, nino)
  val vatClient = TestClient(personal, Service.Vat, "VRN", vrn, vrn)
  val trustClient = TestClient(business, Service.Trust, "UTR", utr, utr)

  val list = List(itsaClient, irvClient, vatClient, trustClient)

  "AuthorisedClientOrStrideUser" should {
    list.foreach {
      client =>
        runHappyLoginScenario(client, true, false)
        runHappyLoginScenario(client, false, true)
    }
    runUnHappyLoginScenario(true, false)
    runUnHappyLoginScenario(false, true)

    s"return 401 for no login" in new LoggedinUser(false, false) {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("UTR", utr.value, strideRoles)(request)

      status(result) shouldBe 401
    }

    s"return 403 if not logged in through GG or PA" in {
      isNotGGorPA
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("UTR", utr.value, strideRoles)(request)

      status(result) shouldBe 403
    }
  }

  def runHappyLoginScenario(testClient: TestClient, forStride: Boolean, forClient: Boolean) = {
    s"return 200 for successful Login if user was ${if(forStride) "stride" else "client"} and requesting ${testClient.service.id}" in new LoggedinUser(forStride, forClient) {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride(testClient.urlIdentifier, testClient.clientId.value, strideRoles)(request)

      status(result) shouldBe 200
    }
  }

  def runUnHappyLoginScenario(forStride: Boolean, forClient: Boolean) = {
    s"return 400 for unsupported Service if user was ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride, forClient) {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("SAUTR", utr.value, strideRoles)(request)

      status(result) shouldBe 400
    }
  }


}
