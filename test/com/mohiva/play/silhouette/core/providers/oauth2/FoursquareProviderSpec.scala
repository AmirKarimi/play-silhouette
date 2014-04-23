/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.core.providers.oauth2

import test.Helper
import java.util.UUID
import scala.concurrent.Future
import play.api.libs.json.Json
import play.api.libs.ws.{ Response, WS }
import play.api.test.{ FakeRequest, WithApplication }
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import FoursquareProvider._
import OAuth2Provider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth2.FoursquareProvider]] class.
 */
class FoursquareProviderSpec extends OAuth2ProviderSpec {

  "The authenticate method" should {
    "fail with AuthenticationException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns Json.obj()
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must startWith(InvalidResponseFormat.format(provider.id, ""))
      }
    }

    "fail with AuthenticationException if API returns error" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/foursquare.error.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token", DefaultAPIVersion)) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          400,
          Some("param_error"),
          Some("Must provide a valid user ID or 'self.'")))
      }
    }

    "fail with AuthenticationException if an unexpected error occurred" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenThrows new RuntimeException("")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token", DefaultAPIVersion)) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "return the social profile" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/foursquare.success.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token", DefaultAPIVersion)) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new SocialProfile(
            loginInfo = LoginInfo(provider.id, "13221052"),
            authInfo = oAuthInfo.as[OAuth2Info],
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://irs0.4sqi.net/img/user/100x100/blank_girl.png")
          )
      }
    }

    "return the social profile if API is deprecated" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/foursquare.deprecated.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token", DefaultAPIVersion)) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new SocialProfile(
            loginInfo = LoginInfo(provider.id, "13221052"),
            authInfo = oAuthInfo.as[OAuth2Info],
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://irs0.4sqi.net/img/user/100x100/blank_girl.png")
          )
      }
    }

    "handle the custom API version property" in new WithApplication with Context {
      val customProperties = Map(APIVersion -> "20120101")
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      override lazy val provider = new FoursquareProvider(
        cacheLayer,
        httpLayer,
        oAuthSettings.copy(customProperties = customProperties)
      )

      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/foursquare.success.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token", "20120101")) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new SocialProfile(
            loginInfo = LoginInfo(provider.id, "13221052"),
            authInfo = oAuthInfo.as[OAuth2Info],
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://irs0.4sqi.net/img/user/100x100/blank_girl.png")
          )
      }
    }

    "handle the custom avatar resolution property" in new WithApplication with Context {
      val customProperties = Map(AvatarResolution -> "150x150")
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      override lazy val provider = new FoursquareProvider(
        cacheLayer,
        httpLayer,
        oAuthSettings.copy(customProperties = customProperties)
      )

      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/foursquare.success.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token", DefaultAPIVersion)) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new SocialProfile(
            loginInfo = LoginInfo(provider.id, "13221052"),
            authInfo = oAuthInfo.as[OAuth2Info],
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://irs0.4sqi.net/img/user/150x150/blank_girl.png")
          )
      }
    }
  }

  /**
   * Defines the context for the abstract OAuth2 provider spec.
   *
   * @return The Context to use for the abstract OAuth2 provider spec.
   */
  override protected def context: OAuth2ProviderSpecContext = new Context {}

  /**
   * The context.
   */
  trait Context extends OAuth2ProviderSpecContext {

    /**
     * The OAuth2 settings.
     */
    lazy val oAuthSettings = OAuth2Settings(
      authorizationURL = "https://foursquare.com/oauth2/authenticate",
      accessTokenURL = "https://foursquare.com/oauth2/access_token",
      redirectURL = "https://www.mohiva.com",
      clientID = "my.client.id",
      clientSecret = "my.client.secret")

    /**
     * The OAuth2 info returned by Foursquare.
     *
     * @see https://developer.foursquare.com/overview/auth
     */
    override lazy val oAuthInfo = Helper.loadJson("providers/oauth2/foursquare.access.token.json")

    /**
     * The provider to test.
     */
    lazy val provider = new FoursquareProvider(cacheLayer, httpLayer, oAuthSettings)
  }
}
