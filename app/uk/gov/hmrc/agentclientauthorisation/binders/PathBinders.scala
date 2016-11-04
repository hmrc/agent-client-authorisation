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

package uk.gov.hmrc.agentclientauthorisation.binders

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, InvitationStatus}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.binders.SimpleObjectBinder

object PathBinders {
  implicit object ArnBinder extends SimpleObjectBinder[Arn](Arn.apply, _.arn)
  implicit object SaUtrBinder extends SimpleObjectBinder[SaUtr](SaUtr.apply, _.value)

  implicit object InvitationStatusBinder extends QueryStringBindable[InvitationStatus] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, InvitationStatus]] = {
      params.get(key) flatMap  {
        case vals if vals.isEmpty => None
        case vals if vals.size > 1 => Some(Left(s"Cannot parse parameter $key as InvitationStatus: multiple values not supported"))
        case vals => InvitationStatus.parse(vals.head) match {
          case Left(error) => Some(Left(s"Cannot parse parameter $key as InvitationStatus: $error"))
          case x => Some(x)
        }
      }
    }

    override def unbind(key: String, value: InvitationStatus): String = s"$key=$value"
  }

}
