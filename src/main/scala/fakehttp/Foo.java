package fakehttp;

public class Foo {

    public static X509Certificate generateStdSSLServerCertificate(
                        final PublicKey newPubKey,
                        final X509Certificate caCert,
                        final PrivateKey caPrivateKey,
                        final String subject)
        throws  CertificateParsingException,
                        SignatureException,
                        InvalidKeyException,
                        CertificateExpiredException,
                        CertificateNotYetValidException,
                        CertificateException,
                        NoSuchAlgorithmException,
                        NoSuchProviderException
        {
                X509V3CertificateGenerator  v3CertGen = new X509V3CertificateGenerator();

                v3CertGen.setSubjectDN(new X500Principal(subject));
                v3CertGen.setSignatureAlgorithm(CertificateCreator.SIGN_ALGO);
                v3CertGen.setPublicKey(newPubKey);
                v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + 30L * 60 * 60 * 24 * 30 * 12));
                v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30 *12));
                v3CertGen.setIssuerDN(caCert.getSubjectX500Principal());

                // Firefox actually tracks serial numbers within a CA and refuses to validate if it sees duplicates
                // This is not a secure serial number generator, (duh!) but it's good enough for our purposes.
                v3CertGen.setSerialNumber(new BigInteger(Long.toString(System.currentTimeMillis())));

                v3CertGen.addExtension(
                                X509Extensions.BasicConstraints,
                                true,
                                new BasicConstraints(false) );

                v3CertGen.addExtension(
                                X509Extensions.SubjectKeyIdentifier,
                                false,
                                new SubjectKeyIdentifierStructure(newPubKey));


                v3CertGen.addExtension(
                                X509Extensions.AuthorityKeyIdentifier,
                                false,
                                new AuthorityKeyIdentifierStructure(caCert.getPublicKey()));

//              Firefox 2 disallows these extensions in an SSL server cert.  IE7 doesn't care.
//              v3CertGen.addExtension(
//                              X509Extensions.KeyUsage,
//                              false,
//                              new KeyUsage(KeyUsage.dataEncipherment | KeyUsage.digitalSignature ) );


                DEREncodableVector typicalSSLServerExtendedKeyUsages = new DEREncodableVector();

                typicalSSLServerExtendedKeyUsages.add(new DERObjectIdentifier(ExtendedKeyUsageConstants.serverAuth));
                typicalSSLServerExtendedKeyUsages.add(new DERObjectIdentifier(ExtendedKeyUsageConstants.clientAuth));
                typicalSSLServerExtendedKeyUsages.add(new DERObjectIdentifier(ExtendedKeyUsageConstants.netscapeServerGatedCrypto));
                typicalSSLServerExtendedKeyUsages.add(new DERObjectIdentifier(ExtendedKeyUsageConstants.msServerGatedCrypto));

                v3CertGen.addExtension(
                                X509Extensions.ExtendedKeyUsage,
                                false,
                                new DERSequence(typicalSSLServerExtendedKeyUsages));

//  Disabled by default.  Left in comments in case this is desired.
//
//              v3CertGen.addExtension(
//                              X509Extensions.AuthorityInfoAccess,
//                              false,
//                              new AuthorityInformationAccess(new DERObjectIdentifier(OID_ID_AD_CAISSUERS),
//                                              new GeneralName(GeneralName.uniformResourceIdentifier, "http://" + subject + "/aia")));

//              v3CertGen.addExtension(
//                              X509Extensions.CRLDistributionPoints,
//                              false,
//                              new CRLDistPoint(new DistributionPoint[] {}));



                X509Certificate cert = v3CertGen.generate(caPrivateKey, "BC");

                return cert;
        }
}
