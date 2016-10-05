package smt.monad

import smt.{ContextWithDB, Context}

import scala.util.{Success, Failure, Try}

/**
  * ついでにTryモナドも合体しています。
  * Created by takezoux2 on 2016/10/05.
  */
case class StateContMonad[S,R,A](run: S => ((S,Try[A]) => R) => R) {

  def map[B](f: A => B) = {
    StateContMonad[S,R,B](s => cont => {
      run(s)((s2,a) => {
        cont(s2,f(a))
      })
    })
  }

  def flatMap[B](f: A => StateContMonad[S,R,B]) = {
    StateContMonad[S,R,B](s => cont => {
      run(s)((s2,a) => {
        f(a).run(s2)((s3,b) => cont(s3,b))
      })
    })
  }

  def runTx(m: StateContMonad[Context,R,A])(toR: Try[A] => R) : R = {
    val s = new ContextWithDB()
    s.beginTransaction()
    m.run(s)((s2, a) => {
      a match{
        case Success(_) => s.commit()
        case Failure(_) => s.rollback()
      }
      toR(a)
    })
  }

}
