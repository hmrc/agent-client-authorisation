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

package uk.gov.hmrc.agentclientauthorisation.model

sealed trait Pillar2KnownFactCheckResult

object Pillar2KnownFactCheckResult {

  case object Pillar2KnownFactCheckOk extends Pillar2KnownFactCheckResult

  case object Pillar2KnownFactNotMatched extends Pillar2KnownFactCheckResult

  case object Pillar2RecordClientInactive extends Pillar2KnownFactCheckResult

  case object Pillar2DetailsNotFound extends Pillar2KnownFactCheckResult
}
