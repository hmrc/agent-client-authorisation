package uk.gov.hmrc.agentclientauthorisation.connectors
import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, ControllerComponents, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.BaseISpec
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.Future

class AuthActionsISpec extends BaseISpec {

  val authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
  val metrics: Metrics = app.injector.instanceOf[Metrics]
  val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

  object TestController extends AuthActions(metrics, authConnector, cc) {

    implicit val hc = HeaderCarrier()

    import scala.concurrent.ExecutionContext.Implicits.global

    def testWithAuthorisedAsClientOrStride(service: String, identifier: String, strideRoles: Seq[String]): Action[AnyContent] =
      super.AuthorisedClientOrStrideUser(service, identifier, strideRoles) {
        _ => _ => Future.successful(Ok)
      }

    def testWithOnlyForClients[T <:TaxIdentifier](service: Service, clientIdType: ClientIdType[T]): Action[AnyContent] =
      super.onlyForClients(service, clientIdType) {
        _ => _ => Future.successful(Ok)
      }

    val testWithOnlyForAgents: Action[AnyContent] =
      super.onlyForAgents {
        _ => _ => Future.successful(Ok)
      }

    val testOnlyForStride: Action[AnyContent] =
      super.onlyStride {
        _ => Future.successful(Ok)
      }
  }

  class LoggedInUser(forStride: Boolean, forClient: Boolean) {
    if(forStride)
      givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
    else if(forClient)
      givenClientAll(mtdItId, vrn, nino, utr, cgtRef)
    else isNotLoggedIn
  }

  val testClientList = List(itsaClient, irvClient, vatClient, trustClient)

  "OnlyForAgents" should {
    s"return 200 if logged in as Agent" in {
      givenAuthorisedAsAgent(arn)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithOnlyForAgents(request)
      status(result) shouldBe 200
    }

    s"return 401 if not logged in as Agent" in {
      givenClientAll(mtdItId, vrn, nino, utr, cgtRef)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithOnlyForAgents(request)
      status(result) shouldBe 401
    }

    s"return 403 if user is not logged in via GovernmentGateway" in {
      givenUnauthorisedForUnsupportedAuthProvider
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithOnlyForAgents(request)
      status(result) shouldBe 403
    }
  }

  "OnlyForClients" should {
    s"return 200 if logged in client" in {
      givenClientAll(mtdItId, vrn, nino, utr, cgtRef)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      testClientList.foreach { testClient =>
        val result: Future[Result] = TestController.testWithOnlyForClients(testClient.service, testClient.clientIdType)(request)
        status(result) shouldBe 200
      }
    }

    s"return 403 if not logged in through GG or PA via Client" in {
      isNotGGorPA
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      testClientList.foreach { testClient =>
        val result: Future[Result] = TestController.testWithOnlyForClients(testClient.service, testClient.clientIdType)(request)
        status(result) shouldBe 403
      }
    }

    s"return 403 if not logged in as a Client" in {
      givenUnauthorisedForUnsupportedAffinityGroup
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      testClientList.foreach { testClient =>
        val result: Future[Result] = TestController.testWithOnlyForClients(testClient.service, testClient.clientIdType)(request)
        status(result) shouldBe 403
      }
    }
  }

  "AuthorisedClientOrStrideUser" should {
    testClientList.foreach {
      client =>
        runHappyLoginScenarioForClient(client, true, false)
        runHappyLoginScenarioForClient(client, false, true)
    }
    runUnHappyLoginScenarioForClient(true, false)
    runUnHappyLoginScenarioForClient(false, true)

    s"return 401 for no login" in new LoggedInUser(false, false) {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("UTR", utr.value, strideRoles)(request)

      status(result) shouldBe 401
    }

    s"return 403 if not logged in through GG or PA via Client Or Stride User" in {
      isNotGGorPA
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("UTR", utr.value, strideRoles)(request)

      status(result) shouldBe 403
    }
  }

  def runHappyLoginScenarioForClient(testClient: TestClient[_], forStride: Boolean, forClient: Boolean) = {
    s"return 200 for successful Login if user was ${if(forStride) "stride" else "client"} and requesting ${testClient.service.id}" in new LoggedInUser(forStride, forClient) {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride(testClient.urlIdentifier, testClient.clientId.value, strideRoles)(request)

      status(result) shouldBe 200
    }
  }

  def runUnHappyLoginScenarioForClient(forStride: Boolean, forClient: Boolean) = {
    s"return 400 for unsupported Service if user was ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forClient) {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("SAUTR", utr.value, strideRoles)(request)

      status(result) shouldBe 400
    }
  }

  "onlyStride" should {
    "return 200 for successful stride login" in {
      givenOnlyStrideStub("caat", "123ABC")
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testOnlyForStride(request)
      status(result) shouldBe 200
    }

    "return 403 if non-stride login" in {
      implicit val request = FakeRequest("GET", "/path-of-request").withSession(SessionKeys.authToken -> "Bearer XYZ")

      val result: Future[Result] = TestController.testOnlyForStride(request)
      status(result) shouldBe 403
    }
  }


}
