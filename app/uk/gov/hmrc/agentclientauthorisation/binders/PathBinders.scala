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

package uk.gov.hmrc.agentclientauthorisation.binders

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatus
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.binders.SimpleObjectBinder

object PathBinders {

  implicit object ArnBinder extends SimpleObjectBinder[Arn](Arn.apply, _.value)

  private def toError(err:String) = s"Cannot parse parameter status as InvitationStatus: status of [$err] is not a valid InvitationStatus"

  implicit object InvitationStatusBinder extends QueryStringBindable[InvitationStatus] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, InvitationStatus]] =
      params.get(key) flatMap {
        _.headOption map (status => InvitationStatus(status) leftMap toError)
      }

    override def unbind(key: String, value: InvitationStatus): String = s"$key=$value"
  }
}
