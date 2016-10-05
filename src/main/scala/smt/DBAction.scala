package smt

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Created by takezoux2 on 2016/09/14.
  */
case class DBAction[A](val _run : Context => ( (Context,Try[A]) => Unit) => Unit) {


  def map[B](func: A => B) : DBAction[B] = {
    new DBAction[B](c => callback => {
      _run(c)((c2,trA) => {
        try {
          callback(c2, trA.map(a => func(a)))
        }catch{
          case t : Throwable => callback(c2,Failure(t))
        }
      })
    })
  }

  def flatMap[B](func : A => DBAction[B]) : DBAction[B] = {
    new DBAction[B](c => callback => {
      _run(c)((c2,trA) => trA match{
        case Success(a) => {
          try{
            func(a)._run(c2)((c3,trB) => callback(c3,trB))
          }catch{
            case t : Throwable => callback(c2,Failure(t))
          }
        }
        case f : Failure[A] => callback(c2,f.asInstanceOf[Failure[B]])
      })
    })
  }

  def andThen[B](next : DBAction[B]) = {
    flatMap[B](_ => next)
  }


  /**
    *
    * @param context
    * @return
    */
  def run(context: Context)(callback: Try[A] => Unit) = {
    try {
      _run(context)((c, trA) => {
        try{
          callback(trA)
        }catch{
          case t : Throwable => // no responsibility after callback
        }
      })
    }catch{
      case t: Throwable => callback(Failure(t))
    }
  }

  def runTx()(implicit runner: DBActionRunner = DBAction.defaultDbRunner) : Future[A] = {
    runner.runForceTx(this)
  }
  def run()(implicit runner: DBActionRunner = DBAction.defaultDbRunner) : Future[A] = {
    runner.run(this)
  }

  def runTxAwait()(implicit runner: DBActionRunner = DBAction.defaultDbRunner) : A = {
    runner.runTxAwait(this)
  }

  def recover( rec : PartialFunction[Throwable,A]) = {
    DBAction[A](c => callback => {
      _run(c)((c2,trA) => trA match{
        case s @ Success(a) => callback(c2,s)
        case f @ Failure(t) => {
          if(rec.isDefinedAt(t)){
            callback(c2,Success(rec(t)))
          }else{
            callback(c2,f)
          }
        }
      })
    })
  }


  /**
    * アクション開始前に何らかのアクションを実行する
    * ログの出力などを行うと便利です
    *
    * @param anyAction
    * @return
    */
  def preAction( anyAction : => Unit) = {
    new DBAction[A](c => callback => {
      anyAction
      _run(c)(callback)
    })
  }
  def postSuccess(anyAction : => Unit) = {
    new DBAction[A](c => callback => {
      _run(c)((c2,r) => {
        r match{
          case Success(_) => anyAction
          case _ =>
        }
        callback(c2,r)
      })
    })
  }
  def postFailure(anyAction : => Unit) = {
    new DBAction[A](c => callback => {
      _run(c)((c2,r) => {
        r match{
          case Failure(_) => anyAction
          case _ =>
        }
        callback(c2,r)
      })
    })
  }

  def zip[B](that: DBAction[B]) : DBAction[(A,B)] = {
    for(a <- this;
    b <- that) yield (a,b)
  }

  def flatten[B](implicit NESTED : A =:= DBAction[B]) = {
    this.flatMap(a => a.asInstanceOf[DBAction[B]])
  }

  /**
    * implicit val e = new WithFilterFailure(new Exception("not match"))
    * for(a <- action;
    *  b <- actionB if a == "hoge") yield "ok"
    *
    * のように使用出来ます
    * (for文のifブロックがシュガーシンタックス)
    * @param f
    * @param e
    * @return
    */
  def withFilter(f : A => Boolean)(implicit e: WithFilterFailure) : DBAction[A] = {
    DBAction(c => callback => {
      _run(c)((c2,tr) => {
        tr match{
          case Success(a) => if(f(a)){
            callback(c2,tr)
          }else{
            callback(c2,Failure(e.t))
          }
          case Failure(t) => {
            callback(c2,tr)
          }
        }
      })
    })
  }
}
class WithFilterFailure(_t: => Throwable){
  def t = _t
}


object DBAction{

  val OK = DBAction.successful(true)
  val NG = DBAction.successful(false)
  val Empty = DBAction.successful[Unit](())

  val defaultDbRunner = DefaultDBActionRunner

  def successful[A](v : A) = {
    new DBAction[A](c => callback => {
      callback(c,Success(v))
    })
  }
  def action( ac: => Unit) = {
    new DBAction[Unit](c => callback => {
      ac
      callback(c, Success(()))
    })
  }

  def sequence[A](actions : Iterable[DBAction[A]]) : DBAction[List[A]] = {
    if(actions.size == 0) return DBAction.successful(Nil)
    new DBAction[List[A]](c => callback => {
      var currentC = c
      var successCount = 0
      var list = List[A]()
      var stop = false

      for(ac <- actions if !stop) {
        ac._run(currentC)((c,trA) => trA match{
          case Success(a) => {
            currentC = c
            successCount += 1
            list = a :: list
            if(successCount == actions.size){
              stop = true
              callback(c,Success(list.reverse))
            }
          }
          case Failure(t) => {
            stop = true
            callback(c,Failure(t))
          }
        })
      }

    })
  }

  /**
    * @param f
    * @tparam A
    * @return
    */
  def future[A](f: Future[A]) = {
    new DBAction[A](c => callback => {
      import scala.concurrent.ExecutionContext.Implicits.global
      f.onComplete({
        case tr => callback(c,tr)
      })
    })
  }

  def error[A](e: Exception) = {
    new DBAction[A](c => callback => {
      callback(c,Failure(e))
    })
  }

}



