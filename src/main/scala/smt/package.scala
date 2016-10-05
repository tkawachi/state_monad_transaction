import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.std.scalaFuture._
import scalaz.{ContT, Kleisli}

package object smt {

  type FutureCont[A] = ContT[Future, A, A]

  type DBActionz[A] = Kleisli[FutureCont, Context, A]

  object DBActionz {
    def apply[A](f: Context => (A => Future[A]) => Future[A]) =
      Kleisli[FutureCont, Context, A] { ctx =>
        ContT[Future, A, A](f(ctx))
      }

    def error[A](e: Exception) = DBActionz[A](_ => _ => Future.failed(e))
  }

  implicit class DBActionzSyntax[A](z: DBActionz[A])(implicit ec: ExecutionContext) {

    def runWithContext(context: Context): Future[A] = {
      z.run(context).run_.andThen {
        case Success(_) => context.forceCommit()
        case Failure(_) => context.forceRollback()
      }
    }

    def runWithRunner(runner: DBActionRunner): Future[A] = runWithContext(runner.newContext())

    def runTx(runner: DBActionRunner): Future[A] = {
      val context = runner.newContext()
      context.beginTransaction()
      runWithContext(context)
    }

    def recover(pf: PartialFunction[Throwable, A]) =
      DBActionz[A](ctx => cb => z.run(ctx).run_.recover(pf))

    def recoverWith(pf: PartialFunction[Throwable, Future[A]]) =
      DBActionz[A](ctx => cb => z.run(ctx).run_.recoverWith(pf))

    def preAction(f: => Unit): DBActionz[A] =
      DBActionz[A]{ctx => cb => Future(f).flatMap (_ => z.run(ctx).run_ )}
  }
}
