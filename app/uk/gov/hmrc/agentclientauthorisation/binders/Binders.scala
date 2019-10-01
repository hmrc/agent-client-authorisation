/*
 * Copyright 2019 HM Revenue & Customs
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

import org.joda.time.LocalDate
import org.joda.time.format.ISODateTimeFormat
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatus
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino

import scala.util.control.NonFatal

object Binders {

  implicit object ArnBinder extends SimpleObjectBinder[Arn](Arn.apply, _.value)
  implicit object NinoBinder extends SimpleObjectBinder[Nino](Nino.apply, _.value)
  implicit object MtdItIdBinder extends SimpleObjectBinder[MtdItId](MtdItId.apply, _.value)
  implicit object VrnBinder extends SimpleObjectBinder[Vrn](Vrn.apply, _.value)
  implicit object UtrBinder extends SimpleObjectBinder[Utr](Utr.apply, _.value)
  implicit object CgtRefBinder extends SimpleObjectBinder[CgtRef](CgtRef.apply, _.value)
  implicit object InvitationIdBinder extends SimpleObjectBinder[InvitationId](InvitationId.apply, _.value)
  implicit object LocalDateBinder
      extends SimpleObjectBinder[LocalDate](s => { assert(s.length == 10); LocalDate.parse(s) }, _.toString)

  private def toError(err: String) =
    s"Cannot parse parameter status as InvitationStatus: status of [$err] is not a valid InvitationStatus"

  implicit object InvitationStatusBinder extends QueryStringBindable[InvitationStatus] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, InvitationStatus]] =
      params.get(key) flatMap {
        _.headOption map (status => InvitationStatus(status) leftMap toError)
      }

    override def unbind(key: String, value: InvitationStatus): String = s"$key=$value"
  }

  implicit object LocalDateQueryStringBinder extends QueryStringBindable[LocalDate] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDate]] =
      params.get(key).flatMap(_.headOption).map { param =>
        try {
          assert(param.length == 10)
          Right(LocalDate.parse(param, ISODateTimeFormat.date()))
        } catch {
          case NonFatal(e) => Left(e.getMessage)
        }
      }

    override def unbind(key: String, value: LocalDate): String = value.toString
  }

}
