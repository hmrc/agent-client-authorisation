/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{Suite, BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.play.it.Port

trait StartAndStopWireMock extends BeforeAndAfterEach with BeforeAndAfterAll {
  self: Suite =>

  protected val wiremockPort = Port.randomAvailable
  protected val wiremockHost = "localhost"
  protected val wiremockBaseUrl: String = s"http://$wiremockHost:$wiremockPort"
  val wireMockServer = new WireMockServer(wireMockConfig().port(wiremockPort))

  override def beforeAll() = {
    wireMockServer.stop()
    wireMockServer.start()
    WireMock.configureFor(wiremockHost, wiremockPort)
  }

  override def beforeEach() = {
    WireMock.reset()
  }

  override protected def afterAll(): Unit = {
    wireMockServer.stop()
  }
}
