package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._

trait NiExemptionRegistrationStub {
  me: StartAndStopWireMock =>

  def givenNiBusinessExists(utr: String, name: String, eori: Option[String]): Unit =
    stubFor(
      post(urlEqualTo(s"/ni-exemption-registration/ni-businesses/$utr"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |   "niBusiness": {
                         |       "name": "$name",
                         |       "subscription": {
                         |           "status": "${if (eori.isDefined) "NI_SUBSCRIBED" else "NI_NOT_SUBSCRIBED"}"
                         |           ${eori.map(v => s""", "eori": "$v" """).getOrElse("")}
                         |       }
                         |   }
                         |}
               """.stripMargin)))

  def givenNiBusinessNotExists(utr: String): Unit =
    stubFor(
      post(urlEqualTo(s"/ni-exemption-registration/ni-businesses/$utr"))
        .willReturn(aResponse()
          .withStatus(409)))

}
