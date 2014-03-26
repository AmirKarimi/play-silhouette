/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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

import play.api.libs.json.JsObject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Success, Failure, Try }
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider }
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import VKProvider._
import OAuth2Provider._

/**
 * A Vk OAuth 2 provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 * @see http://vk.com/dev/auth_sites
 * @see http://vk.com/dev/api_requests
 * @see http://vk.com/pages.php?o=-1&p=getProfiles
 */
class VKProvider(
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth2Settings)
    extends OAuth2Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Vk

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[Try[SocialProfile[OAuth2Info]]] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ ErrorCode).as[Int]
          val errorMsg = (error \ ErrorMsg).as[String]

          Failure(new AuthenticationException(SpecifiedProfileError.format(id, errorCode, errorMsg)))
        case _ =>
          val me = (json \ Response).apply(0)
          val userId = (me \ ID).as[Long]
          val firstName = (me \ FirstName).asOpt[String]
          val lastName = (me \ LastName).asOpt[String]
          val avatarURL = (me \ Photo).asOpt[String]

          Success(SocialProfile(
            loginInfo = LoginInfo(id, userId.toString),
            authInfo = authInfo,
            firstName = firstName,
            lastName = lastName,
            avatarURL = avatarURL))
      }
    }.recover {
      case e => Failure(new AuthenticationException(UnspecifiedProfileError.format(id), e))
    }
  }
}

/**
 * The companion object.
 */
object VKProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The VK constants.
   */
  val Vk = "vk"
  val API = "https://api.vk.com/method/getProfiles?fields=uid,first_name,last_name,photo&access_token=%s"
  val ErrorCode = "error_code"
  val ErrorMsg = "error_msg"
  val Response = "response"
  val ID = "uid"
  val FirstName = "first_name"
  val LastName = "last_name"
  val Photo = "photo"
}
