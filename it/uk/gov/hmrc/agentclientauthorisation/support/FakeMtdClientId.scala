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

import org.apache.commons.lang3.RandomStringUtils
import uk.gov.hmrc.agentclientauthorisation.model.MtdClientId
import uk.gov.hmrc.domain.SaUtr

/**
  * Transformations between fake MTD client IDs and SA UTRs
  *
  * Being able to do these transformations is completely bogus and
  * unrealistic, we are just doing it to make it easier for us to test things
  * whilst we don't know how MTD enrolments will really work.
  *
  * The transformations in here need to match the transformation inside
  * agencies-fake (in ClientRecordController at the time of writing).
  *
  * N.B. this is deliberately test-only code, non-test code that needs to
  * perform these transformations should use the agencies-fake microservice.
  */
object FakeMtdClientId {
  private val fakeMtdPrefix = "MTD-REG-"

  def toSaUtr(mtdClientId: MtdClientId): SaUtr = {
    if (!mtdClientId.value.startsWith(fakeMtdPrefix))
      throw new IllegalArgumentException(s"$mtdClientId is not a valid fake MTD client ID because it does not start with $fakeMtdPrefix")
    else
      SaUtr(mtdClientId.value.substring(fakeMtdPrefix.length))
  }

  def apply(saUtr: SaUtr): MtdClientId =
    MtdClientId(s"$fakeMtdPrefix${saUtr.value}")

  def random(): MtdClientId =
    apply(SaUtr(RandomStringUtils.randomNumeric(10)))
}
