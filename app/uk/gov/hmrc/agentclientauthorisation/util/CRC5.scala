package uk.gov.hmrc.agentclientauthorisation.util

import java.nio.charset.Charset

object CRC5 {

  /* Params for CRC-5/EPC */
  val bitWidth = 5
  val poly = 0x09
  val initial = 0x09
  val xorOut = 0

  val table: Seq[Int] = {
    val widthMask = (1 << bitWidth) - 1
    val shpoly = poly << (8 - bitWidth)
    for (i <- 0 until 256) yield {
      var crc = i
      for (_ <- 0 until 8) {
        crc = if ((crc & 0x80) != 0) (crc << 1) ^ shpoly else crc << 1
      }
      (crc >> (8 - bitWidth)) & widthMask
    }
  }

  def calculate(string: String): Int = calculate(string.getBytes())

  def calculate(input: Array[Byte]): Int = {
    val start = 0
    val length = input.length
    var crc = initial ^ xorOut
    for (i <- 0 until length) {
      crc = table((crc << (8 - bitWidth)) ^ (input(start + i) & 0xff)) & 0xff
    }
    crc ^ xorOut
  }
}
