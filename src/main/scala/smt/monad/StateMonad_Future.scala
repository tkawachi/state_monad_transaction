package smt.monad

import smt.{DBAction, ContextWithDB, Context}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by takezoux2 on 2016/10/05.
  */
case class StateMonad_Future[S,A](run: S => Future[(S,A)]) {

  def map[B](f : A => B)(implicit ec: ExecutionContext) = {
    StateMonad_Future[S,B](s => {
      run(s).map(t => (t._1,f(t._2)))
    })
  }

  def flatMap[B](f : A => StateMonad_Future[S,B])(implicit ec: ExecutionContext) = {
    StateMonad_Future[S,B](s => {
      run(s).flatMap(t => {
        f(t._2).run(t._1)
      })
    })
  }

  def runTx(a: StateMonad_Future[Context,A])(implicit ec: ExecutionContext) = {
    // 各所にExecutionContextを引き回す必要があり
    val c = new ContextWithDB()
    c.beginTransaction()
    val f = a.run(c)
    f.onComplete({
      case Success(_) => c.commit()
      case Failure(_) => c.rollback()
    })
    f
  }


}
