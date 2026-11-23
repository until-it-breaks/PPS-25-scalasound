package scalasound.model.io

/** The channel layouts of the supported WAV subset, shared by [[WavAudioReader]] and
  * [[WavAudioWriter]].
  *
  * @param channels
  *   the number of channels of the layout
  */
private[io] enum ChannelLayout(val channels: Int):
  case Mono   extends ChannelLayout(1)
  case Stereo extends ChannelLayout(2)

  /** The size in bytes of one frame: one 16 bit sample for each channel. */
  def frameSize: Int = channels * Pcm16.BytesPerSample

private[io] object ChannelLayout:

  /** The layout with the given number of channels, if it is supported. */
  def fromChannelCount(channels: Int): Option[ChannelLayout] =
    values.find(_.channels == channels)
