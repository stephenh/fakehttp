package fakehttp.ssl

import java.security.KeyStore
import java.security.Security
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactorySpi
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

import org.bouncycastle.asn1.DEREncodableVector
import org.bouncycastle.asn1.DERObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.X509Extensions
import org.bouncycastle.x509.X509V3CertificateGenerator
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure

object CyberVillainsContextFactory {
  private val ks = KeyStore.getInstance("JKS")
  ks.load(classOf[Proxy].getResourceAsStream("/cybervillainsCA.jks"), "password".toCharArray)

  private val signingCert = ks.getCertificate("signingCert").asInstanceOf[X509Certificate]
  private val signingPrivateKey = ks.getKey("signingCertPrivKey", "password".toCharArray).asInstanceOf[PrivateKey]
  private val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
  kmf.init(ks, "password".toCharArray)

  private val serverContexts = new scala.collection.mutable.HashMap[String, SSLContext]()

  val clientContext = SSLContext.getInstance("TLS")
  clientContext.init(null, null, null)

  def createServerContextForHost(hostname: String): SSLContext = synchronized {
    serverContexts.getOrElse(hostname, makeServerContextForHost(hostname))
  }

  private def makeServerContextForHost(hostname: String): SSLContext = synchronized {
    val subject = "CN="+hostname+", OU=Test, O=CyberVillainsCA, L=Seattle, S=Washington, C=US"
    val gen = java.security.KeyPairGenerator.getInstance("RSA")
    gen.initialize(1024, new java.security.SecureRandom)
    val keyPair = gen.generateKeyPair()
    val newCert = CyberVillainsCerts.makeSslCert(keyPair.getPublic, signingCert, signingPrivateKey, subject)

    val tempKeyStore = KeyStore.getInstance("JKS")
    tempKeyStore.load(null, "password".toCharArray)
    tempKeyStore.setCertificateEntry(hostname, newCert)
    tempKeyStore.setKeyEntry(hostname, keyPair.getPrivate, "password".toCharArray, Array(newCert))

    tempKeyStore.load(null, "password".toCharArray)
    val tempKmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    tempKmf.init(tempKeyStore, "password".toCharArray)

    println("Make new key store for "+hostname+": "+tempKeyStore)

    val tempServerContext = SSLContext.getInstance("TLS")
    tempServerContext.init(tempKmf.getKeyManagers(), null, null)
    tempServerContext
  }
}

object CyberVillainsCerts {
  java.security.Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 2)

  def makeSslCert(newPublicKey: PublicKey, caCert: X509Certificate, caPrivateKey: PrivateKey, subject: String): X509Certificate = {
    val gen = new X509V3CertificateGenerator()
    gen.setSubjectDN(new X500Principal(subject))
    gen.setSignatureAlgorithm("SHA1withRSA")
    gen.setPublicKey(newPublicKey)
    gen.setNotAfter(new Date(System.currentTimeMillis() + 30L * 60 * 60 * 24 * 30 * 12))
    gen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30 * 12))
    gen.setIssuerDN(caCert.getSubjectX500Principal())
    gen.setSerialNumber(new java.math.BigInteger(java.lang.Long.toString(System.currentTimeMillis())))
    gen.addExtension(X509Extensions.BasicConstraints, true, new BasicConstraints(false))
    gen.addExtension(X509Extensions.SubjectKeyIdentifier, false, new SubjectKeyIdentifierStructure(newPublicKey))
    gen.addExtension(X509Extensions.AuthorityKeyIdentifier, false, new AuthorityKeyIdentifierStructure(caCert.getPublicKey()))

    val keyUsages = new DEREncodableVector()
    val keyPurposeBase = "1.3.6.1.5.5.7.3"
    val serverAuth = keyPurposeBase + ".1"
    keyUsages.add(new DERObjectIdentifier(serverAuth))
    val clientAuth = keyPurposeBase + ".2"
    keyUsages.add(new DERObjectIdentifier(clientAuth))
    val netscapeServerGatedCrypto = "2.16.840.1.113730.4.1"
    keyUsages.add(new DERObjectIdentifier(netscapeServerGatedCrypto))
    val msServerGatedCrypto = "1.3.6.1.4.1.311.10.3.3"
    keyUsages.add(new DERObjectIdentifier(msServerGatedCrypto))
    gen.addExtension(X509Extensions.ExtendedKeyUsage, false, new DERSequence(keyUsages))

    gen.generate(caPrivateKey, "BC")
  }
}

// Not used right now--I think for accepting bad destination certs.
object FakeTrustManager extends TrustManagerFactorySpi {
  val dummyTrustManager = new X509TrustManager() {
    override def getAcceptedIssuers() = Array[X509Certificate]()
    override def checkClientTrusted(arg0: Array[X509Certificate], arg1: String) = {
      println("checkClientTrusted "+arg0+" ... "+arg1)
    }
    override def checkServerTrusted(arg0: Array[X509Certificate], arg1: String) = {
      println("checkServedTrusted "+arg0+" ... "+arg1)
    }
  }

  override def engineGetTrustManagers(): Array[TrustManager] = Array(dummyTrustManager)
  override def engineInit(keyStore: KeyStore) = {}
  override def engineInit(managerFactoryParameters: ManagerFactoryParameters) = {}
}
