package smt

import org.slf4j.LoggerFactory
import scalikejdbc.{DBSession, NamedDB, Tx}

/**
  * Created by takezoux2 on 2016/09/14.
  */
trait Context {

  def borrow[T](shard: String)(func: DBSession => T) : T

  def beginTransaction() : Unit
  def commit() : Unit
  def rollback() : Unit
  def forceCommit() : Unit
  def forceRollback() : Unit

}

class ContextWithDB extends Context{

  private var transactionCount = 0
  private var sessions = Map.empty[String,SessionAndTx]
  private var autoCommitSessions = Map.empty[String,DBSession]

  case class SessionAndTx(session: DBSession, tx: Tx)

  def borrow[T](shard: String)(func: DBSession => T) : T = this.synchronized{
    if(transactionCount > 0){
      sessions.get(shard) match{
        case Some(s) => {
          func(s.session)
        }
        case None => {
          println(s"Begin transaction DB:${shard}")
          val db = NamedDB(shard)
          val tx = db.newTx
          tx.begin
          val session = DBSession(db.conn,Some(tx))
          sessions +=(shard -> SessionAndTx(session,tx))
          func(session)
        }
      }
    }else{
      autoCommitSessions.get(shard) match{
        case Some(s) => {
          func(s)
        }
        case None => {
          println(s"Open connection DB:${shard}")
          val db = NamedDB(shard)
          val session = db.autoCommitSession()
          autoCommitSessions +=(shard -> session)
          func(session)
        }
      }
    }
  }

  def beginTransaction() = this.synchronized{
    transactionCount += 1
  }


  def commit() = this.synchronized{
    transactionCount -= 1
    if(transactionCount == 0){
      forceCommit()
    }
  }
  def rollback() = {
    forceRollback()
  }

  def forceCommit() : Unit = this.synchronized{
    if(sessions.size > 0) {
      println("Commit")
    }
    transactionCount = 0
    sessions.foreach(_._2.tx.commit())
    close()
  }
  def forceRollback() : Unit = this.synchronized{
    println("Rollback")
    sessions.foreach(_._2.tx.rollback())
    close()
  }
  private def close() = {
    sessions.foreach(_._2.session.close())
    autoCommitSessions.foreach(_._2.close())
    sessions = Map.empty
    autoCommitSessions = Map.empty
  }



}

object Context{
  val logger = LoggerFactory.getLogger(classOf[Context])
}


