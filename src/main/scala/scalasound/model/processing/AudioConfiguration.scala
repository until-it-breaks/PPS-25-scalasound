package scalasound.model.processing

/**
 * Defines a container for the range of possible values of a specific parameter.
 * @param min minimum value.
 * @param max maximum value.
 * @param default default value.
 */
final case class ParameterConfiguration(min: Float, max: Float, default: Float)

/**
 * Defines a contract for all the objects (singleton) effects.
 */
trait AudioConfiguration

object AudioConfiguration:
  /**
   * Returns the singleton related to the specific effect to extract information
   * about the specific parameters requested by the effect itself.
   * @param effectType the type of effect requested.
   * @return the related instance of the effect based on the type T in the effectType input.
   */
  def parameterConfiguration[T <: AudioConfiguration](effectType: AudioEffectType[T]): T =
    effectType.instance