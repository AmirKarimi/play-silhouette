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
package com.mohiva.play.silhouette.core

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.{ WithApplication, FakeRequest, PlaySpecification }
import play.api.GlobalSettings
import play.api.i18n.{ Messages, Lang }
import play.api.mvc.{ RequestHeader, Result }
import play.api.mvc.Results._
import play.api.test.FakeApplication
import play.api.libs.json.Json
import com.mohiva.play.silhouette.core.services.{ AuthenticatorService, IdentityService }
import com.mohiva.play.silhouette.core.exceptions.{ AuthenticationException, AccessDeniedException }
import play.api.libs.concurrent.Akka
import akka.testkit.TestProbe
import akka.actor.{ Actor, Props }

/**
 * Test case for the [[com.mohiva.play.silhouette.core.Silhouette]] base controller.
 */
class SilhouetteSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `SecuredAction` action" should {
    "restrict access if no authenticator can be retrieved" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "restrict access if no identity could be found for an authenticator" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "update an authenticator if an identity could be found for it" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      await(controller.protectedAction(request))

      there was one(env.authenticatorService).update(any)
    }

    "display local not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env) {
        override def notAuthenticated(request: RequestHeader): Option[Future[Result]] = {
          Some(Future.successful(Unauthorized("local.not.authenticated")))
        }
      }

      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("local.not.authenticated")
    }

    "display global not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("global.not.authenticated")
    }

    "display fallback message if user isn't authenticated and fallback methods aren't implemented" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "publish `NotAuthenticatedEvent` if user isn't authenticated" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val listener = system.actorOf(Props(new Actor {
        def receive = {
          case e @ NotAuthenticatedEvent(_, _) => theProbe.ref ! e
        }
      }))

      env.eventBus.subscribe(listener, classOf[NotAuthenticatedEvent])

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request, lang))
    }

    "display local not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false)) {
        override def notAuthorized(request: RequestHeader): Option[Future[Result]] = {
          Some(Future.successful(Forbidden("local.not.authorized")))
        }
      }

      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("local.not.authorized")
    }

    "display global not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("global.not.authorized")
    }

    "display fallback message if user isn't authorized and fallback methods aren't implemented" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authorized"))
    }

    "publish `NotAuthorizedEvent` if user isn't authorized" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val listener = system.actorOf(Props(new Actor {
        def receive = {
          case e @ NotAuthorizedEvent(_, _, _) => theProbe.ref ! e
        }
      }))

      env.eventBus.subscribe(listener, classOf[NotAuthorizedEvent[TestIdentity]])

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(request)

      theProbe.expectMsg(500 millis, NotAuthorizedEvent(identity, request, lang))
    }

    "invoke action without authorization if user is authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain("full.access")
    }

    "invoke action with authorization if user is authenticated but not authorized" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain("full.access")
    }

    "publish `AuthenticatedEvent` if user is authenticated" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val listener = system.actorOf(Props(new Actor {
        def receive = {
          case e @ AuthenticatedEvent(_, _, _) => theProbe.ref ! e
        }
      }))

      env.eventBus.subscribe(listener, classOf[AuthenticatedEvent[TestIdentity]])

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request, lang))
    }

    "discard authentication cookie if user isn't authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }

      val controller = new SecuredController(env)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)

      there was one(env.authenticatorService).discard(any)
    }

    "handle an Ajax request" in new WithSecuredGlobal {
      implicit val req = FakeRequest().withHeaders("Accept" -> "application/json")

      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.protectedAction(req)

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsString(result) must /("result" -> "full.access")
    }
  }

  "The `UserAwareAction` action" should {
    "restrict access if no authenticator could be found" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "restrict access if no identity could be found for an authenticator" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "update an authenticator if an identity could be found for it" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      await(controller.userAwareAction(request))

      there was one(env.authenticatorService).update(any)
    }

    "grant access if an identity could be found" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("full.access"))
    }
  }

  "The `exceptionHandler` method" should {
    "translate an AccessDeniedException into a 403 Forbidden result" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }

      val controller = new SecuredController(env)
      val failed = Future.failed(new AccessDeniedException("Access denied"))
      val result = controller.recover(failed)

      status(result) must equalTo(FORBIDDEN)
    }

    "translate an AuthenticationException into a 401 Unauthorized result" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)
      env.authenticatorService.discard(any) answers { r => r.asInstanceOf[Result] }

      val controller = new SecuredController(env)
      val failed = Future.failed(new AuthenticationException("Not authenticated"))
      val result = controller.recover(failed)

      status(result) must equalTo(UNAUTHORIZED)
    }
  }

  /**
   * A test identity.
   *
   * @param loginInfo The linked login info.
   */
  case class TestIdentity(loginInfo: LoginInfo) extends Identity

  /**
   * A test authenticator.
   *
   * @param loginInfo The linked login info.
   */
  case class TestAuthenticator(loginInfo: LoginInfo) extends Authenticator

  /**
   * The context.
   */
  trait Context extends Scope {
    self: WithApplication =>

    /**
     * The Silhouette environment.
     */
    lazy val env = Environment[TestIdentity, TestAuthenticator](
      mock[IdentityService[TestIdentity]],
      mock[AuthenticatorService[TestAuthenticator]],
      Map(),
      new EventBus
    )

    /**
     * An identity.
     */
    lazy val identity = new TestIdentity(LoginInfo("test", "1"))

    /**
     * An authenticator.
     */
    lazy val authenticator = new TestAuthenticator(LoginInfo("test", "1"))

    /**
     * A fake request.
     */
    lazy implicit val request = FakeRequest()

    /**
     * A language.
     */
    lazy val lang = Lang.defaultLang

    /**
     * The Play actor system.
     */
    lazy implicit val system = Akka.system

    /**
     * The test probe.
     */
    lazy val theProbe = TestProbe()
  }

  /**
   * Runs a fake application with a secured global object.
   */
  class WithSecuredGlobal extends WithApplication(FakeApplication(withGlobal = Some(new GlobalSettings with SecuredSettings {

    /**
     * Called when a user isn't authenticated.
     *
     * @param request The request header.
     * @param lang The current selected lang.
     * @return The result to send to the client.
     */
    override def onNotAuthenticated(request: RequestHeader, lang: Lang) = {
      Some(Future.successful(Unauthorized("global.not.authenticated")))
    }

    /**
     * Called when a user isn't authorized.
     *
     * @param request The request header.
     * @param lang The current selected lang.
     * @return The result to send to the client.
     */
    override def onNotAuthorized(request: RequestHeader, lang: Lang) = {
      Some(Future.successful(Forbidden("global.not.authorized")))
    }

  }))) with Context

  /**
   * Runs a fake application with a default global object.
   */
  class WithDefaultGlobal extends WithApplication(FakeApplication()) with Context

  /**
   * A secured controller.
   *
   * @param env The silhouette environment.
   * @param authorization An authorization implementation.
   */
  class SecuredController(
    val env: Environment[TestIdentity, TestAuthenticator],
    val authorization: Authorization[TestIdentity] = SimpleAuthorization())
      extends Silhouette[TestIdentity, TestAuthenticator] {

    /**
     * A protected action.
     *
     * @return The result to send to the client.
     */
    def protectedAction = SecuredAction { implicit request =>
      render {
        case Accepts.Json() => Ok(Json.obj("result" -> "full.access"))
        case Accepts.Html() => Ok("full.access")
      }
    }

    /**
     * A protected action with authorization.
     *
     * @return The result to send to the client.
     */
    def protectedActionWithAuthorization = SecuredAction(authorization) { implicit request: SecuredRequest[_] =>
      Ok("full.access")
    }

    /**
     * A user aware action.
     *
     * @return The result to send to the client.
     */
    def userAwareAction = UserAwareAction { implicit request =>
      if (request.identity.isDefined) {
        Ok("full.access")
      } else {
        Unauthorized("not.authenticated")
      }
    }

    /**
     * Method to test the `exceptionHandler` method of the Silhouette controller.
     *
     * @param f The future to recover from.
     * @param request The request header.
     * @return The result to send to the client.
     */
    def recover(f: Future[Result])(implicit request: RequestHeader): Future[Result] = {
      f.recoverWith(exceptionHandler)
    }
  }

  /**
   * A simple authorization class.
   *
   * @param isAuthorized True if the access is authorized, false otherwise.
   */
  case class SimpleAuthorization(isAuthorized: Boolean = true) extends Authorization[TestIdentity] {

    /**
     * Checks whether the user is authorized to execute an action or not.
     *
     * @param identity The identity to check for.
     * @param request The current request header.
     * @param lang The current lang.
     * @return True if the user is authorized, false otherwise.
     */
    def isAuthorized(identity: TestIdentity)(implicit request: RequestHeader, lang: Lang): Boolean = isAuthorized
  }
}
