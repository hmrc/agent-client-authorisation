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

package uk.gov.hmrc.agentclientauthorisation.model
import play.api.libs.json.Json
import Service._
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.i18n.MessagesApi
import play.api.i18n.I18nSupport

case class EmailInformation(
  to: Seq[String],
  templateId: String,
  parameters: Map[String, String],
  force: Boolean = false,
  eventUrl: Option[String] = None,
  onSendUrl: Option[String] = None)

object EmailInformation {
  implicit val formats = Json.format[EmailInformation]

//  def getEmailTemplateService(service: Service): String =
//    service.id match {
//      case HMRCMTDIT  => Messages(s"service.$HMRCMTDIT")
//      case HMRCPIR    => Messages(s"service.$HMRCPIR")
//      case HMRCMTDVAT => Messages(s"service.$HMRCMTDVAT")
//    }
}
