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
package com.mohiva.play.silhouette.api

import play.api.i18n.Lang
import play.api.mvc.RequestHeader

/**
 * A trait to define Authorization objects that let you hook
 * an authorization implementation in SecuredActions.
 *
 * @tparam I The type of the identity.
 */
trait Authorization[I <: Identity] {

  /**
   * Checks whether the user is authorized to execute an action or not.
   *
   * @param identity The identity to check for.
   * @param request The current request header.
   * @param lang The current lang.
   * @return True if the user is authorized, false otherwise.
   */
  def isAuthorized(identity: I)(implicit request: RequestHeader, lang: Lang): Boolean
}
