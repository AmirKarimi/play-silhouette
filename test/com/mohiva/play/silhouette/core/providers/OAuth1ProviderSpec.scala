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
package com.mohiva.play.silhouette.core.providers

import java.util.UUID
import play.api.test.{ FakeRequest, WithApplication, PlaySpecification }
import org.specs2.matcher.{ ThrownExpectations, JsonMatchers }
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import scala.util.{ Success, Failure }
import scala.concurrent.Future
import com.mohiva.play.silhouette.core.utils.{ CacheLayer, HTTPLayer }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import com.mohiva.play.silhouette.core.AccessDeniedException
import com.mohiva.play.silhouette.core.AuthenticationException
import OAuth1Provider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.OAuth1ProviderSpec]] class.
 *
 * These tests will be additionally executed before every OAuth1 provider spec.
 */
abstract class OAuth1ProviderSpec extends PlaySpecification with Mockito with JsonMatchers {
  isolated

  "The authenticate method" should {
    val c = context
    "throw an AccessDeniedException if denied key exists in query string" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Denied + "=")
      await(c.provider.authenticate()) must throwAn[AccessDeniedException].like {
        case e => e.getMessage must startWith(AuthorizationError.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if request token cannot be retrieved" in new WithApplication {
      implicit val req = FakeRequest()
      c.oAuthService.retrieveRequestToken(c.oAuthSettings.callbackURL) returns Future.successful(Failure(new Exception("")))

      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(ErrorRequestToken.format(c.provider.id, ""))
      }
    }

    "redirect to authorization URL if request token could be retrieved" in new WithApplication {
      implicit val req = FakeRequest()
      c.oAuthService.retrieveRequestToken(c.oAuthSettings.callbackURL) returns Future.successful(Success(c.oAuthInfo))
      c.oAuthService.redirectUrl(any) returns c.oAuthSettings.authorizationURL

      await(c.provider.authenticate()) must beLeft.like {
        case r =>
          val result = Future.successful(r)
          status(result) must equalTo(SEE_OTHER)
          session(result).get(OAuth1Provider.CacheKey) must beSome.which(s => UUID.fromString(s).toString == s)
          redirectLocation(result) must beSome.which(_ == c.oAuthSettings.authorizationURL)
      }
    }

    "cache the oauth info if request token could be retrieved" in new WithApplication {
      implicit val req = FakeRequest()
      c.oAuthService.retrieveRequestToken(c.oAuthSettings.callbackURL) returns Future.successful(Success(c.oAuthInfo))
      c.oAuthService.redirectUrl(any) returns c.oAuthSettings.authorizationURL

      await(c.provider.authenticate()) must beLeft.like {
        case r =>
          val result = Future.successful(r)
          val cacheID = session(result).get(OAuth1Provider.CacheKey).get

          there was one(c.cacheLayer).set(cacheID, c.oAuthInfo, CacheExpiration)
      }
    }

    "throw an AuthenticationException if OAuthVerifier exists in URL but info doesn't exists in session" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier")
      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(CacheKeyNotInSession.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if OAuthVerifier exists in URL but info doesn't exists in cache" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      c.cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(None)

      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(CachedTokenDoesNotExists.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if access token cannot be retrieved" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      c.cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(c.oAuthInfo))
      c.oAuthService.retrieveAccessToken(c.oAuthInfo, "my.verifier") returns Future.successful(Failure(new Exception("")))

      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(ErrorAccessToken.format(c.provider.id, ""))
      }
    }
  }

  /**
   * Defines the context for the abstract OAuth1 provider spec.
   *
   * @return The Context to use for the abstract OAuth1 provider spec.
   */
  protected def context: OAuth1ProviderSpecContext
}

/**
 * Context for the OAuth1ProviderSpec.
 */
trait OAuth1ProviderSpecContext extends Scope with Mockito with ThrownExpectations {

  /**
   * The auth info service mock.
   */
  lazy val authInfoService: AuthInfoService = mock[AuthInfoService]

  /**
   * The cache layer mock.
   */
  lazy val cacheLayer: CacheLayer = mock[CacheLayer]

  /**
   * The HTTP layer mock.
   */
  lazy val httpLayer: HTTPLayer = mock[HTTPLayer]

  /**
   * A OAuth1 info.
   */
  lazy val oAuthInfo = OAuth1Info("my.token", "my.secret")

  /**
   * The OAuth1 service mock.
   */
  lazy val oAuthService: OAuth1Service = mock[OAuth1Service]

  /**
   * The OAuth1 settings.
   */
  def oAuthSettings: OAuth1Settings

  /**
   * The provider to test.
   */
  def provider: OAuth1Provider
}
