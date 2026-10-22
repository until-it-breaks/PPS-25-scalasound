package scalasound.domain

import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

enum AudioChunk:
  case MonoChunk(samples: Vector[Float])
  case StereoChunk(left: Vector[Float], right: Vector[Float])

  /**
   * Checks the emptiness of the vectors contained in a chunk.
   */
  def isEmpty: Boolean = this match
    case MonoChunk(samples) => samples.isEmpty
    case StereoChunk(left, right) => left.isEmpty && right.isEmpty

  /**
   * Return the content of a chunk in a list.
   */
  def getVectors: List[Vector[Float]] = this match
    case MonoChunk(samples) => List(samples)
    case StereoChunk(left, right) => List(left, right)

trait AudioTrack:
  def sampleRate: Int
  def durationInSeconds: Double

case class MonoTrack(sampleRate: Int, chunks: Vector[MonoChunk]) extends AudioTrack {
  def durationInSeconds: Double = ???
}

case class StereoTrack(sampleRate: Int, chunks: Vector[StereoChunk]) extends AudioTrack {
  def durationInSeconds: Double = ???
}

@main def main(): Unit =
  val monoTrack = MonoTrack(44100, Vector(MonoChunk(Vector.fill(10)(0f))))
  println(monoTrack.chunks)

  val stereoTrack = StereoTrack(44100, Vector(StereoChunk(Vector.fill(10)(0f), Vector.fill(10)(0f))))
  println(stereoTrack.chunks)
  /*
  val stereoTrack = StereoTrack(44100, Vector(StereoChunk(monoTrack.chunks.head, monoTrack.chunks.head)))
  println(stereoTrack)
   */