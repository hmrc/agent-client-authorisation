/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.connectors

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import org.joda.time.format._
import play.api.Logging
import play.api.http.Status
import play.api.libs.json.{JsPath, Reads}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class CitizenDateOfBirth(dateOfBirth: Option[LocalDate])

object CitizenDateOfBirth {
  val format = DateTimeFormat.forPattern("ddMMyyyy")
  implicit val reads: Reads[CitizenDateOfBirth] =
    (JsPath \ "dateOfBirth")
      .readNullable[String]
      .map {
        case Some(dob) => CitizenDateOfBirth(Some(LocalDate.parse(dob, format)))
        case None      => CitizenDateOfBirth(None)
      }
}
case class Citizen(firstName: Option[String], lastName: Option[String], nino: Option[String] = None) {
  lazy val name: Option[String] = {
    val n = Seq(firstName, lastName).collect({ case Some(x) => x }).mkString(" ")
    if (n.isEmpty) None else Some(n)
  }
}

object Citizen {
  implicit val reads: Reads[Citizen] = {
    val current = JsPath \ "name" \ "current"
    for {
      fn <- (current \ "firstName").readNullable[String]
      ln <- (current \ "lastName").readNullable[String]
      n  <- (JsPath \ "ids" \ "nino").readNullable[String]
    } yield Citizen(fn, ln, n)
  }
}

case class DesignatoryDetails(nino: Option[String], postCode: Option[String])

object DesignatoryDetails {
  implicit val reads: Reads[DesignatoryDetails] = for {
    nino     <- (JsPath \ "person" \ "nino").readNullable[String]
    postCode <- (JsPath \ "address" \ "postcode").readNullable[String]
  } yield DesignatoryDetails(nino, postCode)
}

@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {
  def getCitizenDateOfBirth(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[CitizenDateOfBirth]]

  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]]

  def getDesignatoryDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[DesignatoryDetails]]
}

@Singleton
class CitizenDetailsConnectorImpl @Inject()(appConfig: AppConfig, http: HttpClient, metrics: Metrics)
    extends HttpAPIMonitor with CitizenDetailsConnector with Logging {

  private val baseUrl = appConfig.citizenDetailsBaseUrl

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getCitizenDateOfBirth(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[CitizenDateOfBirth]] =
    monitor(s"ConsumedAPI-CitizenDetails-GET") {
      val url = s"$baseUrl/citizen-details/nino/${nino.value}"
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case Status.OK => Try(response.json.asOpt[CitizenDateOfBirth]).getOrElse(None)
          case _         => None
        }
      }
    }

  def getCitizenDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[Citizen]] =
    monitor(s"ConsumedAPI-CitizenDetails-GET") {
      val url = s"$baseUrl/citizen-details/nino/${nino.value}"
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case Status.OK        => Try(response.json.asOpt[Citizen]).getOrElse(None)
          case Status.NOT_FOUND => None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getCitizenDetails', statusCode=$other", other)
        }
      }
    }

  override def getDesignatoryDetails(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[DesignatoryDetails]] =
    monitor(s"ConsumedAPI-CitizenDetailsDesignatorDetails-GET") {
      val url = s"$baseUrl/citizen-details/nino/${nino.value}/designatory-details"
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case Status.OK        => Try(response.json.asOpt[DesignatoryDetails]).getOrElse(None)
          case Status.NOT_FOUND => None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getCitizenDetailsDesignatoryDetails', statusCode=$other", other)
        }
      }
    }

}
