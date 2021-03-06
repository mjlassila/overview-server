package controllers

import com.google.inject.ImplementedBy
import java.util.Date
import javax.inject.Inject
import play.api.i18n.{MessagesApi,Messages}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.{AnyContent,MessagesActionBuilder,PreferredMessagesProvider,RequestHeader}
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.{AuthResults,OptionallyAuthorizedAction}
import controllers.auth.Authorities.anyUser
import controllers.backend.SessionBackend
import mailers.Mailer
import models.User
import models.tables.Users

/**
 * Handles reset-password.
 *
 * RESTful methods:
 *
 * new: form to create a new reset-password process ("enter an email address")
 * create: (given an email address) creates a token and emails the user
 * edit: (given a token) edits the user's password
 * update: (given a token and password) changes the password, clears the token
 *         and logs in
 */
class PasswordController @Inject() (
  sessionBackend: SessionBackend,
  storage: PasswordController.Storage,
  mail: PasswordController.Mail,
  messagesAction: MessagesActionBuilder,
  val controllerComponents: ControllerComponents,
  newHtml: views.html.Password._new,
  editHtml: views.html.Password.edit,
  errorHtml: views.html.Password.editError
) extends BaseController {
  private lazy val newForm = controllers.forms.NewPasswordForm()
  private lazy val editForm = controllers.forms.EditPasswordForm()

  def _new() = optionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case None => Ok(newHtml(newForm))
      case Some(_) => doRedirect
    }
  }

  def edit(token: String) = optionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case Some(_) => doRedirect
      case None => {
        storage.findUserByResetToken(token) match {
          case None => showInvalidToken
          case Some(u) => Ok(editHtml(u, editForm))
        }
      }
    }
  }

  private def doRedirect = Redirect(routes.WelcomeController.show)
  private def showInvalidToken(implicit messagesProvider: PreferredMessagesProvider, request: RequestHeader) = BadRequest(errorHtml())

  def create() = messagesAction { implicit request =>
    newForm.bindFromRequest.fold(
      formWithErrors => BadRequest(newHtml(formWithErrors)), // no email address given
      email => {
        // We got a valid email address, but that doesn't mean this is a real
        // user. We need to send an email to the address either way.
        storage.findUserByEmail(email) match {
          case None => {
            val resetPasswordFormUrl = routes.PasswordController._new().absoluteURL
            mail.sendCreateErrorUserDoesNotExist(email, resetPasswordFormUrl)
          }
          case Some(user) => {
            // Success: generate a token and send an email
            val token = storage.addResetPasswordTokenToUser(user)
            val resetPasswordUrl = routes.PasswordController.edit(token).absoluteURL
            mail.sendCreated(user.copy(resetPasswordToken=Some(token)), resetPasswordUrl)
          }
        }

        // Fake success either way
        doRedirect.flashing(
          "success" -> request.messages("controllers.PasswordController.create.success", email),
          "event" -> "password-create"
        )
      }
    )
  }

  def update(token: String) = messagesAction.async { implicit request =>
    storage.findUserByResetToken(token) match {
      case None => Future.successful(showInvalidToken)
      case Some(user) => {
        editForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(editHtml(user, formWithErrors))),
          newPassword => {
            for {
              _ <- storage.resetPassword(user, newPassword)
              session <- sessionBackend.create(user.id, request.remoteAddress)
            } yield AuthResults.loginSucceeded(request, session).flashing(
              "success" -> request.messages("controllers.PasswordController.update.success"),
              "event" -> "password-update"
            )
          }
        )
      }
    }
  }
}

object PasswordController {
  val SecondsResetTokenIsValid = 14400 // 4 hours

  @ImplementedBy(classOf[PasswordController.BlockingDatabaseStorage])
  trait Storage {
    def findUserByEmail(email: String): Option[User]
    def findUserByResetToken(token: String): Option[User]
    def addResetPasswordTokenToUser(user: User): String
    def resetPassword(user: User, password: String): Future[Unit]
  }

  @ImplementedBy(classOf[PasswordController.DefaultMail])
  trait Mail {
    def sendCreated(user: User, resetPasswordUrl: String)(implicit messages: Messages) : Unit
    def sendCreateErrorUserDoesNotExist(email: String, resetPasswordFormUrl: String)(implicit messages: Messages) : Unit
  }

  class BlockingDatabaseStorage @Inject() extends Storage with HasBlockingDatabase {
    import database.api._

    override def findUserByEmail(email: String) = {
      blockingDatabase.option(Users.filter(_.email === email))
    }

    override def findUserByResetToken(token: String) = {
      blockingDatabase.option(
        Users
          .filter(_.resetPasswordToken === token)
          .filter(_.resetPasswordSentAt >= new java.sql.Timestamp(new Date().getTime - SecondsResetTokenIsValid * 1000))
      )
    }

    override def addResetPasswordTokenToUser(user: User) = {
      val token = User.generateToken
      blockingDatabase.runUnit(
        Users
          .filter(_.id === user.id)
          .map(u => (u.resetPasswordToken, u.resetPasswordSentAt))
          .update((Some(token), Some(new java.sql.Timestamp(new Date().getTime))))
      )
      token
    }

    override def resetPassword(user: User, password: String) = {
      database.runUnit(
        Users
          .filter(_.id === user.id)
          .map(u => (u.passwordHash, u.resetPasswordToken, u.resetPasswordSentAt))
          .update((User.hashPassword(password), None, None))
      )
    }
  }

  class DefaultMail @Inject() (mailer: Mailer) extends PasswordController.Mail {
    override def sendCreated(user: User, resetPasswordUrl: String)(implicit messages: Messages) = {
      val mail = mailers.Password.create(user, resetPasswordUrl)
      mailer.send(mail)
    }

    override def sendCreateErrorUserDoesNotExist(email: String, resetPasswordFormUrl: String)(implicit messages: Messages) = {
      val mail = mailers.Password.createErrorUserDoesNotExist(email, resetPasswordFormUrl)
      mailer.send(mail)
    }
  }
}
