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

  def givenCitizenDetailsNoDob(nino: String): Unit =
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
                         |   }
                         |}""".stripMargin)))

  def givenCitizenDetailsReturnsResponseFor(nino: String, response: Int): Unit =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(aResponse()
          .withStatus(response)))
}
