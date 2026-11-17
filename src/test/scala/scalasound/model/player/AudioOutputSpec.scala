package scalasound.model.player

import cats.syntax.foldable.*
import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk
import scalasound.model.player.AudioOutput

import scala.concurrent.duration.*

class AudioOutputSpec extends CatsEffectSuite:

  private val sampleRate: Int = 44100

  private def mono(frames: Int): AudioChunk =
    AudioChunk.MonoChunk(Vector.fill(frames)(0.0f))

  private def stereo(frames: Int): AudioChunk =
    AudioChunk.StereoChunk(Vector.fill(frames)(0.0f), Vector.fill(frames)(0.0f))

  private def elapsed(io: IO[Unit]): IO[FiniteDuration] =
    for
      start <- IO.monotonic
      _ <- io
      end <- IO.monotonic
    yield end - start

  private def assertWaitedAtLeast(actual: FiniteDuration, expected: FiniteDuration): Unit =
    val slack = 5.millis
    assert(
      actual >= expected - slack,
      s"Expected at least ${expected.toMillis}ms, but took ${actual.toMillis}ms"
    )

  test("console writes a mono chunk without error") {
    assertIO(AudioOutput.console.write(mono(100), sampleRate), ())
  }

  test("console writes a stereo chunk without error") {
    assertIO(AudioOutput.console.write(stereo(100), sampleRate), ())
  }

  test("console does not wait") {
    // a 100 ms chunk is being played here
    elapsed(AudioOutput.console.write(mono(4410), sampleRate)).map: e =>
      assert(e < 50.millis, s"Expected no pacing, but took ${e.toMillis}ms")
  }

  List(8000 -> 120.millis, 16000 -> 60.millis, 48000 -> 20.millis, 96000 -> 10.millis)
    .foreach: (rate, expected) =>
      test(s"paced waits at least $expected for 960 frames at $rate Hz") {
        elapsed(AudioOutput.console.paced.write(mono(960), rate))
          .map(assertWaitedAtLeast(_, expected))
      }

  test("paced waits the total duration of sequential chunks") {
    // 3 chunks of 100 ms each
    val chunks = Vector.fill(3)(mono(4410))
    elapsed(chunks.traverse_(AudioOutput.console.paced.write(_, sampleRate)))
      .map(assertWaitedAtLeast(_, 300.millis))
  }

  test("paced duration depends on frames, so stereo waits as long as mono") {
    elapsed(AudioOutput.console.paced.write(stereo(4410), sampleRate))
      .map(assertWaitedAtLeast(_, 100.millis))
  }

  test("paced does not wait for an empty chunk") {
    elapsed(AudioOutput.console.paced.write(mono(0), sampleRate)).map: e =>
      assert(e < 50.millis, s"Expected no wait, but took ${e.toMillis}ms")
  }