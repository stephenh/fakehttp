package fakehttp.ssl

import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactorySpi
import javax.net.ssl.X509TrustManager

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

object FakeSsl {
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