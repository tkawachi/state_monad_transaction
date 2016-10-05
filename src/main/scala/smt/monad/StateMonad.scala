package smt.monad

import smt.{ContextWithDB, Context}

/**
  * Created by takezoux2 on 2016/10/05.
  */
case class StateMonad[S,A]( run : S => (S,A)) {

  def map[B](f: A => B) = {
    StateMonad[S,B](s => {
      val (s2, a) = run(s)
      (s2, f(a))
    })
  }

  def flatMap[B](f: A => StateMonad[S, B]) = {
    StateMonad[S,B](s => {
      val (s2, a) = run(s)
      f(a).run(s2)
    })
  }

  def runTx(m : StateMonad[Context,A]) = {
    // いい感じにtransactionをまとめることは出来るが、
    // Futureとのコンビネーションが良く無い
    val s = new ContextWithDB()
    s.beginTransaction()
    try {
      val t = m.run(s)
      s.commit()
      t
    }catch{
      case t: Throwable => {
        s.rollback()
        throw t
      }
    }
  }

}

