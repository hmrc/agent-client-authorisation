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

import org.joda.time.DateTime._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, Invitation, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._

import scala.concurrent.Future

trait TestData {

  val arn = Arn("arn1")

  val mtdSaPendingInvitationId: BSONObjectID = BSONObjectID.generate
  val mtdSaAcceptedInvitationId: BSONObjectID = BSONObjectID.generate
  val otherRegimePendingInvitationId: BSONObjectID = BSONObjectID.generate

  val allInvitations = List(
    Invitation(mtdSaPendingInvitationId, arn, "HMRC-MTD-IT", mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Pending))),
    Invitation(mtdSaAcceptedInvitationId, arn, "HMRC-MTD-IT", mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Accepted))),
    Invitation(otherRegimePendingInvitationId, arn, "mtd-other", mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Pending)))
  )

  val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", arn.value)), confidenceLevel = L200,
      state = "", delegatedAuthRule = None)
  )

  val clientEnrolment = Set(
    Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", mtdItId1.value)), confidenceLevel = L200,
      state = "", delegatedAuthRule = None)
  )

  val clientAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(clientEnrolment)))

  val clientNoEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(Set.empty[Enrolment])))

  val clientNoAffinityGroup: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](None, Enrolments(clientEnrolment)))

  val agentAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(agentEnrolment)))

  val neitherHaveAffinityOrEnrolment: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](None, Enrolments(Set.empty[Enrolment])))

  val agentNoEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(Set.empty[Enrolment])))

  val agentIncorrectAffinity: Future[~[Option[AffinityGroup], Enrolments]] =
    Future.successful(new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(agentEnrolment)))

  val failedStub: Future[~[Option[AffinityGroup], Enrolments]] =
    Future failed new NullPointerException

}
