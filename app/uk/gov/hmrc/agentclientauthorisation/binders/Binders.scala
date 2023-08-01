/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatus
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino

import java.time.LocalDate
import scala.util.control.NonFatal

object Binders {

  implicit object ArnBinder extends SimpleObjectBinder[Arn](Arn.apply, _.value)
  implicit object NinoBinder extends SimpleObjectBinder[Nino](Nino.apply, _.value)
  implicit object MtdItIdBinder extends SimpleObjectBinder[MtdItId](MtdItId.apply, _.value)
  implicit object VrnBinder extends SimpleObjectBinder[Vrn](Vrn.apply, _.value)
  implicit object UtrBinder extends SimpleObjectBinder[Utr](Utr.apply, _.value)
  implicit object UrnBinder extends SimpleObjectBinder[Urn](Urn.apply, _.value)
  implicit object CgtRefBinder extends SimpleObjectBinder[CgtRef](CgtRef.apply, _.value)
  implicit object InvitationIdBinder extends SimpleObjectBinder[InvitationId](InvitationId.apply, _.value)
  implicit object LocalDateBinder extends SimpleObjectBinder[LocalDate](s => { assert(s.length == 10); LocalDate.parse(s) }, _.toString)
  implicit object PptRefBinder extends SimpleObjectBinder[PptRef](PptRef.apply, _.value)
  implicit object CbcIdBinder extends SimpleObjectBinder[CbcId](CbcId.apply, _.value)

  /**
    * Binds either of two possible binders. The 'right' binder if preferred if both are valid.
    */
  def eitherBinder[L, R](isValidL: L => Boolean, isValidR: R => Boolean)(
    implicit bindL: PathBindable[L],
    bindR: PathBindable[R]): PathBindable[Either[L, R]] =
    new PathBindable[Either[L, R]] {
      def bind(key: String, value: String): Either[String, Either[L, R]] = {
        val resR = bindR.bind(key, value)
        lazy val resL = bindL.bind(key, value)
        lazy val err = (resR.right.toSeq ++ resL.left.toSeq).mkString(", ")
        if (resR.exists(isValidR)) resR.map(Right(_))
        else if (resL.exists(isValidL)) resL.map(Left(_))
        else Left(err)
      }

      override def unbind(key: String, value: Either[L, R]): String = value match {
        case Left(l)  => bindL.unbind(key, l)
        case Right(r) => bindR.unbind(key, r)
      }
    }
  implicit val agentIdBinder = eitherBinder[Utr, Arn](utr => Utr.isValid(utr.value), arn => Arn.isValid(arn.value))

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
          Right(LocalDate.parse(param))
        } catch {
          case NonFatal(e) => Left(e.getMessage)
        }
      }

    override def unbind(key: String, value: LocalDate): String = value.toString
  }

}
