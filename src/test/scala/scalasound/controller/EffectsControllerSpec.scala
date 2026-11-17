package scalasound.controller

import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.MonoTrack
import scalasound.model.processing.AudioEffect
import scalasound.model.state.AppStateStore
import scalasound.model.state.Command.UpdateImport
import scalasound.model.state.ImportState.Imported

/**
 * Contains tests for the EffectsController, checking
 * the way it interacts with the AppStateStore.
 */
final class EffectsControllerSpec extends CatsEffectSuite:

  private val SampleRate = 1000
  private val ChunkSize = 10
  private val TrackSize = 5
  private val DefaultValue = 0.5f

  private def setup: IO[(AppStateStore, EffectsController)] =
    for
      store             <- AppStateStore.make
      chunk: MonoChunk  = MonoChunk(Vector.fill(ChunkSize)(DefaultValue))
      track             = MonoTrack(SampleRate, Vector.fill(TrackSize)(chunk))
      _                 <- store.dispatch(UpdateImport(Imported(track)))
    yield (store, EffectsController(store))

  test("initial state should be empty with no effect associated to the track"):
    for
      (store, _) <- setup
      current <- store.current
    yield assert(current.effects.isEmpty)

  test("removeAll should clean all the effects contained in " +
    "the effects list even if the list is empty"):
    val gain = AudioEffect.gain(1.0f)
    for
      (store, controller) <- setup
      current             <- store.current
      _                   <- controller.removeAll()
      emptyStore          <- store.current
      _                   <- controller.addEffect(gain)
      gainStore           <- store.current
      _                   <- controller.removeAll()
      newEmptyStore       <- store.current
    yield
      assert(current.effects.isEmpty)
      assert(emptyStore.effects.isEmpty)
      assert(gainStore.effects.nonEmpty)
      assertEquals(gainStore.effects.head.effectType, gain.effectType)
      assert(newEmptyStore.effects.isEmpty)

  test("addEffect should add an effect to the list and " +
    "remove a same type effect if already in the list"):
    val gain = AudioEffect.gain(1.0f)
    val volume = AudioEffect.volume(1.0f)
    for
      (store, controller) <- setup
      _                   <- controller.addEffect(volume)
      _                   <- controller.addEffect(gain)
      twoEffectsStore     <- store.current
      _                   <- controller.addEffect(volume)
      newStore            <- store.current
    yield
      assert(twoEffectsStore.effects.nonEmpty)
      assertEquals(twoEffectsStore.effects.length, 2)
      assertEquals(twoEffectsStore.effects.head.effectType, gain.effectType)
      assertEquals(newStore.effects.length, 2)
      assertEquals(newStore.effects.head.effectType, volume.effectType)