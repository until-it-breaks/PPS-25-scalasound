package scalasound

import cats.effect.unsafe.IORuntime
import cats.effect.{Deferred, IO, IOApp}
import scalasound.view.ScalaFxApp

object Main extends IOApp.Simple:

  override def run: IO[Unit] =
    given IORuntime = runtime

    Deferred[IO, Unit].flatMap { ready =>
      AppSetup.make(ready.get).use { dependencies =>
        IO.blocking {
          new ScalaFxApp(
            dependencies,
            onReady = () => ready.complete(()).void.unsafeRunAndForget()
          ).main(Array.empty[String])
        }
      }
    }