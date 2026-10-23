package scalasound.player

import scalasound.domain.AudioChunk

/**
 * Destination for processed audio, typically the sound card.
 */
trait AudioOutput:

  /**
   * Outputs one chunk.
   *
   * May block until the audio has been consumed as a real sound card blocks when its buffer is full.
   *
   * @param chunk      mono or stereo samples in the range [-1.0, 1.0]
   * @param sampleRate sample rate of the chunk, in Hz
   */
  def write(chunk: AudioChunk, sampleRate: Int): Unit

object AudioOutput:
  def console(): AudioOutput = (chunk: AudioChunk, sampleRate: Int) =>
    val (kind, frames) = chunk match
      case AudioChunk.MonoChunk(samples) => ("mono", samples.length)
      case AudioChunk.StereoChunk(left, _) => ("stereo", left.length)
    println(s"output <- $kind chunk, $frames frames")