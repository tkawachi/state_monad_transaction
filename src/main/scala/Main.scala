import org.joda.time.DateTime
import scalikejdbc.{DB, ConnectionPool, NamedDB}
import smt.{DefaultDBActionRunner, DBAction}
import smt.model.{LoginTime, User}

import scala.concurrent.Await
import scala.util.Success
import scala.concurrent.duration._

/**
  * Created by takezoux2 on 2016/10/05.
  */
object Main {

  def main(args: Array[String]) : Unit = {
    initDB()

    implicit val runner = DefaultDBActionRunner

    val f = createUserAction().run()

    val f2 = updateLoginTime(1).run()


    println(Await.result(f,1.seconds))
    println(Await.result(f2,1.seconds))


    for(i <- 0 until 10){
      Await.ready(createThenUpdate().runTx(),1.seconds)
    }

    for(i <- 0 until 10){
      Await.ready(createThenError().runTx(),1.seconds)
    }

    for(i <- 0 until 1){
      Await.ready(createThenUpdate().runTx(),1.seconds)
    }

    Await.result(selectUsers("db1").run(), 2.seconds).foreach(user => {
      println(user)
    })


    ConnectionPool.closeAll()
    DefaultDBActionRunner.executionContext
    println("Done")
  }

  var nextId = 1

  def createThenUpdate() = {
    for(user <- createUserAction();
        lt <- updateLoginTime(user.id)) yield{
      user
    }
  }
  def createThenError() = {
    for(user <- createUserAction();
    _ <- DBAction.error[Unit](new Exception("Unknown error"));
    lt <- updateLoginTime(user.id)) yield{
      user
    }
  }

  def createUserAction() : DBAction[User] = {
    DBAction[User](c => callback => {
      val id = nextId
      nextId += 1
      val shard = getShard(id)
      val user = new User(id,"User" + id)
      c.beginTransaction()
      c.borrow(shard)(implicit s => {
        User.insert(user)
      })
      c.commit()
      callback(c,Success(user))
    })
  }


  def updateLoginTime(userId: Long) : DBAction[LoginTime] = {
    DBAction[LoginTime](c => callback => {
      val shard = getShard(userId)
      val lt = LoginTime(userId,new DateTime())
      c.beginTransaction()
      c.borrow(shard)(implicit s => {
        if(LoginTime.update(lt) != 1){
          LoginTime.insert(lt)
        }
      })
      c.commit()
      callback(c,Success(lt))
    })
  }

  def selectUsers(shard: String) = {
    DBAction[List[User]](c => callback => {
      val users = c.borrow(shard)(implicit s => {
        User.selectAll()
      })
      callback(c,Success(users))
    })
  }

  def selectLoginTimes(shard: String) = {
    DBAction[List[LoginTime]](c => callback => {
      val loginTimes = c.borrow(shard)(implicit s => {
        LoginTime.selectAll()
      })
      callback(c,Success(loginTimes))
    })

  }

  def getShard(userId: Long) = {
    "db" + (userId % 2 + 1)
  }



  def initDB() = {Class.forName("org.h2.Driver")
    ConnectionPool.add("db1","jdbc:h2:mem:db1",null,null)
    ConnectionPool.add("db2","jdbc:h2:mem:db2",null,null)

    createTables("db1")
    createTables("db2")
  }
  def createTables(shard: String) = {
    NamedDB(shard).autoCommit(implicit s => {
      s.executeUpdate(
        """CREATE TABLE User(
          |id INT PRIMARY KEY,
          |username VARCHAR(100) UNIQUE
          |);
        """.stripMargin)
      s.executeUpdate(
        """CREATE TABLE Login_Time(
          |user_id INT PRIMARY KEY,
          |login_time TIMESTAMP
          |)
        """.stripMargin)

    })
  }

}
