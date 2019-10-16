package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait CitizenDetailsStub {
  me: StartAndStopWireMock =>

  def givenCitizenDetailsAreKnownFor(nino: String, dob: String): StubMapping =
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

  def givenCitizenDetailsNoDob(nino: String): StubMapping =
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

  def givenCitizenDetailsReturnsResponseFor(nino: String, response: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(aResponse()
          .withStatus(response)))
}
