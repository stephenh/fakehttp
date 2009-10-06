package fakehttp.ssl

import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.DEREncodableVector
import org.bouncycastle.asn1.DERObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.X509Extensions
import org.bouncycastle.x509.X509V3CertificateGenerator
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure

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
    gen.setSerialNumber(new java.math.BigInteger(java.lang.Long.toString(System .currentTimeMillis())))
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
