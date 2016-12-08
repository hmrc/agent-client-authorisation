/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import javax.inject._

import play.api.hal.{Hal, HalLink, HalResource}
import play.api.libs.json.Json
import play.api.mvc.Action
import uk.gov.hmrc.agentclientauthorisation.controllers.{HalWriter, routes => prodroutes}
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class SandboxRootController
    extends BaseController with HalWriter {
  private val selfLink = Vector(HalLink("self", prodroutes.RootController.getRootResource().url));

  def getRootResource() = Action { implicit request =>
    val invitationsSentLink = HalLink("sent", prodroutes.AgencyInvitationsController.getSentInvitations(HardCodedSandboxIds.arn, None, None, None).url)
    val invitationsReceivedLink = HalLink("received", prodroutes.ClientInvitationsController.getInvitations(HardCodedSandboxIds.clientId.value, None).url)
    val halResource: HalResource = Hal.hal(Json.obj(), selfLink ++ Vector(invitationsSentLink, invitationsReceivedLink), Vector())
    Ok(halResource)
  }
}
