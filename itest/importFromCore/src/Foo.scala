import cats.Monad // From cats-core

object Foo {
  val bar = Monad[Option].pure(1)
}
