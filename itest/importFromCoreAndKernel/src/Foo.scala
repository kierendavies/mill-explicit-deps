import cats.Monad // From cats-core
import cats.Monoid // From cats-kernel

object Foo {
  val bar = Monad[Option].pure(1)
  val baz = Monoid[Int].empty
}
