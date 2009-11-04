
Intro
=====

fakehttp is an HTTP proxy written in [Scala](http://www.scala-lang.org) on top of [Netty](http://www.jboss.org/netty).

fakehttp's original use case was intercepting automated browser testing traffic, however it can also function as a general purpose HTTP proxy.

Intercepting Browser Traffic
============================

While shuffling bytes between the incoming browser and the outgoing server, fakehttp provides an [Interceptor][1] hook to inspect requests and either modify the destination or respond directly.

E.g. for automated testing, the [LocalhostInterceptor][2] can route browser traffic that would otherwise go to `http://www.somesite.com` to `http://localhost`. This is useful for containing (and asserting against) URLs within your application you either cannot easily change or are using to test cross-domain functionality.

SSL Modes
=========

fakehttp has two SSL modes:

* [OpaqueSslMode][3] leaves SSL traffic as encrypted and means you can no longer inspect the HTTP contents going back and forth
* [ClearSslMode][4] sets fakehttp up as a man-in-the-middle to decrypt and re-crypt the SSL traffic you can continue inspecting it

License
=======

fakehttp is Apache licensed, except for the CyberVillain stuff that drives [ClearSslMode][4], which is [ASL licensed to Selenium][5] and I include here mostly as a proof of concept/cool hack.

[1]: master/src/main/scala/fakehttp/interceptor/Interceptor.scala
[2]: master/src/main/scala/fakehttp/interceptor/LocalhostInterceptor.scala
[3]: master/src/main/scala/fakehttp/ssl/OpaqueSslMode.scala
[4]: master/src/main/scala/fakehttp/ssl/ClearSslMode.scala
[5]: http://code.google.com/p/selenium/source/browse/selenium-rc/trunk/server-coreless/src/main/java/cybervillains/ca/CertificateCreator.java
