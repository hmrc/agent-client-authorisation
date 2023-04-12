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

package uk.gov.hmrc.agentclientauthorisation.support

import org.bson.types.ObjectId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{urn, _}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, InvitationId, Service}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~

import java.time.{Instant, LocalDate, ZoneOffset}
import scala.concurrent.Future

trait TestData {

  val arn = Arn("arn1")

  val mtdSaPendingInvitationDbId: ObjectId = ObjectId.get()
  val mtdSaAcceptedInvitationDbId: ObjectId = ObjectId.get()

  val trustPendingInvitationDbId: ObjectId = ObjectId.get()
  val trustAcceptedInvitationDbId: ObjectId = ObjectId.get()

  val trustNTPendingInvitationDbId: ObjectId = ObjectId.get()
  val trustNTAcceptedInvitationDbId: ObjectId = ObjectId.get()

  val otherRegimePendingInvitationDbId: ObjectId = ObjectId.get()

  val businessAddress = BusinessAddress("25 Any Street", None, None, None, Some("AA1 7YY"), "GB")

  val mtdSaPendingInvitationId: InvitationId =
    InvitationId.create(arn.value, mtdItId1.value, "HMRC-MTD-IT", LocalDate.parse("2001-01-01").atStartOfDay())('A')
  val mtdSaAcceptedInvitationId: InvitationId =
    InvitationId.create(arn.value, mtdItId1.value, "HMRC-MTD-IT", LocalDate.parse("2001-01-02").atStartOfDay())('A')

  val trustPendingInvitationId: InvitationId =
    InvitationId.create(arn.value, utr.value, "HMRC-TERS-ORG", LocalDate.parse("2001-01-01").atStartOfDay())('D')

  val trustAcceptedInvitationId: InvitationId =
    InvitationId.create(arn.value, utr.value, "HMRC-TERS-ORG", LocalDate.parse("2001-01-02").atStartOfDay())('D')

  val trustNTPendingInvitationId: InvitationId =
    InvitationId.create(arn.value, urn.value, "HMRC-TERSNT-ORG", LocalDate.parse("2001-01-01").atStartOfDay())('F')
  val trustNTAcceptedInvitationId: InvitationId =
    InvitationId.create(arn.value, urn.value, "HMRC-TERSNT-ORG", LocalDate.parse("2001-01-02").atStartOfDay())('F')

  val otherRegimePendingInvitationId: InvitationId =
    InvitationId.create(arn.value, mtdItId1.value, "mtd-other", LocalDate.parse("2001-01-03").atStartOfDay())('A')

  def now() = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime

  val allInvitations = List(
    Invitation(
      mtdSaPendingInvitationDbId,
      mtdSaPendingInvitationId,
      arn,
      Some("personal"),
      Service.MtdIt,
      mtdItId1,
      ClientIdentifier(nino1.value, "ni"),
      now().toLocalDate.plusDays(100),
      None,
      false,
      None,
      None,
      events = List(StatusChangeEvent(now(), Pending))
    ),
    Invitation(
      mtdSaAcceptedInvitationDbId,
      mtdSaAcceptedInvitationId,
      arn,
      Some("personal"),
      Service.MtdIt,
      mtdItId1,
      ClientIdentifier(nino1.value, "ni"),
      now().toLocalDate.plusDays(100),
      None,
      false,
      None,
      None,
      events = List(StatusChangeEvent(now(), Accepted))
    ),
    Invitation(
      otherRegimePendingInvitationDbId,
      otherRegimePendingInvitationId,
      arn,
      Some("personal"),
      Service.PersonalIncomeRecord,
      mtdItId1,
      ClientIdentifier(nino1.value, "ni"),
      now().toLocalDate.plusDays(100),
      None,
      false,
      None,
      None,
      events = List(StatusChangeEvent(now(), Pending))
    )
  )

  val invitationExpired = Invitation(
    otherRegimePendingInvitationDbId,
    otherRegimePendingInvitationId,
    arn,
    Some("personal"),
    Service.PersonalIncomeRecord,
    mtdItId1,
    ClientIdentifier(urn.value, "urn"),
    now().toLocalDate.plusDays(100),
    None,
    false,
    None,
    None,
    events = List(StatusChangeEvent(now(), Expired))
  )

