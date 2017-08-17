package uk.gov.hmrc.agentclientauthorisation.repository

import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentclientauthorisation.support.ResetMongoBeforeTest
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global


class ClientIdMappingRepositoryISpec extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside {

  private val now = DateTime.now()

  private def repository = new ClientIdMappingRepository(mongo()) {
    override def withCurrentTime[A](f: (DateTime) => A): A = f(now)
  }

  private val canonicalClientId = "id1"
  private val canonicalClientIdType = "type1"
  private val suppliedClientId = "supplied id1"
  private val suppliedClientIdType = "supplied type1"


  "create" should {
    "create a new StatusChangedEvent of Pending" in {
      val clientIdMapping = await(repository.create(canonicalClientId, canonicalClientIdType, suppliedClientId, suppliedClientIdType))

      await(repository.findById(clientIdMapping.id)).head shouldBe clientIdMapping
    }

    "create should support duplicate invitations" in {
      await(repository.create(canonicalClientId, canonicalClientIdType, suppliedClientId, suppliedClientIdType))

      val e = intercept[DatabaseException](
        await(repository.create(canonicalClientId, canonicalClientIdType, suppliedClientId, suppliedClientIdType))
      )

      e.getMessage() should include("E11000")
    }
  }

  "find by supplied clientId  supplied clientIdType" should {

    "return a matching invitation" in {
      val createdInvitation = await(repository.create(canonicalClientId, canonicalClientIdType, suppliedClientId, suppliedClientIdType))
      val optInvitation = await(repository.find(suppliedClientId, suppliedClientIdType))

      optInvitation.head shouldBe createdInvitation
    }

    "return None when there is no matching invitation" in {
      val suppliedClientId = "nino1"
      val suppliedClientIdType = "ni"

      await(repository.find(suppliedClientId, suppliedClientIdType)) shouldBe Nil
    }
  }
}
