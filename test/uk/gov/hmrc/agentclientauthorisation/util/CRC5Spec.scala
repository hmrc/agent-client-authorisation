package uk.gov.hmrc.agentclientauthorisation.util

import java.nio.charset.StandardCharsets

import uk.gov.hmrc.play.test.UnitSpec

class CRC5Spec extends UnitSpec {
  "CRC5" should {
    "be deterministic" in {
      CRC5.calculate(Array[Byte](1, 2)) shouldBe CRC5.calculate(Array[Byte](1, 2))
    }

    "not (usually) give same checksum for different input" in {
      CRC5.calculate(Array[Byte](1, 2)) should not be CRC5.calculate(Array[Byte](2, 1))
      CRC5.calculate(Array[Byte](1, 2, 3)) should not be CRC5.calculate(Array[Byte](1, 3, 2))
      CRC5.calculate(Array[Byte](1, 2, 3)) should not be CRC5.calculate(Array[Byte](3, 2, 3))
      CRC5.calculate(Array[Byte](1, 2, 3)) should not be CRC5.calculate(Array[Byte](3, 2, 1))
      CRC5.calculate(Array[Byte](1, 2, 3)) should not be CRC5.calculate(Array[Byte](3, 1, 2))
    }

    "provide checksum for many bytes" in {
      CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)) shouldBe CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8))
    }

    "produce a checksum that captures minor errors in large input" in {
      CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)) should not be CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 7))
      CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)) should not be CRC5.calculate(Array[Byte](0, 2, 3, 4, 5, 6, 7, 8))
      CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)) should not be CRC5.calculate(Array[Byte](1, 2, 3, 3, 5, 6, 7, 8))
      CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)) should not be CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 0, 7, 8))
      CRC5.calculate(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)) should not be CRC5.calculate(Array[Byte](0, 0, 0, 4, 5, 0, 7, 8))
    }

    "produce checksums from either a String's bytes or bytes directly" in {
      CRC5.calculate("ABC") shouldBe CRC5.calculate("ABC".getBytes(StandardCharsets.UTF_8))
    }
  }
}
