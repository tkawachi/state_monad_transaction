package smt

import java.util.concurrent.Executors

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * Created by takezoux2 on 2016/09/14.
  */
trait DBActionRunner {

  implicit def executionContext: ExecutionContext

  def newContext() : Context

  def run[A](dbAction: DBAction[A]) : Future[A] = {
    runWith(dbAction,newContext())
  }
  def runForceTx[A](dbAction: DBAction[A]) : Future[A] = {
    val c = newContext()
    c.beginTransaction()
    runWith(dbAction,c)
  }

  private def runWith[A](dbAction: DBAction[A],c: Context) : Future[A] = {
    val p = Promise[A]()
    Future {
      dbAction.run(c)(r => {
        r match {
          case Success(v) => {
            c.forceCommit()
          }
          case Failure(t) => {
            c.forceRollback()
          }
        }
        p.complete(r)
      })
    }.onFailure({
      case t => {
        try{c.forceRollback()}
        finally {
          p.complete(Failure(t))
        }
      }
    })
    p.future
  }


  /**
    * 結果の完了まで待つ
    * Futureが一回挟まらない分だけAwait.ready(run(dbAction))よりパフォーマンスは良くなる
    *
    * @param dbAction
    * @tparam A
    * @return
    */
  def runTxAwait[A](dbAction: DBAction[A]) : A = {
    // 大体の場合は、callbackは同期的に呼び出されるので、
    // このメソッドは同期的に終了することになる
    // 十分に数が確保されたスレッド内で実行する場合は、このメソッドを使用して実行しても良い
    val c = newContext()
    val p = Promise[A]()
    c.beginTransaction()
    try{
      dbAction.run(c)(r => {
        r match {
          case Success(v) => {
            c.forceCommit()
          }
          case Failure(t) => {
            c.forceRollback()
          }
        }
        p.complete(r)
      })
    }catch{
      case t: Throwable => {
        try{c.forceRollback()}
        finally {
          p.complete(Failure(t))
        }
      }
    }

    Await.result(p.future,10.seconds)
  }

}


object DefaultDBActionRunner extends DBActionRunner{


  override def newContext(): Context = new ContextWithDB()

  override implicit val executionContext: ExecutionContext = {
    ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(100)
    )
  }
}