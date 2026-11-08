package scalasound.model.state

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.MonoTrack
import scalasound.model.io.{AudioLoadError, AudioSaveError}
import scalasound.model.state.Command.*
import scalasound.model.state.{AppState, ExportState, ImportState}
import scalasound.model.state.ImportState.Imported
import scalasound.model.state.PlaybackState.*

class AppStateSpec extends AnyFlatSpec with Matchers:
  private val track = MonoTrack(
    sampleRate = 44100,
    chunks = Vector(
      MonoChunk(Vector.fill(44100)(0f)),  // 0.0s to 1.0s chunk (Index 0)
      MonoChunk(Vector.fill(44100)(0f)),  // 1.0s to 2.0s chunk (Index 1)
      MonoChunk(Vector.fill(44100)(0f))   // 2.0s to 3.0s chunk (Index 2)
    )
  )

  "initial state" should "have playback stopped, no track loaded, have chunkIndex at 0 and import/export in idle" in {
    val state = AppState.initial
    state.playbackState shouldBe Stopped
    state.importState shouldBe ImportState.Idle
    state.track shouldBe None
    state.chunkIndex shouldBe 0
    state.exportState shouldBe ExportState.Idle
  }

  "loading a track" should "stop playback, set the track and set chunkIndex to 0" in {
    val state = AppState.initial
      .handle(UpdateImport(Imported(track)))
    state.playbackState shouldBe Stopped
    state.track shouldBe Some(track)
    state.chunkIndex shouldBe 0
  }

  "play" should "transition to playback to playing only if a track is loaded" in {
    val noTrackState = AppState.initial.handle(Play)
    noTrackState.playbackState shouldBe Stopped
    val loadedState = AppState.initial
      .handle(UpdateImport(Imported(track)))
      .handle(Play)
    loadedState.playbackState shouldBe Playing
  }

  "pause" should "transition to playback to pause only if currently playing" in {
    val base = AppState.initial.handle(UpdateImport(Imported(track)))
    val stoppedState = base.handle(Pause)
    stoppedState.playbackState shouldBe Stopped
    val playingState = base.handle(Play).handle(Pause)
    playingState.playbackState shouldBe Paused
  }

  "stop" should "reset playback to stopped and chunkIndex to 0 while keeping the track" in {
    val state = AppState.initial
      .handle(UpdateImport(Imported(track)))
      .handle(Play)
      .handle(NextChunk)
      .handle(NextChunk)
      .handle(Stop)
    state.playbackState shouldBe Stopped
    state.track shouldBe Some(track)
    state.chunkIndex shouldBe 0
  }

  "seek by chunk index" should "update chunkIndex if a track is loaded and clamp bounds" in {
    val stateWithTrack = AppState.initial.handle(UpdateImport(Imported(track)))
    stateWithTrack.handle(SeekToIndex(2)).chunkIndex shouldBe 2
    stateWithTrack.handle(SeekToIndex(10)).chunkIndex shouldBe 2
    stateWithTrack.handle(SeekToIndex(-5)).chunkIndex shouldBe 0
    val stateNoTrack = AppState.initial.handle(SeekToIndex(10))
    stateNoTrack.chunkIndex shouldBe 0
  }

  "nextChunk" should "increment chunkIndex by 1 when playing" in {
    val state = AppState.initial
      .handle(UpdateImport(Imported(track)))
      .handle(Play)
      .handle(NextChunk)
      .handle(NextChunk)
    state.chunkIndex shouldBe 2
  }

  "nextChunk" should "do nothing if playback is paused or stopped" in {
    val base = AppState.initial.handle(UpdateImport(Imported(track)))
    val stoppedState = base.handle(NextChunk)
    stoppedState.chunkIndex shouldBe 0
    val pausedState = base.handle(Play).handle(Pause).handle(NextChunk)
    pausedState.chunkIndex shouldBe 0
  }

  "currentPositionSeconds" should "calculate correct time based on active chunkIndex" in {
    val state = AppState.initial.handle(UpdateImport(Imported(track)))
    // Chunk 0 (0.0s)
    state.currentPositionSeconds shouldBe 0.0 +- 0.001
    // Chunk 1 (Starts at 1.0s)
    state.handle(SeekToIndex(1)).currentPositionSeconds shouldBe 1.0 +- 0.001
    // Chunk 2 (Starts at 2.0s)
    state.handle(SeekToIndex(2)).currentPositionSeconds shouldBe 2.0 +- 0.001
    // No track loaded should return 0.0
    AppState.initial.currentPositionSeconds shouldBe 0.0
  }

  "import and export" should "handle intermediate and failure states without losing track" in {
    val loaded = AppState.initial.handle(UpdateImport(Imported(track)))
    val importingState = loaded.handle(UpdateImport(ImportState.Importing))
    importingState.importState shouldBe ImportState.Importing
    importingState.track shouldBe Some(track)
    val loadError = AudioLoadError.CorruptedData("Corrupt file")
    val failedImport = loaded.handle(UpdateImport(ImportState.Failed(loadError)))
    failedImport.importState shouldBe ImportState.Failed(loadError)
    failedImport.track shouldBe Some(track)
    val exportingState = loaded.handle(UpdateExport(ExportState.Exporting))
    exportingState.exportState shouldBe ExportState.Exporting
    val exportedState = exportingState.handle(UpdateExport(ExportState.Exported))
    exportedState.exportState shouldBe ExportState.Exported
  }

  it should "return to Idle once a failure or a completed export is acknowledged" in {
    val failedImport = AppState.initial.handle(UpdateImport(ImportState.Failed(AudioLoadError.FileNotFound("a.wav"))))
    failedImport.handle(UpdateImport(ImportState.Idle)).importState shouldBe ImportState.Idle
    val failedExport = AppState.initial.handle(UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded)))
    failedExport.exportState shouldBe ExportState.Failed(AudioSaveError.NoTrackLoaded)
    failedExport.handle(UpdateExport(ExportState.Idle)).exportState shouldBe ExportState.Idle
    val exported = AppState.initial.handle(UpdateExport(ExportState.Exported))
    exported.handle(UpdateExport(ExportState.Idle)).exportState shouldBe ExportState.Idle
  }
