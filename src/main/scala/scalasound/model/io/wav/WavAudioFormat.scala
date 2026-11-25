package scalasound.model.io.wav

import scalasound.model.io.AudioFileType

/** The WAV format, as an [[AudioFileType]], that can be registered in an [[AudioFormatRegistry]].
  */
object WavAudioFormat:

  val fileType: AudioFileType = AudioFileType(
    name = "WAV",
    primaryExtension = "wav",
    reader = Some(WavAudioReader),
    writer = Some(WavAudioWriter)
  )
