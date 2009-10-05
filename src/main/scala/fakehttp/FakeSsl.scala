package fakehttp

import java.security.KeyStore
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

  private val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
  kmf.init(ks, "password".toCharArray)
  kmf.getKeyManagers.foreach(k => println("keyManager: "+k))

  // Initialize the SSLContext to work with our key managers.
  val serverContext = SSLContext.getInstance("TLS")
  serverContext.init(kmf.getKeyManagers(), null, null)
  
  val clientContext = SSLContext.getInstance("TLS")
  clientContext.init(null, null, null)
  
  def getForHost(hostname: String): SSLContext = {
    val subject = "CN="+hostname+", OU=Test, O=CyberVillainsCA, L=Seattle, S=Washington, C=US"
    val gen = java.security.KeyPairGenerator.getInstance("RSA")
    gen.initialize(1024, new java.security.SecureRandom)
    val keyPair = gen.generateKeyPair()
    null
  }
  
}