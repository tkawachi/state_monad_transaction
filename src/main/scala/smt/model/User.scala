package smt.model

import scalikejdbc._, SQLInterpolation._

/**
  * Created by takezoux2 on 2016/10/05.
  */
case class User(id: Long, username: String) {

}


object User extends SQLSyntaxSupport[User]{

  /**
    * これを指定すると、Shardingがうまくいく
    * @return
    */
  override def columns: Seq[String] = Seq("id","username")

  def apply(rs: WrappedResultSet) : User = {
    User(rs.long("id"),rs.string("username"))
  }
  def apply(g: ResultName[User])(rs: WrappedResultSet) : User = {
    User(rs.long(g.id),rs.string(g.username))
  }


  def insert(user : User)(implicit s: DBSession) = {
    withSQL {
      QueryDSL.insert.into(User).namedValues(
        User.column.id -> user.id,
        User.column.username -> user.username
      )
    } update() apply()
  }

  def selectAll()(implicit s: DBSession) : List[User] = {
    val t = User.syntax
    withSQL{
      QueryDSL.select.from(User as t)
    }.map(rs => User.apply(rs)).list().apply()
  }


}