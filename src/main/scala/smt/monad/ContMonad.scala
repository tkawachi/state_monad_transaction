package smt.monad

/**
  * Created by takezoux2 on 2016/10/05.
  */
case class ContMonad[R,A](run:(A => R) => R) {

  def map[B](f: A => B) = {
    ContMonad[R,B](cont => {
      run(a => {
        cont(f(a))
      })
    })
  }

  def flatMap[B](f: A => ContMonad[R,B]) = {
    ContMonad[R,B](cont => {
      run(a => {
        f(a).run(b => cont(b))
      })
    })
  }

}
