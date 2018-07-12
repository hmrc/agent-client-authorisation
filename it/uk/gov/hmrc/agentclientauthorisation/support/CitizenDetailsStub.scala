package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait CitizenDetailsStub {
  me: StartAndStopWireMock =>

  def givenCitizenDetailsAreKnownFor(nino: String, dob: String): Unit =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |   "name": {
                         |      "current": {
                         |         "firstName": "John",
                         |         "lastName": "Smith"
                         |      },
                         |      "previous": []
                         |   },
                         |   "ids": {
                         |      "nino": "$nino"
                         |   },
                         |   "dateOfBirth": "$dob"
                         |}""".stripMargin)))

  def givenCitizenDetailsReturns404For(nino: String): Unit =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(aResponse()
          .withStatus(404)))

  def givenCitizenDetailsReturns400For(nino: String): Unit =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(aResponse()
          .withStatus(400)))
}
