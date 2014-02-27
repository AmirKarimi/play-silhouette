---
layout: default
title: Silhouette
---

<a href="http://www.reactivemanifesto.org/"> <img style="border: 0; position: fixed; right: 0; top:0; z-index: 9000" src="http://www.reactivemanifesto.org/images/ribbons/we-are-reactive-black-right.png"> </a>

[![Build Status](https://travis-ci.org/mohiva/play-silhouette.png)](https://travis-ci.org/mohiva/play-silhouette) [![Coverage Status](https://coveralls.io/repos/mohiva/play-silhouette/badge.png)](https://coveralls.io/r/mohiva/play-silhouette)

**Silhouette** is an authentication library for Play Framework applications that supports several authentication methods, including OAuth1, OAuth2, OpenID, Credentials or custom authentication schemes. It is a fork of [SecureSocial](http://securesocial.ws/), the widely known authentication module for Play Framework applications, with the intention to built a more customizable, non-blocking and well tested implementation.

It can be integrated as is, or used as a building block and customized to meet specific application requirements, thanks to its loosely coupled design.

The project is named after the fictional crime fighter character [Silhouette](http://www.comicvine.com/silhouette/4005-35807/), from the Watchmen [graphic novel](http://en.wikipedia.org/wiki/Watchmen) and [movie](http://en.wikipedia.org/wiki/Watchmen_%28film%29).


## Features

* Asynchronous, non-blocking operations
* Customizable: components can be enhanced via inheritance or replaced based on their traits
* Internationalization support
* Persistence agnostic
* Scala API
* Testable


## Installation

Note: The module is currently only available as snapshot.

In your build.sbt:
{% highlight scala %}
libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "1.0"
)
{% endhighlight %}

If you want to use the latest snapshot, add the following instead:
{% highlight scala %}
resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "1.0-SNAPSHOT"
)
{% endhighlight %}


## Documentation

See [the project wiki](https://github.com/mohiva/play-silhouette/wiki) for more information. If you need help with the integration of Silhouette into your project, don't hesitate and ask questions in our [mailing list](https://groups.google.com/forum/#!forum/play-silhouette) or on [Stack Overflow](http://stackoverflow.com/questions/tagged/playframework).


### API Documentation

* [1.0-SNAPSHOT](http://silhouette.mohiva.com/api/1.0-SNAPSHOT/#com.mohiva.play.silhouette.core.package)

## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0) and the documentation under [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/).

Unless otherwise stated, all artifacts are Copyright 2014 Mohiva Organisation (license at mohiva dot com).

This project is derived from [SecureSocial](https://github.com/jaliss/securesocial), Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss. Thanks to [Jorge Aliss](https://github.com/jaliss) for his great work.

