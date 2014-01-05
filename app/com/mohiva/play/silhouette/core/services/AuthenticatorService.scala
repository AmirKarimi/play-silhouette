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
package com.mohiva.play.silhouette.core.services

import com.mohiva.play.silhouette.core.{Identity, Authenticator}
import scala.concurrent.Future

/**
 * The authenticator store is in charge of persisting authenticators for the Silhouette module.
 */
trait AuthenticatorService {

  /**
   * Creates a new authenticator ID for the specified identity.
   *
   * @param identity The identity for which the ID should be created.
   * @param expiry The expiry of the authenticator in minutes. Defaults to 12 hours.
   * @return An authenticator.
   */
  def create[I <: Identity](identity: I, expiry: Int = 12 * 60): Future[Authenticator]

  /**
   * Saves the authenticator.
   *
   * @param authenticator The authenticator to save.
   * @return The saved authenticator or None if the authenticator couldn't be saved.
   */
  def save(authenticator: Authenticator): Future[Option[Authenticator]]

  /**
   * Updates an existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator or None if the authenticator couldn't be updated.
   */
  def update(authenticator: Authenticator): Future[Option[Authenticator]]

  /**
   * Finds an authenticator.
   *
   * @param id The authenticator ID.
   * @return The found authenticator or None if no authenticator couldn't be found for the given ID.
   */
  def findByID(id: String): Future[Option[Authenticator]]

  /**
   * Deletes an authenticator.
   *
   * @param id The authenticator ID.
   */
  def deleteByID(id: String)
}
