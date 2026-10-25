package scalasound.processing

//enum AudioErrors(val error: String):
//  case AudioEffectsErrors(override val error: String) extends AudioErrors(error)
//  case AudioProcessorErrors(override val error: String) extends AudioErrors(error)

/**
 * Defines a way to handle different errors that could happen 
 * during the appliance of constraints or during the audio processing. 
 */
enum AudioError:
  /**
   * Error for the AudioEffect.
   * @param effect specify in which effect the error happened.
   * @param msg the error message.
   */
  case AudioEffectsError(effect: AudioEffect, msg: String)
  /**
   * Error for the AudioProcessor.
   * @param processor specify in which processor the error happened.
   * @param msg the error message.
   */
  case AudioProcessorError(processor: AudioProcessor, msg: String)

  /* Returns the name of the audio effect. */
  private def effectName(effect: AudioEffect): String =
    effect.getClass.getSimpleName

  /* Returns the name of the audio processor.*/
  private def processorName(processor: AudioProcessor): String =
    processor.getClass.getSimpleName.stripSuffix("$")

  /**
   * Returns the content of the error.
   */
  def getContent: String = this match
    case AudioEffectsError(effect, msg) => s"${effectName(effect)} -> $msg"
    case AudioProcessorError(processor, msg) => s"${processorName(processor)} -> $msg"

end AudioError