  val invitationActive = Invitation(
    otherRegimePendingInvitationDbId,
    otherRegimePendingInvitationId,
    arn,
    Some("personal"),
    Service.TrustNT,
    mtdItId1,
    ClientIdentifier(urn.value, "urn"),
    now().toLocalDate.plusDays(100),
    None,
    false,
    None,
    None,
    events = List(StatusChangeEvent(now(), Accepted))
  )

  val invitationPending = Invitation(
    otherRegimePendingInvitationDbId,
    otherRegimePendingInvitationId,
    arn,
    Some("personal"),
    Service.TrustNT,
    mtdItId1,
    ClientIdentifier(urn.value, "urn"),
    now().toLocalDate.plusDays(100),
    None,
    false,
    None,
    None,
    events = List(StatusChangeEvent(now(), Pending))
  )

  val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", arn.value)), state = "", delegatedAuthRule = None))

  //Singular Client
  val clientMtdItEnrolment = Set(Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", mtdItId1.value)), state = "", delegatedAuthRule = None))

  val clientNiEnrolment = Set(Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", "AA000003D")), state = "", delegatedAuthRule = None))

  val clientVrnEnrolment = Set(Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VRN", vrn.value)), state = "", delegatedAuthRule = None))

  val clientTrustEnrolment = Set(
    Enrolment("HMRC-TERS-ORG", Seq(EnrolmentIdentifier("SAUTR", utr.value)), state = "", delegatedAuthRule = None)
  )

  val clientMtdItEnrolments: Future[Enrolments] = Future successful Enrolments(clientMtdItEnrolment)

  val clientNiEnrolments: Future[Enrolments] = Future successful Enrolments(clientNiEnrolment)

  val clientVrnEnrolments: Future[Enrolments] = Future successful Enrolments(clientVrnEnrolment)

  val clientNoEnrolments: Future[Enrolments] = Future successful Enrolments(Set.empty[Enrolment])

  val clientMtdItIrvVat = Set(
    Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", mtdItId1.value)), state = "", delegatedAuthRule = None),
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", "AA000003D")), state = "", delegatedAuthRule = None),
    Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VRN", vrn.value)), state = "", delegatedAuthRule = None)
  )

  val clientMtdItIrv = Set(
    Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", mtdItId1.value)), state = "", delegatedAuthRule = None),
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", "AA000003D")), state = "", delegatedAuthRule = None)
  )

  val clientMtdVat = Set(Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VRN", vrn.value)), state = "", delegatedAuthRule = None))

  val clientMtdIrvVatEnrolmentsIndividual: Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments] =
    client(AffinityGroup.Individual, ConfidenceLevel.L200, clientMtdItIrvVat)

  val clientMtdIrvEnrolmentsIndividual: Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments] =
    client(AffinityGroup.Individual, ConfidenceLevel.L200, clientMtdItIrv)

  val clientVatEnrolmentsOrganisation: Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments] =
    client(AffinityGroup.Organisation, ConfidenceLevel.L50, clientMtdVat)

  def client(
    affinityGroup: AffinityGroup,
    confidenceLevel: ConfidenceLevel,
    enrolments: Set[Enrolment]): Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments] =
    Future.successful(
      new ~(new ~(Some(affinityGroup), confidenceLevel), Enrolments(enrolments))
    )

  val agentAffinityConfidenceAndEnrolment: Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments] =
    Future.successful(new ~(new ~(Some(AffinityGroup.Agent), ConfidenceLevel.L50), Enrolments(agentEnrolment)))

  //Agent
  val agentAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(agentEnrolment))

  val neitherHaveAffinityOrEnrolment: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](None, Enrolments(Set.empty[Enrolment]))

  val agentNoEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(Set.empty[Enrolment]))

  val agentIncorrectAffinity: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(agentEnrolment))

  val failedStubForClient: Future[Enrolments] = Future failed MissingBearerToken()

}
