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
import java.net.URLEncoder._
import scala.concurrent.Future
import play.api.test.{ FakeRequest, WithApplication, PlaySpecification }
import org.specs2.matcher.{ ThrownExpectations, JsonMatchers }
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import com.mohiva.play.silhouette.core.utils.{ CacheLayer, HTTPLayer }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import com.mohiva.play.silhouette.core.{ AuthenticationException, AccessDeniedException }
import OAuth2Provider._
import play.api.libs.ws.WS

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.OAuth2ProviderSpec]] class.
 *
 * These tests will be additionally executed before every OAuth2 provider spec.
 */
abstract class OAuth2ProviderSpec extends PlaySpecification with Mockito with JsonMatchers {
  isolated

  "The authenticate method" should {
    val c = context
    "throw an AccessDeniedException if 'error' key with value 'access_denied' exists in query string" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Error + "=" + AccessDenied)
      await(c.provider.authenticate()) must throwAn[AccessDeniedException].like {
        case e => e.getMessage must startWith(AuthorizationError.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if 'error' key with unspecified value exists in query string" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Error + "=unspecified")
      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(AuthorizationError.format(c.provider.id, "unspecified"))
      }
    }

    "redirect to authorization URL if authorization code doesn't exists in request" in new WithApplication {
      implicit val req = FakeRequest(GET, "/")
      await(c.provider.authenticate()) must beLeft.like {
        case r =>
          val result = Future.successful(r)
          status(result) must equalTo(SEE_OTHER)
          session(result).get(CacheKey) must beSome.which(s => UUID.fromString(s).toString == s)
          redirectLocation(result) must beSome.which { url =>
            val urlParams = c.urlParams(url)
            val params = c.oAuthSettings.scope.foldLeft(List(
              (ClientID, c.oAuthSettings.clientID),
              (RedirectURI, c.oAuthSettings.redirectURL),
              (ResponseType, Code),
              (State, urlParams(State))) ++ c.oAuthSettings.authorizationParams.toList) {
              case (p, s) => (Scope, s) :: p
            }
            url must be equalTo (c.oAuthSettings.authorizationURL + params.map { p =>
              encode(p._1, "UTF-8") + "=" + encode(p._2, "UTF-8")
            }.mkString("?", "&", ""))
          }
      }
    }

    "cache the state if authorization code doesn't exists in request" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "/").withSession(CacheKey -> cacheID)
      await(c.provider.authenticate()) must beLeft.like {
        case r =>
          val result = Future.successful(r)
          val cacheID = session(result).get(CacheKey).get
          val url = redirectLocation(result).get
          val urlParams = c.urlParams(url)

          there was one(c.cacheLayer).set(cacheID, urlParams(State), CacheExpiration)
      }
    }

    "throw an AuthenticationException if code exists in URL but info doesn't exists in session" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(CacheKeyNotInSession.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if code exists in URL but info doesn't exists in cache" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code").withSession(CacheKey -> cacheID)
      c.cacheLayer.get[String](cacheID) returns Future.successful(None)

      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(CachedStateDoesNotExists.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if code exists in URL but info doesn't exists in session" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code").withSession(CacheKey -> cacheID)
      c.cacheLayer.get[String](cacheID) returns Future.successful(Some(state))

      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(RequestStateDoesNotExists.format(c.provider.id, ""))
      }
    }

    "throw an AuthenticationException if cached state doesn't equal request sate" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      val cachedState = UUID.randomUUID().toString
      val requestState = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + requestState).withSession(CacheKey -> cacheID)
      c.cacheLayer.get[String](cacheID) returns Future.successful(Some(cachedState))

      await(c.provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(StateIsNotEqual.format(c.provider.id, ""))
      }
    }

    "submit the proper params to the access token post request" in new WithApplication {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val params = Map(
        ClientID -> Seq(c.oAuthSettings.clientID),
        ClientSecret -> Seq(c.oAuthSettings.clientSecret),
        GrantType -> Seq(AuthorizationCode),
        Code -> Seq("my.code"),
        RedirectURI -> Seq(c.oAuthSettings.redirectURL)) ++ c.oAuthSettings.accessTokenParams.mapValues(Seq(_))
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)

      // We must use this neat trick here because it isn't possible to check the post call with a verification,
      // because of the implicit params needed for the post call. On the other hand we can test it in the abstract
      // spec, because we throw an exception in both cases which stops the test once the post method was called.
      // This protects as for an NPE because of the not mocked dependencies. The other solution would be to execute
      // this test in every provider with the full mocked dependencies.
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) answers {
        _.equals(params) match {
          case true => throw new RuntimeException("success")
          case false => throw new RuntimeException("failure")
        }
      }
      c.cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      c.httpLayer.url(c.oAuthSettings.accessTokenURL) returns requestHolder

      await(c.provider.authenticate()) must throwAn[RuntimeException].like {
        case e => e.getMessage must startWith("success")
      }
    }
  }

  /**
   * Defines the context for the abstract OAuth2 provider spec.
   *
   * @return The Context to use for the abstract OAuth2 provider spec.
   */
  protected def context: OAuth2ProviderSpecContext
}

/**
 * Context for the OAuth2ProviderSpec.
 */
trait OAuth2ProviderSpecContext extends Scope with Mockito with ThrownExpectations {

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
   * A OAuth2 info.
   */
  lazy val oAuthInfo = OAuth2Info(
    accessToken = "my.access.token",
    tokenType = None,
    expiresIn = None,
    refreshToken = None)

  /**
   * The OAuth2 settings.
   */
  def oAuthSettings: OAuth2Settings

  /**
   * The provider to test.
   */
  def provider: OAuth2Provider

  /**
   * Extracts the params of a URL.
   *
   * @param url The url to parse.
   * @return The params of a URL.
   */
  def urlParams(url: String) = (url.split('&') map { str =>
    val pair = str.split('=')
    pair(0) -> pair(1)
  }).toMap
}
