package uk.gov.hmrc.agentclientauthorisation.connectors
import com.kenshoo.play.metrics.Metrics
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.controllers.BaseISpec
import uk.gov.hmrc.agentmtdidentifiers.model.Service.{HMRCCGTPD, HMRCMTDIT, HMRCMTDVAT}
import uk.gov.hmrc.agentmtdidentifiers.model.{ClientIdType, Service}
import uk.gov.hmrc.auth.core.{AuthConnector, EnrolmentIdentifier, Enrolment => AuthEnrolment}
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.Future

class AuthActionsISpec extends BaseISpec {

  val authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
  val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]
  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  object TestController extends AuthActions(metrics, appConfig ,authConnector, cc) {

//    implicit val hc = HeaderCarrier()

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

    def testOnlyForStride(strideRole: String): Action[AnyContent] =
      super.onlyStride(strideRole) { _ =>
          Future.successful(Ok)
      }

    def testMultiEnrolledClient(implicit request: Request[AnyContent]): Future[Result] =
      super.withMultiEnrolledClient{ enrols => Future.successful(Ok(s"$enrols"))}
  }

  class LoggedInUser(forStride: Boolean, forClient: Boolean) {
    if(forStride)
      givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
    else if(forClient)
      givenClientAll(mtdItId, vrn, nino, utr, urn, cgtRef, pptRef, cbcId, plrId)
    else isNotLoggedIn
  }

  val testClientList = List(itsaClient, irvClient, vatClient, trustClient)

  "OnlyForAgents" should {
    s"return 200 if logged in as Agent" in {
      givenAuthorisedAsAgent(arn)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithOnlyForAgents(request)
      status(result) shouldBe 200
    }

    s"return 401 if not logged in as Agent" in {
      givenClientAll(mtdItId, vrn, nino, utr, urn, cgtRef, pptRef, cbcId, plrId)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithOnlyForAgents(request)
      status(result) shouldBe 401
    }

    s"return 403 if user is not logged in via GovernmentGateway" in {
      givenUnauthorisedForUnsupportedAuthProvider
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithOnlyForAgents(request)
      status(result) shouldBe 403
    }
  }

  "OnlyForClients" should {
    s"return 200 if logged in client" in {
      givenClientAll(mtdItId, vrn, nino, utr, urn, cgtRef, pptRef, cbcId, plrId)
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      testClientList.foreach { testClient =>
        val result: Future[Result] = TestController.testWithOnlyForClients(testClient.service, testClient.clientIdType)(request)
        status(result) shouldBe 200
      }
    }

    s"return 403 if not logged in through GG or PA via Client" in {
      isNotGGorPA
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      testClientList.foreach { testClient =>
        val result: Future[Result] = TestController.testWithOnlyForClients(testClient.service, testClient.clientIdType)(request)
        status(result) shouldBe 403
      }
    }

    s"return 403 if not logged in as a Client" in {
      givenUnauthorisedForUnsupportedAffinityGroup
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

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
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("UTR", utr.value, strideRoles)(request)

      status(result) shouldBe 401
    }

    s"return 403 if not logged in through GG or PA via Client Or Stride User" in {
      isNotGGorPA
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("UTR", utr.value, strideRoles)(request)

      status(result) shouldBe 403
    }
  }

  def runHappyLoginScenarioForClient(testClient: TestClient[_], forStride: Boolean, forClient: Boolean) = {
    s"return 200 for successful Login if user was ${if(forStride) "stride" else "client"} and requesting ${testClient.service.id}" in new LoggedInUser(forStride, forClient) {
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride(testClient.urlIdentifier, testClient.clientId.value, strideRoles)(request)

      status(result) shouldBe 200
    }
  }

  def runUnHappyLoginScenarioForClient(forStride: Boolean, forClient: Boolean) = {
    s"return 400 for unsupported Service if user was ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forClient) {
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testWithAuthorisedAsClientOrStride("SAUTR", utr.value, strideRoles)(request)

      status(result) shouldBe 400
    }
  }

  "onlyStride" should {
    "return 200 for successful stride login" in {
      givenOnlyStrideStub("caat", "123ABC")
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testOnlyForStride("caat")(request)
      status(result) shouldBe 200
    }

    "return 401 for incorrect stride login" in {
      givenOnlyStrideStub("maintain-agent-relationships", "123ABC")
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testOnlyForStride("caat")(request)
      status(result) shouldBe 401
    }

    "return 403 if non-stride login" in {
      implicit val request = FakeRequest("GET", "/path-of-request").withHeaders("Authorization" -> "Bearer XYZ")

      val result: Future[Result] = TestController.testOnlyForStride("caat")(request)
      status(result) shouldBe 403
    }
  }

  "withMultiEnrolledClient" should {
    "return 200 for Individual when low confidence level if CGT only" in {
      implicit val fakeRequest = FakeRequest("GET", "/path-of-request")
        .withHeaders("Authorization" -> "Bearer XYZ")
      authenticatedClient(
        request = fakeRequest,
        confidenceLevel = 50,
        enrolments = Set(
          AuthEnrolment(key = HMRCCGTPD,
            identifiers = Seq(EnrolmentIdentifier("CGTPDRef","cgtRef")), state = "Activated")))
      val result = TestController.testMultiEnrolledClient(fakeRequest)
      status(result) shouldBe 200
       bodyOf(result.futureValue).contains("HMRC-CGT-PD") shouldBe true
    }
    "return 200 for Individual with high confidence level" in {
       implicit val fakeRequest = FakeRequest("GET", "/path-of-request")
          .withHeaders("Authorization" -> "Bearer XYZ")
      authenticatedClient(request = fakeRequest,
        enrolments = Set(AuthEnrolment(key = HMRCMTDIT,
          identifiers = Seq(EnrolmentIdentifier("MTDITID", "itsaRef")), state = "Activated")))
      val result = TestController.testMultiEnrolledClient(fakeRequest)
      status(result) shouldBe 200
      bodyOf(result.futureValue).contains("HMRC-MTD-IT") shouldBe true

    }

    "return 403 for Individual with low confidence level"   in {
      implicit val fakeRequest = FakeRequest("GET", "/path-of-request")
        .withHeaders("Authorization" -> "Bearer XYZ")
      authenticatedClient(request = fakeRequest,
        confidenceLevel = 50,
        enrolments = Set(AuthEnrolment(key = HMRCMTDIT,
          identifiers = Seq(EnrolmentIdentifier("MTDITID", "itsaRef")), state = "Activated")))
      val result = TestController.testMultiEnrolledClient(fakeRequest)
      status(result) shouldBe 403
    }
    "return 200 for Organisation" in {
       implicit val fakeRequest = FakeRequest("GET", "/path-of-request")
         .withHeaders("Authorization" -> "Bearer XYZ")
       authenticatedClient(request = fakeRequest,
         affinityGroup = "Organisation",
         confidenceLevel = 50,
         enrolments = Set(AuthEnrolment(key = HMRCMTDVAT,
           identifiers = Seq(EnrolmentIdentifier("VRN", "vatRef")), state = "Activated")))
       val result = TestController.testMultiEnrolledClient(fakeRequest)
       status(result) shouldBe 200
      bodyOf(result.futureValue).contains("HMRC-MTD-VAT") shouldBe true
    }
    "return 200 for Organisation with NINO" in {
         implicit val fakeRequest = FakeRequest("GET", "/path-of-request")
           .withHeaders("Authorization" -> "Bearer XYZ")
         authenticatedClient(request = fakeRequest,
           affinityGroup = "Organisation",                                                       
           confidenceLevel = 50,
           enrolments = Set(AuthEnrolment(key = "HMRC-NI",
             identifiers = Seq(EnrolmentIdentifier("NINO", "ninoRef")), state = "Activated")))
         val result = TestController.testMultiEnrolledClient(fakeRequest)
         status(result) shouldBe 200
         bodyOf(result.futureValue).contains("HMRC-MTD-IT") shouldBe true
    }
  }


}
