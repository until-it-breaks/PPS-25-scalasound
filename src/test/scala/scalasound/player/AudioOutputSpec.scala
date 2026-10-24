package scalasound.player

import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.domain.AudioChunk

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class AudioOutputSpec extends CatsEffectSuite:
  test("ConsoleAudioOutput writes successfully without error") {
    val output = AudioOutput.console
    val chunk = AudioChunk.MonoChunk(Vector.fill(100)(0.0f))
    // Assert that writing a single chunk completes successfully
    assertIO(output.write(chunk, 44100), ())
  }

  test("BlockingConsoleAudioOutput correctly paces a chunk being played") {
    val output = AudioOutput.blocking
    val sampleRate = 44100
    // 4410 frames at 44100 Hz = exactly 0.1 seconds (100 ms)
    val chunk = AudioChunk.MonoChunk(Vector.fill(4410)(0.0f))
    val elapsedIO: IO[FiniteDuration] =
      for
        start <- IO.monotonic
        _     <- output.write(chunk, sampleRate)
        end   <- IO.monotonic
      yield end - start
    elapsedIO.map { elapsed =>
      // Verify it blocked for at least 90ms
      assert(elapsed >= 90.millis, s"Expected at least 90ms, but took ${elapsed.toMillis}ms")
    }
  }

  test("BlockingConsoleAudioOutput correctly paces multiple sequential chunks being played") {
    val output = AudioOutput.blocking
    val sampleRate = 44100
    // 4410 frames per chunk at 44100 Hz = 0.1 seconds per chunk (totaling 300ms for 3 chunks)
    val chunks = Vector.fill(3)(AudioChunk.MonoChunk(Vector.fill(4410)(0.0f)))
    val elapsedIO: IO[FiniteDuration] =
      for
        start <- IO.monotonic
        _ <- chunks.foldLeft(IO.unit) { (acc, chunk) =>
          acc *> output.write(chunk, sampleRate)
        }
        end <- IO.monotonic
      yield end - start

    elapsedIO.map { elapsed =>
      // Verify it blocked for at least 270ms
      assert(elapsed >= 270.millis, s"Expected at least 270ms for 3 chunks, but took ${elapsed.toMillis}ms")
    }
  }