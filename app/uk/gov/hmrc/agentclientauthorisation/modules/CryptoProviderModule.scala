package uk.gov.hmrc.agentclientauthorisation.modules

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainBytes, PlainContent, PlainText, SymmetricCryptoFactory}

import java.nio.charset.StandardCharsets
import java.util.Base64

class CryptoProviderModule extends Module {

  def aesCryptoInstance(configuration: Configuration): Encrypter with Decrypter = if (
    configuration.underlying.getBoolean("fieldLevelEncryption.enable")
  )
    SymmetricCryptoFactory.aesCryptoFromConfig("fieldLevelEncryption", configuration.underlying)
  else
    NoCrypto

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[Encrypter with Decrypter].qualifiedWith("aes").toInstance(aesCryptoInstance(configuration))
    )
}

/** Encrypter/decrypter that does nothing (i.e. leaves content in plaintext). Only to be used for debugging.
 */
trait NoCrypto extends Encrypter with Decrypter {
  def encrypt(plain: PlainContent): Crypted = plain match {
    case PlainText(text)   => Crypted(text)
    case PlainBytes(bytes) => Crypted(new String(Base64.getEncoder.encode(bytes), StandardCharsets.UTF_8))
  }
  def decrypt(notEncrypted: Crypted): PlainText = PlainText(notEncrypted.value)
  def decryptAsBytes(nullEncrypted: Crypted): PlainBytes = PlainBytes(Base64.getDecoder.decode(nullEncrypted.value))
}

object NoCrypto extends NoCrypto
