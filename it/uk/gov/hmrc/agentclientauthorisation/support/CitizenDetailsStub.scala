package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping

trait CitizenDetailsStub {
  me: StartAndStopWireMock =>

  def givenCitizenDetailsAreKnownFor(nino: String, dob: String, sautr: Option[String] = None): StubMapping =
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
                         |      "nino": "$nino" ${sautr.map(utr => s""","sautr": "$utr" """).getOrElse("")}
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

  def givenDesignatoryDetailsAreKNownFor(nino: String, postcode: Option[String]): StubMapping =
    stubFor(
      get(urlEqualTo(s"/citizen-details/$nino/designatory-details"))
        .willReturn(aResponse()
          .withStatus(200)
        .withBody(
          s"""{
             |"person": {
             |  "nino": "$nino"
             |} ${postcode.map(pc => s""", "address": { "postcode": "$pc"} """).getOrElse("")}
             |}""".stripMargin)
    ))
}
