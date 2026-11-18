package scalasound

import cats.effect.unsafe.IORuntime
import cats.effect.{Deferred, IO}
import scalasound.view.ScalaFxApp

object Main:

  def main(args: Array[String]): Unit =
    given IORuntime = IORuntime.global

    val ready = Deferred.unsafe[IO, Unit]

    val (dependencies, release) =
      AppSetup.make(ready.get).allocated.unsafeRunSync()

    try
      new ScalaFxApp(
        dependencies,
        onReady = () => ready.complete(()).void.unsafeRunAndForget()
      ).main(args)
    finally
      release.unsafeRunSync()
