package scalasound.model.domain

opaque type SampleRate = Int

object SampleRate:

  /** Creates a [[SampleRate]] if the given value is strictly positive.
    *
    * @param value
    *   the sampling rate in hertz
    * @return
    *   `Right(SampleRate)` if positive, or `Left(AudioDomainError.InvalidSampleRate)` otherwise
    */
  def from(value: Int): Either[AudioDomainError, SampleRate] =
    Either.cond(value > 0, value, AudioDomainError.InvalidSampleRate(value))

  extension (sampleRate: SampleRate) def value: Int = sampleRate
