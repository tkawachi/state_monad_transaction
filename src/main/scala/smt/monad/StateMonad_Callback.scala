package smt.monad

import smt.{ContextWithDB, Context}

/**
  * Created by takezoux2 on 2016/10/05.
  */
case class StateMonad_Callback[S,A]( run: S => ((S,A) => Unit) => Unit) {

  def map[B](f: A => B) = {
    StateMonad_Callback[S,B](s => callback => {
      run(s)((s2,a) => {
        callback(s2,f(a))
      })
    })
  }

  def flatMap[B](f : A => StateMonad_Callback[S,B]) = {
    StateMonad_Callback[S,B](s => callback => {
      run(s)((s2,a) => {
        f(a).run(s2)((s3,b) => callback(s3,b))
      })
    })
  }

  def runTx(m: StateMonad_Callback[Context,A])(callback : A => Unit) = {
    val c = new ContextWithDB()
    c.beginTransaction()
    try {
      m.run(c)((s, a) => {
        c.commit()
        callback(a)
      })
    }catch{
      case t: Throwable => {
        // runの後の処理が、同じスレッドで実行されると限らないため、
        // 例外を拾えない可能性がある
        c.rollback()
        // callbackに対して結果を返せない
        throw t
      }
    }
  }

}
