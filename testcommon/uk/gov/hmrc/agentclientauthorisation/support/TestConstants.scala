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

import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.domain.{Generator, Nino}

object TestConstants {
  val mtdItId1: MtdItId = MtdItId("mtdItId")

  val nino1: Nino = new Generator().nextNino
  val nino2: Nino = new Generator().nextNino

  val arn = "ABCDEF123456"

  val agentCode = "12345"

  val MtdItService = "HMRC-MTD-IT"
}
