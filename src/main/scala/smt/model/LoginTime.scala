package smt.model

import org.joda.time.DateTime
import scalikejdbc._, SQLInterpolation._


/**
  * Created by takezoux2 on 2016/10/05.
  */
case class LoginTime(userId: Long, loginTime: DateTime) {

}

object LoginTime extends SQLSyntaxSupport[LoginTime]{


  override def columns: Seq[String] = Seq("user_id","login_time")

  def apply(rs: WrappedResultSet) : LoginTime = {
    LoginTime(rs.long("user_id"),rs.jodaDateTime("login_time"))
  }
  def apply(g: ResultName[LoginTime])(rs: WrappedResultSet) : LoginTime = {
    LoginTime(rs.long(g.userId), rs.jodaDateTime(g.loginTime))
  }

  def insert(lt : LoginTime)(implicit s: DBSession) = {
    withSQL {
      QueryDSL.insert.into(LoginTime).namedValues(
        LoginTime.column.userId -> lt.userId,
        LoginTime.column.loginTime -> lt.loginTime
      )
    } update() apply()
  }
  def update(lt: LoginTime)(implicit s: DBSession) = {
    withSQL {
      QueryDSL.update(LoginTime).set(
        LoginTime.column.loginTime -> lt.loginTime
      ).where.eq(LoginTime.column.userId,lt.userId)
    } update() apply()

  }

  def selectAll()(implicit s: DBSession) : List[LoginTime] = {
    val t = LoginTime.syntax
    withSQL{
      QueryDSL.select.from(LoginTime as t)
    }.map(rs => LoginTime.apply(rs)).list().apply()
  }

}